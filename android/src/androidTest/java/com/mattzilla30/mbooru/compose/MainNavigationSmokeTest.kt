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

    @Test
    fun drawerSettingsAndActivityRecreationKeepWorking() {
        awaitDrawer()
        screenshot("sphere-home")
        compose
            .onNodeWithContentDescription(
                compose.activity.getString(R.string.navigation_drawer_open)
            )
            .performClick()
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
        compose
            .onNodeWithContentDescription(
                compose.activity.getString(R.string.navigation_drawer_open)
            )
            .assertIsDisplayed()
    }

    @Test
    fun primaryTabsKeepSearchAndCollectionAcrossRecreation() {
        awaitDrawer()
        compose.onNodeWithTag("home-tab-1").performClick()
        compose.onNodeWithTag("unified-query").performTextReplacement("landscape -cloud")
        compose.onNodeWithTag("home-tab-2").performClick()
        compose.onNodeWithText("New collection").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Native test collection")
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("Native test collection").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-tab-3").performClick()
        compose.onNodeWithText("Add, remove, import or export sites").assertExists()
        compose.onNodeWithTag("home-tab-4").performClick()
        compose.onNodeWithTag("settings_list").assertExists()
        Espresso.pressBack()
        compose.onNodeWithTag("home-tab-0").assertIsSelected()
        compose.onNodeWithTag("home-tab-1").performClick()
        compose.onNodeWithTag("unified-query").assert(hasText("landscape -cloud"))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("unified-query").assert(hasText("landscape -cloud"))
        compose.onNodeWithTag("home-tab-2").performClick()
        compose.onNodeWithText("Native test collection").assertExists()
    }

    @Test
    fun cachedBrowseRestoresFarPositionAcrossTabsAndActivityRecreation() {
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
            compose.onNodeWithTag("home-tab-2").performClick()
            compose.onNodeWithTag("home-tab-0").performClick()
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
        output.outputStream().use {
            compose
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun awaitDrawer() {
        val description = compose.activity.getString(R.string.navigation_drawer_open)
        compose.waitUntil(20000) {
            compose
                .onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }
}
