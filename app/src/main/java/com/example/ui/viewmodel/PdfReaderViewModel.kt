package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.pdf.PdfBookLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface PdfLoadState {
    data class Loading(val progress: Float = 0f) : PdfLoadState
    data class Ready(val pageCount: Int) : PdfLoadState
    data class Error(val message: String) : PdfLoadState
}

class PdfReaderViewModel : ViewModel() {
    private val _loadState = MutableStateFlow<PdfLoadState>(PdfLoadState.Loading())
    val loadState: StateFlow<PdfLoadState> = _loadState.asStateFlow()

    private val renderMutex = Mutex()
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var loadedBookKey: String? = null

    private val pageCache = object : LruCache<Int, Bitmap>(12) {}

    fun openBook(context: Context, bookId: String, pdfUrl: String) {
        val key = "$bookId|$pdfUrl"
        if (loadedBookKey == key && _loadState.value is PdfLoadState.Ready) return
        loadedBookKey = key

        viewModelScope.launch {
            _loadState.value = PdfLoadState.Loading(0.05f)
            closeRenderer()

            val fileResult = PdfBookLoader.loadSecurePdfFile(
                context = context.applicationContext,
                bookId = bookId,
                pdfUrl = pdfUrl,
                onProgress = { progress ->
                    _loadState.value = PdfLoadState.Loading(progress)
                }
            )

            fileResult.fold(
                onSuccess = { file ->
                    initRenderer(file)
                },
                onFailure = { error ->
                    _loadState.value = PdfLoadState.Error(
                        error.localizedMessage ?: "Unable to open this book."
                    )
                }
            )
        }
    }

    private suspend fun initRenderer(file: File) = withContext(Dispatchers.IO) {
        try {
            renderMutex.withLock {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                fileDescriptor = pfd
                pdfRenderer = renderer
                pageCache.evictAll()
                _loadState.value = PdfLoadState.Ready(renderer.pageCount.coerceAtLeast(1))
            }
        } catch (e: Exception) {
            _loadState.value = PdfLoadState.Error(
                e.localizedMessage ?: "Failed to render PDF document."
            )
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        pageCache.get(pageIndex)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        renderMutex.withLock {
            pageCache.get(pageIndex)?.let { cached ->
                if (!cached.isRecycled) return@withLock cached
            }
            val renderer = pdfRenderer ?: return@withLock null
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withLock null

            renderer.openPage(pageIndex).use { page ->
                val clampedWidth = targetWidthPx.coerceIn(720, 1800)
                val aspectRatio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                val targetHeight = (clampedWidth * aspectRatio).toInt().coerceAtLeast(100)

                val bitmap = Bitmap.createBitmap(clampedWidth, targetHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pageCache.put(pageIndex, bitmap)
                bitmap
            }
        }
    }

    private suspend fun closeRenderer() = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            try {
                pdfRenderer?.close()
            } catch (_: Exception) {
            }
            try {
                fileDescriptor?.close()
            } catch (_: Exception) {
            }
            pdfRenderer = null
            fileDescriptor = null
            pageCache.evictAll()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
            pageCache.evictAll()
        } catch (_: Exception) {
        }
    }
}
