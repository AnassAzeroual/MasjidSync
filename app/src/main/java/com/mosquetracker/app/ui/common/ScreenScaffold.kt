package com.mosquetracker.app.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Inner scaffold for a tab: the outer [com.mosquetracker.app.ui.MainScreen] already
 * applies window insets, so this one intentionally consumes none of them.
 *
 * The header defaults to the MasjidSync brand bar (deep green with white text); pass explicit
 * colors for screens that need a different treatment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    headerContainerColor: Color = MaterialTheme.colorScheme.primary,
    headerContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = navigationIcon ?: {},
                actions = actions,
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerContainerColor,
                    titleContentColor = headerContentColor,
                    navigationIconContentColor = headerContentColor,
                    actionIconContentColor = headerContentColor,
                ),
            )
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}
