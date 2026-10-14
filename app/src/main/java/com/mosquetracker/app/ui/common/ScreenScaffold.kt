package com.mosquetracker.app.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Extra bottom padding for the scrollable content of a screen that shows a floating action
 * button. [Scaffold] draws the FAB on top of the body rather than insetting it, so screens with
 * a FAB must reserve this space themselves — otherwise the FAB floats over their last cards.
 *
 * Sized to clear a 56dp FAB (extended and regular) plus a comfortable gap.
 */
val FabContentPadding = 88.dp

/**
 * Inner scaffold for a tab: the outer [com.mosquetracker.app.ui.MainScreen] already
 * applies window insets, so this one intentionally consumes none of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = actions,
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}
