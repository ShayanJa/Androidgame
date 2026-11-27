package com.example.headsuppoker.ui.main

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import com.example.headsuppoker.ui.poker.PokerAppScreen

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
) {
    PokerAppScreen()
}
