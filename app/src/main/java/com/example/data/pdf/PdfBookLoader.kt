package com.example.data.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object PdfBookLoader {
    const val SAMPLE_BOOK_ID = "sample_preview_rahifa"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Downloads or opens a PDF exclusively inside the app's private cache directory.
     * Files are never placed in external storage or Downloads to enforce read-only protection.
     */
    suspend fun loadSecurePdfFile(
        context: Context,
        bookId: String,
        pdfUrl: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (bookId == SAMPLE_BOOK_ID || pdfUrl == "local://sample_preview") {
                onProgress(1f)
                return@withContext Result.success(generateSamplePreviewPdf(context))
            }

            val cacheDir = File(context.cacheDir, "secure_books").apply { mkdirs() }
            val safeName = bookId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_") + "_" +
                pdfUrl.hashCode().toUInt().toString(16) + ".pdf"
            val targetFile = File(cacheDir, safeName)

            if (targetFile.exists() && targetFile.length() > 100L) {
                onProgress(1f)
                return@withContext Result.success(targetFile)
            }

            // Support local content:// URIs only when previewing before upload
            if (pdfUrl.startsWith("content://")) {
                val uri = Uri.parse(pdfUrl)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.failure(IllegalStateException("Unable to read selected PDF"))
                onProgress(1f)
                return@withContext Result.success(targetFile)
            }

            val request = Request.Builder().url(pdfUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Failed to load book (HTTP ${response.code})")
                    )
                }
                val body = response.body
                    ?: return@withContext Result.failure(IllegalStateException("Empty PDF response"))
                val totalBytes = body.contentLength()
                val tempFile = File(cacheDir, "$safeName.tmp")
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                onProgress((downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                onProgress(1f)
                Result.success(targetFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Counts pages of a locally picked PDF Uri so Admin can automatically populate pageCount.
     */
    suspend fun countPdfPages(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
            pfd?.use { descriptor ->
                android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                    renderer.pageCount
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Generates a polished multi-page literary PDF booklet in private cache so users can
     * immediately experience Rahifa Books' 3D page-curl reader, sepia mode, and zoom.
     */
    fun generateSamplePreviewPdf(context: Context): File {
        val cacheDir = File(context.cacheDir, "secure_books").apply { mkdirs() }
        val file = File(cacheDir, "rahifa_sample_preview.pdf")
        if (file.exists() && file.length() > 1024L) return file

        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val chapters = listOf(
            Pair(
                "Rahifa Books",
                "THE ART OF SLOW READING\n\nA Digital Folio Edition\n\nSwipe horizontally to turn each page with 3D motion. Tap the screen to adjust Sepia Paper mode, Dark Ink mode, brightness, sound effects, or jump to any chapter."
            ),
            Pair(
                "Chapter I • The Quiet Hour",
                "Before the rush of notifications and endless feeds, reading was a sanctuary built from paper, ink, and stillness.\n\nEvery book asks for nothing more than an unhurried mind. When we slow our eyes across a well-set page, paragraphs stop competing for attention and begin to converse with memory.\n\nNotice how warm parchment softens the contrast between ink and page, reducing visual fatigue during long evening sessions."
            ),
            Pair(
                "Chapter II • Margins & Memory",
                "A good book remembers where you paused. Whether you close the cover at page three or page three hundred, your bookmark waits silently without demanding an account or interrupting your train of thought.\n\nUse two fingers to pinch-to-zoom into fine typography, or double-tap any paragraph to examine details up close. When you return to normal scale, swipe naturally across the spine to turn the leaf."
            ),
            Pair(
                "Chapter III • Light & Atmosphere",
                "Morning sun calls for crisp ivory paper. Late-night reading rewards Dark Ink mode, dimming harsh white glare while keeping letters legible.\n\nFor afternoon study, Warm Sepia bridges both worlds — echoing classic stitched editions kept in sunlit libraries.\n\nAdjust the brightness slider anytime from the top reader bar without leaving your current page."
            ),
            Pair(
                "Chapter IV • Protected Editions",
                "Every edition in Rahifa Books is delivered strictly for in-app reading. External file exports, copying, and outside downloads remain locked so authors' and curators' collections stay curated within the reading room.\n\nThank you for reading with Rahifa Books."
            )
        )

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(139, 58, 43)
            textSize = 24f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(156, 107, 25)
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.14f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(38, 30, 25)
            textSize = 15f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(210, 194, 172)
            strokeWidth = 1.5f
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 105, 92)
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        chapters.forEachIndexed { index, (heading, body) ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            // Decorative subtle border frame
            canvas.drawLine(56f, 64f, pageWidth - 56f, 64f, rulePaint)
            canvas.drawText("RAHIFA BOOKS EDITION", 56f, 54f, subtitlePaint)

            var y = 124f
            canvas.drawText(heading, 56f, y, titlePaint)
            y += 24f
            canvas.drawLine(56f, y, 180f, y, rulePaint)
            y += 40f

            // Word-wrap paragraphs cleanly inside margins
            val maxWidth = pageWidth - 112f
            body.split("\n").forEach { paragraph ->
                if (paragraph.isBlank()) {
                    y += 18f
                } else {
                    val words = paragraph.split(" ")
                    var line = StringBuilder()
                    for (word in words) {
                        val candidate = if (line.isEmpty()) word else "$line $word"
                        if (bodyPaint.measureText(candidate) > maxWidth) {
                            canvas.drawText(line.toString(), 56f, y, bodyPaint)
                            y += 24f
                            line = StringBuilder(word)
                        } else {
                            line = StringBuilder(candidate)
                        }
                    }
                    if (line.isNotEmpty()) {
                        canvas.drawText(line.toString(), 56f, y, bodyPaint)
                        y += 26f
                    }
                }
            }

            canvas.drawLine(56f, pageHeight - 70f, pageWidth - 56f, pageHeight - 70f, rulePaint)
            canvas.drawText("Page ${index + 1} of ${chapters.size}", pageWidth - 125f, pageHeight - 48f, footerPaint)
            canvas.drawText("Read-Only Reader Preview", 56f, pageHeight - 48f, footerPaint)

            document.finishPage(page)
        }

        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return file
    }
}
