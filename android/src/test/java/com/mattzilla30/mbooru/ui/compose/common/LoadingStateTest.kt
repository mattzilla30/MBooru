package com.mattzilla30.mbooru.ui.compose.common

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.LoadState
import androidx.test.core.app.ApplicationProvider
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.ui.compose.browse.PagingStatus
import com.mattzilla30.mbooru.ui.compose.theme.MBooruTheme
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class LoadingStateTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a failed request offers retry without displaying its private URL`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        var attempts = 0
        compose.setContent {
            MBooruTheme {
                PagingStatus(
                    LoadState.Error(IOException("https://example.invalid/?api_key=private")),
                    { attempts++ },
                )
            }
        }
        compose.onNodeWithText(app.getString(R.string.action_retry)).performClick()
        compose.runOnIdle { assertEquals(1, attempts) }
        compose.onNodeWithText("api_key", substring = true).assertDoesNotExist()
    }

    @Test
    fun `an empty successful request has a distinct empty state`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        compose.setContent {
            MBooruTheme { PagingStatus(LoadState.NotLoading(true), {}, empty = true) }
        }
        compose.onNodeWithText(app.getString(R.string.browse_empty)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.action_retry)).assertDoesNotExist()
    }
}
