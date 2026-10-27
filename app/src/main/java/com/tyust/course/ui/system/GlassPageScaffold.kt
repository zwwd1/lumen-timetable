package com.tyust.course.ui.system

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

@Composable
internal fun rememberListHeaderCollapse(state: LazyListState): Float {
    val travel = with(LocalDensity.current) { 96.dp.toPx() }
    val collapse by remember(state, travel) {
        derivedStateOf {
            if (state.firstVisibleItemIndex > 0) 1f
            else (state.firstVisibleItemScrollOffset / travel).coerceIn(0f, 1f)
        }
    }
    return collapse
}

@Composable
internal fun rememberScrollHeaderCollapse(state: ScrollState): Float {
    val travel = with(LocalDensity.current) { 96.dp.toPx() }
    val collapse by remember(state, travel) { derivedStateOf { (state.value / travel).coerceIn(0f, 1f) } }
    return collapse
}

@Composable
internal fun rememberListHeaderCollapseProvider(state: LazyListState): () -> Float {
    val travel = with(LocalDensity.current) { 96.dp.toPx() }
    val collapse = remember(state, travel) { derivedStateOf {
        if (state.firstVisibleItemIndex > 0) 1f
        else (state.firstVisibleItemScrollOffset / travel).coerceIn(0f, 1f)
    } }
    return remember(collapse) { { collapse.value } }
}

/** Shared page shell for forms, history and standalone browser activities. */
@Composable
fun GlassPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    topBar: (@Composable () -> Unit)? = null,
    collapseFraction: Float? = null,
    collapseFractionProvider: (() -> Float)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val wallpaper = LocalAppBackdrop.current
    val scrollingHeader = collapseFraction != null || collapseFractionProvider != null
    val contentBackdrop = if (scrollingHeader && wallpaper != null && isBackdropSupported()) rememberLayerBackdrop() else null
    // Capture only the scrolling body. The header consumes its sibling layer, never itself.
    val headerBackdrop = if (wallpaper != null && contentBackdrop != null)
        rememberCombinedBackdrop(wallpaper, contentBackdrop) else wallpaper
    val density = LocalDensity.current
    val width = LocalConfiguration.current.screenWidthDp
    var expandedTop by remember(density.fontScale, width) { mutableStateOf(0.dp) }
    val page: @Composable () -> Unit = {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                bottomBar = bottomBar,
                topBar = {
                    if (topBar != null) topBar() else SystemTopBar(
                        title = title,
                        subtitle = subtitle,
                        collapseFraction = collapseFractionProvider?.invoke() ?: collapseFraction ?: 0f,
                        backdrop = headerBackdrop,
                        navigationIcon = {
                            if (onBack != null) SystemIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                        },
                        actions = actions
                    )
                }
            ) { padding ->
                val measuredTop = padding.calculateTopPadding()
                if (scrollingHeader) SideEffect {
                    if (measuredTop > expandedTop) expandedTop = measuredTop
                }
                val contentPadding = PaddingValues(
                    // Keep the expanded inset while the bar contracts; otherwise the list
                    // moves once from scrolling and again from the changing scaffold padding.
                    top = if (!scrollingHeader) measuredTop else maxOf(measuredTop, expandedTop),
                    bottom = maxOf(padding.calculateBottomPadding(), LocalAppOverlayBottomInset.current)
                )
                Box(Modifier.fillMaxSize().then(if (contentBackdrop != null) Modifier.layerBackdrop(contentBackdrop) else Modifier)) {
                    content(contentPadding)
                }
            }
    }
    if (LocalDialogHost.current == null) {
        GlassWindowHost(modifier) { page() }
    } else {
        Box(modifier.fillMaxSize()) { page() }
    }
}
