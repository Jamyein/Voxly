package com.voxly.presentation.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.voxly.presentation.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for the main screens.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesSuccessfully() {
        // Check that the app launches and shows the file browser
        composeTestRule.onNodeWithText("Files").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationIsDisplayed() {
        // Check that all bottom navigation items are visible
        composeTestRule.onNodeWithContentDescription("Files").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Recent").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Batch").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun canNavigateBetweenTabs() {
        // Navigate to Recent tab
        composeTestRule.onNodeWithContentDescription("Recent").performClick()
        composeTestRule.onNodeWithText("Recent Edits").assertIsDisplayed()

        // Navigate to Batch tab
        composeTestRule.onNodeWithContentDescription("Batch").performClick()
        composeTestRule.onNodeWithText("Batch Operations").assertIsDisplayed()

        // Navigate to Settings tab
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()

        // Navigate back to Files tab
        composeTestRule.onNodeWithContentDescription("Files").performClick()
    }
}
