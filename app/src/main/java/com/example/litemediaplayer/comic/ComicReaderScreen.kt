package com.example.litemediaplayer.comic

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ComicReaderScreen(
    bookId: Long,
    onBack: () -> Unit,
    onOpenNextBook: ((Long) -> Unit)? = null,
    viewModel: ComicReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showControls by remember { mutableStateOf(true) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showNextBookDialog by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val touchZone = remember(uiState.settings.touchZone, uiState.settings.readingDirection) {
        uiState.settings.touchZone.resolvedForDirection(uiState.settings.readingDirection)
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(bookId) {
        showJumpDialog = false
        showNextBookDialog = false
        viewModel.openBook(bookId)
    }

    LaunchedEffect(uiState.currentBookId, uiState.currentPage, uiState.pages.size) {
        if (
            uiState.currentBookId == bookId &&
            uiState.pages.isNotEmpty() &&
            uiState.currentPage >= uiState.pages.lastIndex
        ) {
            showNextBookDialog = true
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun executeAction(action: TouchAction) {
        when (action) {
            TouchAction.NEXT_PAGE -> viewModel.nextPage()
            TouchAction.PREV_PAGE -> viewModel.previousPage()
            TouchAction.TOGGLE_CONTROLS -> showControls = !showControls
            TouchAction.FIRST_PAGE -> viewModel.goToFirstPage()
            TouchAction.LAST_PAGE -> viewModel.goToLastPage()
            TouchAction.JUMP_TO_PAGE -> {
                if (uiState.pages.isNotEmpty()) {
                    showJumpDialog = true
                }
            }

            TouchAction.SKIP_FORWARD -> viewModel.skipForward(touchZone.skipPageCount)
            TouchAction.SKIP_BACKWARD -> viewModel.skipBackward(touchZone.skipPageCount)
            TouchAction.TOGGLE_FULLSCREEN -> isFullscreen = !isFullscreen
            TouchAction.NONE -> Unit
        }
    }

    val pages = uiState.pages
    val currentPage = pages.getOrNull(uiState.currentPage)

    if (showJumpDialog) {
        JumpToPageDialog(
            currentPage = (uiState.currentPage + 1).coerceAtLeast(1),
            totalPages = uiState.pages.size,
            onJump = { pageNum ->
                viewModel.setCurrentPage(pageNum - 1)
                showJumpDialog = false
            },
            onDismiss = { showJumpDialog = false }
        )
    }

    val nextBook = remember(uiState.books, uiState.currentBookId, showNextBookDialog) {
        if (!showNextBookDialog) {
            null
        } else {
            val books = uiState.books
            val currentIndex = books.indexOfFirst { it.id == uiState.currentBookId }
            if (currentIndex >= 0 && currentIndex < books.lastIndex) {
                books[currentIndex + 1]
            } else {
                null
            }
        }
    }

    LaunchedEffect(showNextBookDialog, nextBook?.id) {
        if (showNextBookDialog && nextBook == null) {
            showNextBookDialog = false
        }
    }

    if (
        showNextBookDialog &&
        uiState.pages.isNotEmpty() &&
        uiState.currentPage >= uiState.pages.lastIndex &&
        nextBook != null
    ) {
        AlertDialog(
            onDismissRequest = { showNextBookDialog = false },
            title = { Text("最後のページです") },
            text = { Text("次の漫画「${nextBook.title}」を開きますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNextBookDialog = false
                        onOpenNextBook?.invoke(nextBook.id) ?: viewModel.openBook(nextBook.id)
                    }
                ) {
                    Text("次を開く")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNextBookDialog = false }) {
                    Text("閉じる")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                    return@onPreviewKeyEvent false
                }

                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        executeAction(touchZone.volumeUpAction)
                        true
                    }

                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        executeAction(touchZone.volumeDownAction)
                        true
                    }

                    else -> false
                }
            }
            .background(Color(uiState.settings.backgroundColorArgb))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showControls && !isFullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onBack) {
                        Text("戻る")
                    }
                    Text(
                        text = "${uiState.currentPage + 1} / ${uiState.pages.size}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { showJumpDialog = true },
                        enabled = uiState.pages.isNotEmpty()
                    ) {
                        Text("移動")
                    }
                }
            }

            when (uiState.settings.mode) {
                ReaderMode.VERTICAL -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(pages, key = { _, page -> page.index }) { _, page ->
                            PageRenderer(
                                model = page.model,
                                maxZoom = uiState.settings.zoomMax,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(uiState.settings.pagePaddingDp.dp)
                            )
                        }
                    }
                }

                ReaderMode.SPREAD -> {
                    if (pages.isEmpty()) {
                        EmptyReader()
                    } else {
                        SpreadModeContent(
                            pages = pages,
                            currentPage = uiState.currentPage,
                            isLandscape = isLandscape,
                            maxZoom = uiState.settings.zoomMax,
                            pagePaddingDp = uiState.settings.pagePaddingDp
                        )
                    }
                }

                ReaderMode.PAGE -> {
                    if (currentPage == null) {
                        EmptyReader()
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            PageRenderer(
                                model = currentPage.model,
                                maxZoom = uiState.settings.zoomMax,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(uiState.settings.pagePaddingDp.dp)
                            )

                            ComicTouchZoneOverlay(
                                layout = touchZone.layout,
                                longPressMs = touchZone.longPressMs.toLong(),
                                onZoneTap = { zone ->
                                    executeAction(resolveTapAction(touchZone, zone))
                                },
                                onZoneLongPress = { zone ->
                                    executeAction(resolveLongPressAction(touchZone, zone))
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        if (uiState.settings.blueLightFilterEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x33FFB74D))
            )
        }
    }
}

enum class ZoneId {
    LEFT,
    CENTER,
    RIGHT,
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT
}

private fun resolveTapAction(config: TouchZoneConfig, zone: ZoneId): TouchAction {
    return when (zone) {
        ZoneId.LEFT -> config.leftTap
        ZoneId.CENTER -> config.centerTap
        ZoneId.RIGHT -> config.rightTap
        ZoneId.TOP_LEFT -> config.topLeftTap
        ZoneId.TOP_CENTER -> config.topCenterTap
        ZoneId.TOP_RIGHT -> config.topRightTap
        ZoneId.BOTTOM_LEFT -> config.bottomLeftTap
        ZoneId.BOTTOM_CENTER -> config.bottomCenterTap
        ZoneId.BOTTOM_RIGHT -> config.bottomRightTap
    }
}

private fun resolveLongPressAction(config: TouchZoneConfig, zone: ZoneId): TouchAction {
    return when (zone) {
        ZoneId.LEFT -> config.leftLongPress
        ZoneId.CENTER -> config.centerLongPress
        ZoneId.RIGHT -> config.rightLongPress
        ZoneId.TOP_LEFT -> config.topLeftLongPress
        ZoneId.TOP_CENTER -> config.topCenterLongPress
        ZoneId.TOP_RIGHT -> config.topRightLongPress
        ZoneId.BOTTOM_LEFT -> config.bottomLeftLongPress
        ZoneId.BOTTOM_CENTER -> config.bottomCenterLongPress
        ZoneId.BOTTOM_RIGHT -> config.bottomRightLongPress
    }
}

@Composable
private fun ComicTouchZoneOverlay(
    layout: TouchZoneLayout,
    longPressMs: Long,
    onZoneTap: (ZoneId) -> Unit,
    onZoneLongPress: (ZoneId) -> Unit,
    modifier: Modifier = Modifier
) {
    when (layout) {
        TouchZoneLayout.THREE_COLUMN -> {
            Row(modifier = modifier) {
                listOf(ZoneId.LEFT, ZoneId.CENTER, ZoneId.RIGHT).forEach { zone ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(zone, longPressMs) {
                                detectTapGestures(
                                    onTap = { onZoneTap(zone) },
                                    onLongPress = { onZoneLongPress(zone) }
                                )
                            }
                    )
                }
            }
        }

        TouchZoneLayout.SIX_ZONE -> {
            Column(modifier = modifier) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    listOf(ZoneId.TOP_LEFT, ZoneId.TOP_CENTER, ZoneId.TOP_RIGHT).forEach { zone ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(zone, longPressMs) {
                                    detectTapGestures(
                                        onTap = { onZoneTap(zone) },
                                        onLongPress = { onZoneLongPress(zone) }
                                    )
                                }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxWidth()
                ) {
                    listOf(
                        ZoneId.BOTTOM_LEFT,
                        ZoneId.BOTTOM_CENTER,
                        ZoneId.BOTTOM_RIGHT
                    ).forEach { zone ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(zone, longPressMs) {
                                    detectTapGestures(
                                        onTap = { onZoneTap(zone) },
                                        onLongPress = { onZoneLongPress(zone) }
                                    )
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JumpToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentPage.toString()) }
    val parsed = text.toIntOrNull()
    val isValid = totalPages > 0 && parsed != null && parsed in 1..totalPages

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ページ移動") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("現在: $currentPage / $totalPages ページ")
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { it.isDigit() }
                    },
                    label = { Text("ページ番号 (1〜$totalPages)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = text.isNotEmpty() && !isValid,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    parsed?.let(onJump)
                },
                enabled = isValid
            ) {
                Text("移動")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
private fun SpreadModeContent(
    pages: List<ComicPage>,
    currentPage: Int,
    isLandscape: Boolean,
    maxZoom: Float,
    pagePaddingDp: Int
) {
    val first = pages.getOrNull(currentPage)
    val second = pages.getOrNull(currentPage + 1)
    val firstPage = first ?: run {
        EmptyReader()
        return
    }

    if (!isLandscape || second == null) {
        PageRenderer(
            model = firstPage.model,
            maxZoom = maxZoom,
            modifier = Modifier
                .fillMaxSize()
                .padding(pagePaddingDp.dp)
        )
        return
    }

    Row(modifier = Modifier.fillMaxSize()) {
        PageRenderer(
            model = firstPage.model,
            maxZoom = maxZoom,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(pagePaddingDp.dp)
        )
        PageRenderer(
            model = second.model,
            maxZoom = maxZoom,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(pagePaddingDp.dp)
        )
    }
}

@Composable
private fun EmptyReader() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "表示できるページがありません")
    }
}
