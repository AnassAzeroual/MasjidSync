package com.mosquetracker.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mosquetracker.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    headerContainerColor: Color = MaterialTheme.colorScheme.surface,
    headerContentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        // The outer navigation scaffold owns the system insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = navigationIcon ?: {
                    Image(
                        painterResource(R.drawable.ic_brand_mark),
                        contentDescription = null,
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp).size(32.dp),
                    )
                },
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
