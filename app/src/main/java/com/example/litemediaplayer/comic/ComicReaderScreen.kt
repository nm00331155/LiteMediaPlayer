package com.example.litemediaplayer.comic

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ComicReaderScreen(
    bookId: Long,
    onBack: () -> Unit,
    viewModel: ComicReaderViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(bookId) {
        viewModel.openBook(bookId)
    }

    val pages = uiState.pages
    val currentPage = pages.getOrNull(uiState.currentPage)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(uiState.settings.backgroundColorArgb))
    ) {
        if (showControls) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onBack) {
                    Text(text = "戻る")
                }
                Text(
                    text = "${uiState.currentPage + 1}/${uiState.pages.size}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
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

                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable {
                                        if (uiState.settings.readingDirection == ReadingDirection.RTL) {
                                            viewModel.nextPage()
                                        } else {
                                            viewModel.previousPage()
                                        }
                                    }
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable { showControls = !showControls }
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable {
                                        if (uiState.settings.readingDirection == ReadingDirection.RTL) {
                                            viewModel.previousPage()
                                        } else {
                                            viewModel.nextPage()
                                        }
                                    }
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
