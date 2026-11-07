package com.praveen.studio.photovideo.url

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class MainActivityRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAppNavigationAndRendering() {
        // Verify Home Screen is rendered
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_content").assertExists()

        // Navigate to History
        composeTestRule.onNodeWithTag("nav_item_history").performClick()
        composeTestRule.waitForIdle()

        // Navigate to Settings
        composeTestRule.onNodeWithTag("nav_item_settings").performClick()
        composeTestRule.waitForIdle()

        // Navigate back to Home
        composeTestRule.onNodeWithTag("nav_item_home").performClick()
        composeTestRule.waitForIdle()
    }
}
