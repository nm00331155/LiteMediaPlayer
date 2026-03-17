package com.example.litemediaplayer.comic

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs

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
    val displayTotalPages = maxOf(uiState.totalPageCount, uiState.pages.size)
    var showControls by remember { mutableStateOf(true) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showNextBookDialog by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isZoneEditMode by remember { mutableStateOf(false) }
    var editingZone by remember { mutableStateOf<ZoneEditTarget?>(null) }
    var isCurrentPageZoomed by remember { mutableStateOf(false) }
    var pendingDoubleTap by remember(uiState.currentBookId, uiState.currentPage) {
        mutableStateOf<Offset?>(null)
    }
    var pageTurnDragOffset by remember(uiState.currentBookId, uiState.currentPage) {
        mutableFloatStateOf(0f)
    }
    var isPageTurnDragging by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val touchZone = remember(uiState.settings.touchZone, uiState.settings.readingDirection) {
        uiState.settings.touchZone.resolvedForDirection(uiState.settings.readingDirection)
    }
    val pages = uiState.pages

    val pageTurnOffset by animateFloatAsState(
        targetValue = if (
            isPageTurnDragging && uiState.settings.animation != PageAnimation.NONE
        ) {
            pageTurnDragOffset
        } else {
            0f
        },
        animationSpec = if (
            isPageTurnDragging || uiState.settings.animation == PageAnimation.NONE
        ) {
            snap()
        } else {
            tween(durationMillis = uiState.settings.animationSpeedMs.coerceIn(80, 320))
        },
        label = "comicPageTurnOffset"
    )

    fun resolvePageTurnAction(offsetX: Float): PageTurnAction? {
        if (abs(offsetX) < 0.5f) {
            return null
        }
        val nextPage = when (uiState.settings.readingDirection) {
            ReadingDirection.RTL -> offsetX < 0f
            ReadingDirection.LTR -> offsetX > 0f
        }
        return if (nextPage) PageTurnAction.NEXT else PageTurnAction.PREVIOUS
    }

    fun canTurnPage(action: PageTurnAction): Boolean {
        return when (action) {
            PageTurnAction.NEXT -> uiState.currentPage < pages.lastIndex
            PageTurnAction.PREVIOUS -> uiState.currentPage > 0
        }
    }

    BackHandler {
        if (isZoneEditMode) {
            isZoneEditMode = false
        } else {
            onBack()
        }
    }

    LaunchedEffect(bookId) {
        showJumpDialog = false
        showNextBookDialog = false
        viewModel.openBook(bookId)
    }

    LaunchedEffect(uiState.currentBookId, uiState.currentPage) {
        pageTurnDragOffset = 0f
        isPageTurnDragging = false
    }

    LaunchedEffect(
        uiState.currentBookId,
        uiState.currentPage,
        uiState.pages.size,
        uiState.isBookFullyLoaded
    ) {
        if (
            uiState.currentBookId == bookId &&
            uiState.isBookFullyLoaded &&
            uiState.pages.isNotEmpty() &&
            uiState.currentPage >= uiState.pages.lastIndex
        ) {
            showNextBookDialog = true
        } else if (!uiState.isBookFullyLoaded || uiState.currentPage < uiState.pages.lastIndex) {
            showNextBookDialog = false
        }
    }

    LaunchedEffect(
        uiState.currentBookId,
        uiState.currentPage,
        showControls,
        isZoneEditMode,
        showJumpDialog
    ) {
        if (!showJumpDialog) {
            withFrameNanos { }
            runCatching {
                focusRequester.requestFocus()
            }
        }
    }

    if (uiState.isLoadingBook) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = Color.White
                )
                Text(
                    text = "読み込み中...",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
        return
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

    editingZone?.let { target ->
        ZoneActionPickerDialog(
            target = target,
            config = touchZone,
            onUpdate = { newConfig ->
                viewModel.updateTouchZoneConfig(newConfig)
                editingZone = null
            },
            onDismiss = { editingZone = null }
        )
    }

    val nextBook = remember(uiState.books, uiState.currentBookId, showNextBookDialog) {
        if (!showNextBookDialog) {
            null
        } else {
            val currentBook = uiState.books.firstOrNull { it.id == uiState.currentBookId }
                ?: return@remember null
            val booksInSequence = uiState.books
                .filter { it.folderId == currentBook.folderId }
                .ifEmpty { uiState.books }
                .sortedWith(comicBookNaturalComparator)

            val currentIndex = booksInSequence.indexOfFirst { it.id == currentBook.id }
            booksInSequence.getOrNull(currentIndex + 1)
        }
    }

    LaunchedEffect(showNextBookDialog, nextBook?.id) {
        if (showNextBookDialog && nextBook == null) {
            showNextBookDialog = false
        }
    }

    if (
        showNextBookDialog &&
        uiState.isBookFullyLoaded &&
        uiState.pages.isNotEmpty() &&
        uiState.currentPage >= uiState.pages.lastIndex &&
        nextBook != null
    ) {
        AlertDialog(
            onDismissRequest = { showNextBookDialog = false },
            title = { Text("次の漫画") },
            text = { Text(nextBook.title) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNextBookDialog = false
                        onOpenNextBook?.invoke(nextBook.id) ?: viewModel.openBook(nextBook.id)
                    }
                ) {
                    Text("開く")
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
                val keyCode = event.nativeKeyEvent.keyCode
                if (
                    keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                    keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
                ) {
                    return@onPreviewKeyEvent false
                }

                if (isZoneEditMode) {
                    return@onPreviewKeyEvent true
                }

                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_VOLUME_UP -> executeAction(touchZone.volumeUpAction)
                        KeyEvent.KEYCODE_VOLUME_DOWN -> executeAction(touchZone.volumeDownAction)
                    }
                }
                true
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
                    Button(
                        onClick = {
                            if (isZoneEditMode) {
                                isZoneEditMode = false
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Text("戻る")
                    }
                    Text(
                        text = "${uiState.currentPage + 1} / $displayTotalPages",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    if (!uiState.isBookFullyLoaded) {
                        Text(
                            text = "残りを読み込み中",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { isZoneEditMode = !isZoneEditMode }
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "ゾーン設定",
                            tint = if (isZoneEditMode) Color.Yellow else Color.White
                        )
                    }
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(
                                    uiState.currentBookId,
                                    uiState.currentPage,
                                    uiState.settings.readingDirection,
                                    isCurrentPageZoomed,
                                    uiState.settings.animation
                                ) {
                                    if (isCurrentPageZoomed) {
                                        return@pointerInput
                                    }

                                    var accumulatedDragX = 0f
                                    val swipeThreshold = size.width * 0.12f
                                    val maxDragOffset = size.width * 0.18f
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            accumulatedDragX = pageTurnOffset
                                            isPageTurnDragging = true
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            val rawCandidate = accumulatedDragX + dragAmount
                                            val action = resolvePageTurnAction(rawCandidate)
                                            accumulatedDragX = if (action != null && canTurnPage(action)) {
                                                rawCandidate.coerceIn(-maxDragOffset, maxDragOffset)
                                            } else {
                                                rawCandidate.coerceIn(-maxDragOffset, maxDragOffset) * 0.25f
                                            }
                                            pageTurnDragOffset = accumulatedDragX
                                            change.consume()
                                        },
                                        onDragEnd = {
                                            val dragX = pageTurnDragOffset
                                            val action = resolvePageTurnAction(dragX)
                                            val isSwipe = action != null &&
                                                canTurnPage(action) &&
                                                abs(dragX) >= swipeThreshold
                                            accumulatedDragX = 0f
                                            isPageTurnDragging = false
                                            if (isSwipe) {
                                                action?.let { resolvedAction ->
                                                    when (resolvedAction) {
                                                        PageTurnAction.NEXT -> viewModel.nextPage()
                                                        PageTurnAction.PREVIOUS -> viewModel.previousPage()
                                                    }
                                                }
                                            }
                                            pageTurnDragOffset = 0f
                                        },
                                        onDragCancel = {
                                            accumulatedDragX = 0f
                                            isPageTurnDragging = false
                                            pageTurnDragOffset = 0f
                                        }
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationX = pageTurnOffset
                                        alpha = if (uiState.settings.animation == PageAnimation.FADE) {
                                            (1f - (abs(pageTurnOffset) / 220f)).coerceIn(0.72f, 1f)
                                        } else {
                                            1f
                                        }
                                    }
                            ) {
                                PageRenderer(
                                    model = currentPage.model,
                                    maxZoom = uiState.settings.zoomMax,
                                    onZoomStateChange = { zoomed ->
                                        isCurrentPageZoomed = zoomed
                                    },
                                    doubleTapRequest = pendingDoubleTap,
                                    onDoubleTapRequestConsumed = {
                                        pendingDoubleTap = null
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(uiState.settings.pagePaddingDp.dp)
                                )
                            }

                            if (isZoneEditMode) {
                                ZoneEditOverlay(
                                    layout = touchZone.layout,
                                    config = touchZone,
                                    onZoneClick = { target -> editingZone = target },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (!isCurrentPageZoomed) {
                                ComicTouchZoneOverlay(
                                    layout = touchZone.layout,
                                    longPressMs = touchZone.longPressMs.toLong(),
                                    onZoneTap = { zone ->
                                        executeAction(resolveTapAction(touchZone, zone))
                                    },
                                    onZoneLongPress = { zone ->
                                        executeAction(resolveLongPressAction(touchZone, zone))
                                    },
                                    onDoubleTap = { tapOffset ->
                                        pendingDoubleTap = tapOffset
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isZoneEditMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "各ゾーンをタップして操作を設定  |  戻るボタンで終了",
                    color = Color.Yellow,
                    style = MaterialTheme.typography.bodyMedium
                )
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

data class ZoneEditTarget(
    val zoneId: ZoneId,
    val label: String,
    val currentTap: TouchAction,
    val currentLongPress: TouchAction
)

@Composable
private fun ZoneEditOverlay(
    layout: TouchZoneLayout,
    config: TouchZoneConfig,
    onZoneClick: (ZoneEditTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    when (layout) {
        TouchZoneLayout.THREE_COLUMN -> {
            Row(modifier = modifier) {
                ZoneEditCell(
                    label = "左",
                    tapAction = config.leftTap,
                    longPressAction = config.leftLongPress,
                    onClick = {
                        onZoneClick(
                            ZoneEditTarget(ZoneId.LEFT, "左", config.leftTap, config.leftLongPress)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                ZoneEditCell(
                    label = "中央",
                    tapAction = config.centerTap,
                    longPressAction = config.centerLongPress,
                    onClick = {
                        onZoneClick(
                            ZoneEditTarget(ZoneId.CENTER, "中央", config.centerTap, config.centerLongPress)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                ZoneEditCell(
                    label = "右",
                    tapAction = config.rightTap,
                    longPressAction = config.rightLongPress,
                    onClick = {
                        onZoneClick(
                            ZoneEditTarget(ZoneId.RIGHT, "右", config.rightTap, config.rightLongPress)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        TouchZoneLayout.SIX_ZONE -> {
            Column(modifier = modifier) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    ZoneEditCell(
                        label = "上左",
                        tapAction = config.topLeftTap,
                        longPressAction = config.topLeftLongPress,
                        onClick = {
                            onZoneClick(
                                ZoneEditTarget(
                                    ZoneId.TOP_LEFT,
                                    "上左",
                                    config.topLeftTap,
                                    config.topLeftLongPress
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    ZoneEditCell(
                        label = "上中央",
                        tapAction = config.topCenterTap,
                        longPressAction = config.topCenterLongPress,
                        onClick = {
                            onZoneClick(
                                ZoneEditTarget(
                                    ZoneId.TOP_CENTER,
                                    "上中央",
                                    config.topCenterTap,
                                    config.topCenterLongPress
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    ZoneEditCell(
                        label = "上右",
                        tapAction = config.topRightTap,
                        longPressAction = config.topRightLongPress,
                        onClick = {
                            onZoneClick(
                                ZoneEditTarget(
                                    ZoneId.TOP_RIGHT,
                                    "上右",
                                    config.topRightTap,
                                    config.topRightLongPress
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxWidth()
                ) {
                    ZoneEditCell(
                        label = "下左",
                        tapAction = config.bottomLeftTap,
                        longPressAction = config.bottomLeftLongPress,
                        onClick = {
                            onZoneClick(
                                ZoneEditTarget(
                                    ZoneId.BOTTOM_LEFT,
                                    "下左",
                                    config.bottomLeftTap,
                                    config.bottomLeftLongPress
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    ZoneEditCell(
                        label = "下中央",
                        tapAction = config.bottomCenterTap,
                        longPressAction = config.bottomCenterLongPress,
                        onClick = {
                            onZoneClick(
                                ZoneEditTarget(
                                    ZoneId.BOTTOM_CENTER,
                                    "下中央",
                                    config.bottomCenterTap,
                                    config.bottomCenterLongPress
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    ZoneEditCell(
                        label = "下右",
                        tapAction = config.bottomRightTap,
                        longPressAction = config.bottomRightLongPress,
                        onClick = {
                            onZoneClick(
                                ZoneEditTarget(
                                    ZoneId.BOTTOM_RIGHT,
                                    "下右",
                                    config.bottomRightTap,
                                    config.bottomRightLongPress
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoneEditCell(
    label: String,
    tapAction: TouchAction,
    longPressAction: TouchAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0x55000088))
            .clickable { onClick() }
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
                .background(Color(0x22FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp)
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "タップ: ${tapAction.label}",
                    color = Color(0xFFAADDFF),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "長押し: ${longPressAction.label}",
                    color = Color(0xFFFFDD88),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ZoneActionPickerDialog(
    target: ZoneEditTarget,
    config: TouchZoneConfig,
    onUpdate: (TouchZoneConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTap by remember(target) { mutableStateOf(target.currentTap) }
    var selectedLongPress by remember(target) { mutableStateOf(target.currentLongPress) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${target.label} ゾーン設定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("タップ操作", style = MaterialTheme.typography.labelLarge)
                ActionDropdownInDialog(
                    current = selectedTap,
                    onSelect = { selectedTap = it }
                )
                Text("長押し操作", style = MaterialTheme.typography.labelLarge)
                ActionDropdownInDialog(
                    current = selectedLongPress,
                    onSelect = { selectedLongPress = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newConfig = applyZoneEdit(
                        config = config,
                        zoneId = target.zoneId,
                        tap = selectedTap,
                        longPress = selectedLongPress
                    )
                    onUpdate(newConfig)
                }
            ) {
                Text("保存")
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
private fun ActionDropdownInDialog(
    current: TouchAction,
    onSelect: (TouchAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = current.label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.Transparent
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TouchAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        onSelect(action)
                        expanded = false
                    },
                    leadingIcon = if (action == current) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

private fun applyZoneEdit(
    config: TouchZoneConfig,
    zoneId: ZoneId,
    tap: TouchAction,
    longPress: TouchAction
): TouchZoneConfig {
    return when (zoneId) {
        ZoneId.LEFT -> config.copy(leftTap = tap, leftLongPress = longPress)
        ZoneId.CENTER -> config.copy(centerTap = tap, centerLongPress = longPress)
        ZoneId.RIGHT -> config.copy(rightTap = tap, rightLongPress = longPress)
        ZoneId.TOP_LEFT -> config.copy(topLeftTap = tap, topLeftLongPress = longPress)
        ZoneId.TOP_CENTER -> config.copy(topCenterTap = tap, topCenterLongPress = longPress)
        ZoneId.TOP_RIGHT -> config.copy(topRightTap = tap, topRightLongPress = longPress)
        ZoneId.BOTTOM_LEFT -> config.copy(bottomLeftTap = tap, bottomLeftLongPress = longPress)
        ZoneId.BOTTOM_CENTER -> config.copy(bottomCenterTap = tap, bottomCenterLongPress = longPress)
        ZoneId.BOTTOM_RIGHT -> config.copy(bottomRightTap = tap, bottomRightLongPress = longPress)
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
    onDoubleTap: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.pointerInput(layout, longPressMs) {
            detectTapGestures(
                onTap = { tapOffset ->
                    onZoneTap(resolveZoneFromOffset(layout, size.width.toFloat(), size.height.toFloat(), tapOffset))
                },
                onLongPress = { tapOffset ->
                    onZoneLongPress(
                        resolveZoneFromOffset(
                            layout,
                            size.width.toFloat(),
                            size.height.toFloat(),
                            tapOffset
                        )
                    )
                },
                onDoubleTap = onDoubleTap
            )
        }
    )
}

private fun resolveZoneFromOffset(
    layout: TouchZoneLayout,
    width: Float,
    height: Float,
    tapOffset: Offset
): ZoneId {
    val safeWidth = width.coerceAtLeast(1f)
    val safeHeight = height.coerceAtLeast(1f)
    val x = tapOffset.x.coerceIn(0f, safeWidth - 1f)
    val y = tapOffset.y.coerceIn(0f, safeHeight - 1f)

    return when (layout) {
        TouchZoneLayout.THREE_COLUMN -> when {
            x < safeWidth / 3f -> ZoneId.LEFT
            x < (safeWidth * 2f) / 3f -> ZoneId.CENTER
            else -> ZoneId.RIGHT
        }

        TouchZoneLayout.SIX_ZONE -> {
            val zoneIndex = when {
                x < safeWidth / 3f -> 0
                x < (safeWidth * 2f) / 3f -> 1
                else -> 2
            }
            val isTopRow = y < safeHeight / 3f

            when {
                isTopRow && zoneIndex == 0 -> ZoneId.TOP_LEFT
                isTopRow && zoneIndex == 1 -> ZoneId.TOP_CENTER
                isTopRow -> ZoneId.TOP_RIGHT
                zoneIndex == 0 -> ZoneId.BOTTOM_LEFT
                zoneIndex == 1 -> ZoneId.BOTTOM_CENTER
                else -> ZoneId.BOTTOM_RIGHT
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
    var text by remember(currentPage, totalPages) { mutableStateOf(currentPage.toString()) }
    val parsed = text.toIntOrNull()
    val isValid = totalPages > 0 && parsed != null && parsed in 1..totalPages

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ページ移動") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("現在: $currentPage / $totalPages ページ")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { text = "1" },
                        modifier = Modifier.weight(1f),
                        enabled = totalPages > 0
                    ) {
                        Text("最初")
                    }
                    Button(
                        onClick = { text = totalPages.toString() },
                        modifier = Modifier.weight(1f),
                        enabled = totalPages > 0
                    ) {
                        Text("最後")
                    }
                }
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

private enum class PageTurnAction {
    NEXT,
    PREVIOUS
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
        Text(text = "ページがありません")
    }
}