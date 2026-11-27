package com.example.headsuppoker.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent { MainScreen() }
    }

    @Test
    fun lobbyScreen_displaysJoinAndCreateRoomControls() {
        composeTestRule.onNodeWithText("Join Room by Code").assertExists()
        composeTestRule.onNodeWithTag("room_code_input").assertExists()
        composeTestRule.onNodeWithTag("create_room_button").assertExists()
    }
}
