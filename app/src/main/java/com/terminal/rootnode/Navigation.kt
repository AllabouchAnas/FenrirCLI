package com.terminal.rootnode

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

@Serializable
sealed interface RootNodeRoute : NavKey {
    @Serializable
    data object Terminal : RootNodeRoute
}

@Composable
fun RootNodeNavHost() {
    val backStack = rememberNavBackStack(RootNodeRoute.Terminal)

    NavDisplay<NavKey>(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = { route ->
            when (route) {
                is RootNodeRoute.Terminal -> NavEntry(route) { TerminalScreen() }
                else -> error("Unknown route: $route")
            }
        }
    )
}