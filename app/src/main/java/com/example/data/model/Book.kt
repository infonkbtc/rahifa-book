package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class Book(
    @DocumentId val id: String = "",
    val title: String = "",
    val author: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val pdfUrl: String = "",
    val published: Boolean = false,
    val featured: Boolean = false,
    val pageCount: Long = 0L,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(adminUid: String): Map<String, Any> = mapOf(
        "title" to title.trim(),
        "author" to author.trim(),
        "categoryId" to categoryId.trim(),
        "categoryName" to categoryName.trim(),
        "description" to description.trim(),
        "coverUrl" to coverUrl.trim(),
        "pdfUrl" to pdfUrl.trim(),
        "published" to published,
        "featured" to featured,
        "pageCount" to pageCount.coerceAtLeast(0L),
        "createdBy" to adminUid,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "title" to title.trim(),
        "author" to author.trim(),
        "categoryId" to categoryId.trim(),
        "categoryName" to categoryName.trim(),
        "description" to description.trim(),
        "coverUrl" to coverUrl.trim(),
        "pdfUrl" to pdfUrl.trim(),
        "published" to published,
        "featured" to featured,
        "pageCount" to pageCount.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
