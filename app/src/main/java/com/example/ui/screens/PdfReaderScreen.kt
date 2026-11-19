package com.example.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Book
import com.example.data.model.ReaderPreferences
import com.example.data.model.ReadingTheme
import com.example.ui.audio.PageTurnSoundPlayer
import com.example.ui.viewmodel.PdfLoadState
import com.example.ui.viewmodel.PdfReaderViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PdfReaderScreen(
    book: Book,
    initialPage: Int,
    preferences: ReaderPreferences,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onThemeChange: (ReadingTheme) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleBookmark: () -> Unit,
    readerViewModel: PdfReaderViewModel = viewModel(key = book.id)
) {
    val context = LocalContext.current
    val loadState by readerViewModel.loadState.collectAsStateWithLifecycle()
    val soundPlayer = remember { PageTurnSoundPlayer() }
    val scope = rememberCoroutineScope()

    var showControls by remember { mutableStateOf(true) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Enforce FLAG_SECURE (read-only screen protection) & brightness while inside reader
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
        }
    }

    LaunchedEffect(preferences.brightness) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val lp = window.attributes
        lp.screenBrightness = preferences.brightness.coerceIn(0.08f, 1.0f)
        window.attributes = lp
    }

    LaunchedEffect(book.id, book.pdfUrl) {
        readerViewModel.openBook(context, book.id, book.pdfUrl)
    }

    val backdropColor = when (preferences.theme) {
        ReadingTheme.LIGHT -> Color(0xFFEBE4D8)
        ReadingTheme.SEPIA -> Color(0xFFE3D3B5)
        ReadingTheme.DARK -> Color(0xFF0E0C0A)
    }

    val pageFilter = remember(preferences.theme) {
        when (preferences.theme) {
            ReadingTheme.LIGHT -> null
            ReadingTheme.SEPIA -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.95f, 0f, 0f, 0f, -6f,
                        0f, 0.89f, 0f, 0f, -10f,
                        0f, 0f, 0.75f, 0f, -16f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            ReadingTheme.DARK -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -0.86f, 0f, 0f, 0f, 232f,
                        0f, -0.86f, 0f, 0f, 224f,
                        0f, 0f, -0.86f, 0f, 214f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdropColor)
    ) {
        when (val state = loadState) {
            is PdfLoadState.Loading -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Opening \"${book.title}\"…",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0.05f, 1f) },
                        modifier = Modifier.fillMaxWidth(0.65f)
                    )
                }
            }

            is PdfLoadState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Unable to Open Book",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = onBack) {
                        Text("Back to Library")
                    }
                }
            }

            is PdfLoadState.Ready -> {
                val totalPages = state.pageCount
                val clampedInitial = initialPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                val pagerState = rememberPagerState(
                    initialPage = clampedInitial,
                    pageCount = { totalPages }
                )
                var lastSoundPage by remember { mutableIntStateOf(clampedInitial) }

                LaunchedEffect(pagerState.currentPage) {
                    onPageChanged(pagerState.currentPage)
                    if (pagerState.currentPage != lastSoundPage) {
                        lastSoundPage = pagerState.currentPage
                        soundPlayer.playPageTurn(preferences.soundMuted)
                    }
                    // Reset zoom when changing page
                    zoomScale = 1f
                    panOffset = Offset.Zero
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { showControls = !showControls },
                                onDoubleTap = {
                                    if (zoomScale > 1.1f) {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    } else {
                                        zoomScale = 2.1f
                                    }
                                }
                            )
                        }
                        .pointerInput(zoomScale > 1.05f) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pointerCount = event.changes.count { it.pressed }
                                    if (pointerCount >= 2 || zoomScale > 1.05f) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        if (zoomChange != 1f || (zoomScale > 1.05f && panChange != Offset.Zero)) {
                                            val newZoom = (zoomScale * zoomChange).coerceIn(1f, 3.2f)
                                            zoomScale = newZoom
                                            if (newZoom <= 1.02f) {
                                                zoomScale = 1f
                                                panOffset = Offset.Zero
                                            } else {
                                                val maxX = (size.width * (newZoom - 1f)) / 2f
                                                val maxY = (size.height * (newZoom - 1f)) / 2f
                                                panOffset = Offset(
                                                    x = (panOffset.x + panChange.x).coerceIn(-maxX, maxX),
                                                    y = (panOffset.y + panChange.y).coerceIn(-maxY, maxY)
                                                )
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                ) {
                    val density = LocalDensity.current
                    val targetRenderWidth = with(density) { maxWidth.toPx().roundToInt() }

                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = zoomScale <= 1.05f,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        var renderedBitmap by remember(pageIndex, book.id) {
                            mutableStateOf<Bitmap?>(null)
                        }

                        LaunchedEffect(pageIndex, book.id, targetRenderWidth) {
                            renderedBitmap = readerViewModel.renderPage(pageIndex, targetRenderWidth)
                        }

                        val pageOffset = (
                            (pagerState.currentPage - pageIndex) +
                                pagerState.currentPageOffsetFraction
                            ).coerceIn(-1f, 1f)
                        val absOffset = abs(pageOffset)

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 14.dp)
                                .graphicsLayer {
                                    cameraDistance = 20f * density.density
                                    transformOrigin = if (pageOffset >= 0f) {
                                        TransformOrigin(0f, 0.5f)
                                    } else {
                                        TransformOrigin(1f, 0.5f)
                                    }
                                    rotationY = -pageOffset * 58f
                                    val curlScale = 1f - (absOffset * 0.08f)
                                    scaleX = curlScale * (if (pageIndex == pagerState.currentPage) zoomScale else 1f)
                                    scaleY = curlScale * (if (pageIndex == pagerState.currentPage) zoomScale else 1f)
                                    if (pageIndex == pagerState.currentPage) {
                                        translationX = panOffset.x
                                        translationY = panOffset.y
                                    }
                                    alpha = 1f - (absOffset * 0.22f)
                                }
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when (preferences.theme) {
                                        ReadingTheme.LIGHT -> Color.White
                                        ReadingTheme.SEPIA -> Color(0xFFF3E5C8)
                                        ReadingTheme.DARK -> Color(0xFF1A1715)
                                    }
                                )
                                .drawWithContent {
                                    drawContent()
                                    // Book spine crease shadow on left edge
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.16f),
                                                Color.Transparent
                                            ),
                                            startX = 0f,
                                            endX = size.width * 0.07f
                                        )
                                    )
                                    // Dynamic 3D page curl fold shadow & paper highlight
                                    if (absOffset > 0.01f) {
                                        drawRect(
                                            color = Color.Black.copy(alpha = (absOffset * 0.28f).coerceAtMost(0.30f))
                                        )
                                        val highlightCenter = if (pageOffset >= 0f) size.width * 0.25f else size.width * 0.75f
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.White.copy(alpha = absOffset * 0.18f),
                                                    Color.Transparent
                                                ),
                                                startX = (highlightCenter - size.width * 0.2f).coerceAtLeast(0f),
                                                endX = (highlightCenter + size.width * 0.2f).coerceAtMost(size.width)
                                            )
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = renderedBitmap
                            if (bmp != null && !bmp.isRecycled) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page ${pageIndex + 1} of $totalPages",
                                    contentScale = ContentScale.Fit,
                                    colorFilter = pageFilter,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    // Extra soft night dimmer for ultra-low brightness comfort
                    if (preferences.brightness < 0.3f) {
                        val dimAlpha = ((0.3f - preferences.brightness) * 1.4f).coerceIn(0f, 0.45f)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = dimAlpha))
                        )
                    }

                    // Top Reader Chrome
                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it },
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            tonalElevation = 6.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier.testTag("reader_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Exit reader"
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = "Protected read-only",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = book.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "Page ${pagerState.currentPage + 1} of $totalPages • Auto-saved",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = onToggleMute,
                                    modifier = Modifier.testTag("reader_sound_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (preferences.soundMuted) {
                                            Icons.AutoMirrored.Rounded.VolumeOff
                                        } else {
                                            Icons.AutoMirrored.Rounded.VolumeUp
                                        },
                                        contentDescription = if (preferences.soundMuted) {
                                            "Unmute page turn sound"
                                        } else {
                                            "Mute page turn sound"
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = onToggleBookmark,
                                    modifier = Modifier.testTag("reader_bookmark_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (isBookmarked) {
                                            Icons.Rounded.Bookmark
                                        } else {
                                            Icons.Rounded.BookmarkBorder
                                        },
                                        contentDescription = "Toggle bookmark",
                                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Reading Comfort Console
                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            tonalElevation = 8.dp,
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                // Page jump slider & prev/next arrows
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (pagerState.currentPage > 0) {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                }
                                            }
                                        },
                                        enabled = pagerState.currentPage > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                            contentDescription = "Previous page"
                                        )
                                    }

                                    if (totalPages > 1) {
                                        Slider(
                                            value = pagerState.currentPage.toFloat(),
                                            onValueChange = { target ->
                                                val page = target.roundToInt().coerceIn(0, totalPages - 1)
                                                if (page != pagerState.currentPage) {
                                                    scope.launch { pagerState.scrollToPage(page) }
                                                }
                                            },
                                            valueRange = 0f..(totalPages - 1).toFloat(),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("reader_page_slider")
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }

                                    IconButton(
                                        onClick = {
                                            if (pagerState.currentPage < totalPages - 1) {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            }
                                        },
                                        enabled = pagerState.currentPage < totalPages - 1
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = "Next page"
                                        )
                                    }
                                }

                                // Reading Theme chips + Brightness slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ReadingTheme.entries.forEach { theme ->
                                        FilterChip(
                                            selected = preferences.theme == theme,
                                            onClick = { onThemeChange(theme) },
                                            label = { Text(theme.label) },
                                            modifier = Modifier.testTag("reader_theme_${theme.name.lowercase()}")
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Brightness6,
                                        contentDescription = "Brightness",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Slider(
                                        value = preferences.brightness,
                                        onValueChange = onBrightnessChange,
                                        valueRange = 0.08f..1.0f,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("reader_brightness_slider")
                                    )
                                    Text(
                                        text = "${(preferences.brightness * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.width(40.dp),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
