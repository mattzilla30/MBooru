package com.mattzilla30.mbooru.compose

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.ui.activity.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationSmokeTest {
    val compose = createAndroidComposeRule<MainActivity>()
    private val notificationPermission =
        org.junit.rules.TestRule { statement, description ->
            if (android.os.Build.VERSION.SDK_INT >= 33)
                androidx.test.rule.GrantPermissionRule.grant(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )
                    .apply(statement, description)
            else statement
        }
    @get:Rule
    val rules: org.junit.rules.TestRule =
        org.junit.rules.RuleChain.outerRule(notificationPermission).around(compose)

    private fun destination(title: Int) {
        compose.runOnIdle { compose.activity.openDrawer() }
        compose
            .onNode(
                hasText(compose.activity.getString(title)) and
                    hasAnyAncestor(hasTestTag("sphere-drawer-content"))
            )
            .performScrollTo()
            .performClick()
        compose.waitForIdle()
    }

    @Test
    fun drawerSettingsAndActivityRecreationKeepWorking() {
        awaitDrawer()
        screenshot("sphere-home")
        compose.onRoot().performTouchInput { swipeRight(startX = 1f, endX = width * .8f) }
        screenshot("sphere-drawer")
        val settings =
            compose.onNode(
                hasText(compose.activity.getString(R.string.title_settings)) and
                    hasAnyAncestor(hasTestTag("sphere-drawer-content"))
            )
        try {
            settings.assertIsDisplayed()
        } catch (_: AssertionError) {
            settings.performScrollTo()
        }
        settings.performClick()
        screenshot("sphere-settings")
        compose
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag(Settings.GRID_ROUNDED_KEY))
        compose.onNodeWithTag(Settings.GRID_ROUNDED_KEY).assertExists().performClick()
        Espresso.pressBack()
        compose.activityRule.scenario.recreate()
        awaitDrawer()
        compose.onNodeWithTag("unified-query").assertIsDisplayed()
    }

    @Test
    fun favoritesKeepSearchAndCollectionAcrossRecreation() {
        awaitDrawer()
        compose.onNodeWithTag("unified-query").performTextReplacement("landscape -cloud")
        destination(R.string.sphere_favorites)
        compose.onNodeWithText("All favorites").assertIsDisplayed()
        compose.onNodeWithText("New collection").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Native test collection")
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Native test collection").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-tab-3").assertDoesNotExist()
        compose.onNodeWithTag("home-tab-4").assertDoesNotExist()
        destination(R.string.title_settings)
        compose.onNodeWithTag("settings_list").assertExists()
        val settingsList = compose.onNodeWithTag("settings_list")
        settingsList.performScrollToNode(hasTestTag("settings-download-limit"))
        compose.onNodeWithTag("settings-download-limit").assertIsDisplayed()
        compose.onNodeWithTag("settings-category-${R.string.title_downloads}").assertIsDisplayed()
        settingsList.performScrollToNode(
            hasTestTag("settings-category-${R.string.sphere_interface}")
        )
        compose.onNodeWithTag("settings-side-panel").assertDoesNotExist()
        compose.onNodeWithTag("settings-category-${R.string.sphere_interface}").assertIsDisplayed()
        settingsList.performScrollToNode(hasTestTag("settings-app-lock"))
        compose.onNodeWithTag("settings-app-lock").assertIsDisplayed()
        compose.onNodeWithTag("settings-category-${R.string.settings_security}").assertIsDisplayed()
        compose.onNodeWithTag("settings-download-limit").assertIsNotDisplayed()
        settingsList.performScrollToIndex(0)
        compose.onNodeWithTag("settings-app-lock").assertIsNotDisplayed()
        settingsList.performScrollToNode(hasTestTag("settings-sites"))
        compose.onNodeWithTag("settings-category-${R.string.sphere_servers}").assertIsDisplayed()
        compose.onNodeWithTag("settings-sites").performClick()
        compose.onNodeWithText("Add, remove, import or export sites").assertExists()
        Espresso.pressBack()
        compose.onNodeWithTag("settings_list").assertExists()
        Espresso.pressBack()
        compose.onNodeWithTag("unified-query").assertExists()
        compose.onNodeWithTag("unified-query").assert(hasText("landscape -cloud"))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("unified-query").assert(hasText("landscape -cloud"))
        destination(R.string.sphere_favorites)
        compose.onNodeWithText("All favorites").assertIsDisplayed()
        compose.onNodeWithText("Native test collection").assertExists()
    }

    @Test
    fun cachedBrowseRestoresFarPositionAcrossFavoritesAndActivityRecreation() {
        awaitDrawer()
        compose.waitUntil(10000) { compose.activity.settings.activatedBooruUid > 0 }
        val database =
            org.koin.core.context.GlobalContext.get()
                .get<com.mattzilla30.mbooru.data.database.MyDatabase>()
        val originalSite = compose.activity.settings.activatedBooruUid
        val originalAutoRefresh = compose.activity.settings.autoRefresh
        val site =
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                database
                    .booruDao()
                    .insert(
                        com.mattzilla30.mbooru.data.model.common.Booru(
                            name = "Native cached grid fixture",
                            scheme = "https",
                            host = "native-cache.invalid",
                            type = com.mattzilla30.mbooru.app.Values.BOORU_TYPE_MOE,
                        )
                    )
            }
        try {
            val file = java.io.File(compose.activity.cacheDir, "native-library-fixture.png")
            val bitmap =
                android.graphics.Bitmap.createBitmap(
                    16,
                    16,
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
            bitmap.eraseColor(android.graphics.Color.rgb(245, 235, 212))
            file.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            val image = android.net.Uri.fromFile(file).toString()
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                val database =
                    org.koin.core.context.GlobalContext.get()
                        .get<com.mattzilla30.mbooru.data.database.MyDatabase>()
                database
                    .postDao()
                    .insert(
                        (0 until 80).map { offset ->
                            com.mattzilla30.mbooru.data.model.common.Post(
                                booruUid = site,
                                id = 200000 + offset,
                                index = offset,
                                query = "",
                                width = 100,
                                height = 100,
                                size = 1,
                                score = 0,
                                rating = "g",
                                time = null,
                                tags = emptyList(),
                                preview = image,
                                sample = image,
                                medium = image,
                                origin = image,
                                source = null,
                                uploader =
                                    com.mattzilla30.mbooru.data.model.common.User(
                                        name = "Native fixture"
                                    ),
                            )
                        }
                    )
            }
            compose.runOnIdle {
                compose.activity.settings.autoRefresh = false
                compose.activity.settings.activatedBooruUid = site
            }
            val first = compose.activity.getString(R.string.post_accessibility, 200000)
            compose.waitUntil(10000) {
                compose.onAllNodesWithContentDescription(first).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("post_grid").performScrollToIndex(50)
            val target = compose.activity.getString(R.string.post_accessibility, 200050)
            compose.waitUntil(10000) {
                compose.onAllNodesWithContentDescription(target).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(target).assertIsDisplayed()
            destination(R.string.sphere_favorites)
            compose.onNodeWithText("All favorites").assertIsDisplayed()
            Espresso.pressBack()
            compose.waitUntil(10000) {
                compose.onAllNodesWithContentDescription(target).fetchSemanticsNodes().isNotEmpty()
            }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000) {
                compose.onAllNodesWithContentDescription(target).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(target).assertIsDisplayed()
        } finally {
            compose.runOnIdle {
                compose.activity.settings.autoRefresh = originalAutoRefresh
                compose.activity.settings.activatedBooruUid = originalSite
            }
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                database.booruDao().delete(site)
            }
        }
    }

    private fun screenshot(name: String) {
        val instrumentation =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        val directory =
            androidx.test.platform.app.InstrumentationRegistry.getArguments()
                .getString("additionalTestOutputDir") ?: return
        val output =
            java.io.File(directory, "screenshots/$name.png").apply { parentFile!!.mkdirs() }
        val bitmap =
            if (android.os.Build.VERSION.SDK_INT >= 26)
                compose.onRoot().captureToImage().asAndroidBitmap()
            else requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        output.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun awaitDrawer() {
        compose.waitUntil(20000) {
            compose
                .onAllNodesWithTag("unified-query")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }
}
