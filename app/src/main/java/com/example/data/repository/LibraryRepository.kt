package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.Announcement
import com.example.data.model.Book
import com.example.data.model.Category
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class LibraryRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val storage: FirebaseStorage = Firebase.storage
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireAdminUid(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Admin must sign in with Google before modifying library data.")

    // --- Public Read-Only Streams (No Sign-In Required) ---

    fun observePublishedBooks(): Flow<List<Book>> = flow {
        val path = "books"
        emitAll(
            db.collection(path)
                .whereEqualTo("published", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Book::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    (firestoreError ?: (error as? Exception))?.let {
                        handleFirestoreError(it, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCategories(): Flow<List<Category>> = flow {
        val path = "categories"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Category::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    (firestoreError ?: (error as? Exception))?.let {
                        handleFirestoreError(it, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeActiveAnnouncements(): Flow<List<Announcement>> = flow {
        val path = "announcements"
        emitAll(
            db.collection(path)
                .whereEqualTo("active", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Announcement::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    (firestoreError ?: (error as? Exception))?.let {
                        handleFirestoreError(it, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // --- Admin Streams (Caller MUST gate behind verified Admin session) ---

    fun observeAllBooks(): Flow<List<Book>> = flow {
        val uid = requireAdminUid()
        val path = "books"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Book::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        (firestoreError ?: (error as? Exception))?.let {
                            handleFirestoreError(it, OperationType.LIST, path)
                        }
                    }
                    throw error
                }
        )
    }

    fun observeAllAnnouncements(): Flow<List<Announcement>> = flow {
        val uid = requireAdminUid()
        val path = "announcements"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Announcement::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        (firestoreError ?: (error as? Exception))?.let {
                            handleFirestoreError(it, OperationType.LIST, path)
                        }
                    }
                    throw error
                }
        )
    }

    // --- Admin Storage Uploads ---

    suspend fun uploadCoverImage(
        bookId: String,
        imageUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = try {
        requireAdminUid()
        val safeId = sanitizeDocId(bookId)
        val ref = storage.reference.child("covers/$safeId/cover_${System.currentTimeMillis()}.jpg")
        val task = ref.putFile(imageUri)
        task.addOnProgressListener { snap ->
            if (snap.totalByteCount > 0) {
                onProgress(snap.bytesTransferred.toFloat() / snap.totalByteCount.toFloat())
            }
        }
        task.await()
        val downloadUrl = ref.downloadUrl.await().toString()
        Result.success(downloadUrl)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(
            IllegalStateException(
                "Cover upload failed (${e.localizedMessage ?: "Firebase Storage error"}). " +
                    "Ensure Firebase Storage is enabled in Firebase Console, or paste a direct Cover URL.",
                e
            )
        )
    }

    suspend fun uploadBookPdf(
        bookId: String,
        pdfUri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = try {
        requireAdminUid()
        val safeId = sanitizeDocId(bookId)
        val ref = storage.reference.child("books/$safeId/book_${System.currentTimeMillis()}.pdf")
        val task = ref.putFile(pdfUri)
        task.addOnProgressListener { snap ->
            if (snap.totalByteCount > 0) {
                onProgress(snap.bytesTransferred.toFloat() / snap.totalByteCount.toFloat())
            }
        }
        task.await()
        val downloadUrl = ref.downloadUrl.await().toString()
        Result.success(downloadUrl)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(
            IllegalStateException(
                "PDF upload failed (${e.localizedMessage ?: "Firebase Storage error"}). " +
                    "Ensure Firebase Storage is enabled in Firebase Console, or paste a direct PDF URL.",
                e
            )
        )
    }

    // --- Admin Category Mutations ---

    suspend fun createCategory(name: String, description: String): Result<String> {
        val cleanName = name.trim()
        val cleanDesc = description.trim()
        val docId = "cat_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val docPath = "categories/$docId"
        return try {
            requireAdminUid()
            require(cleanName.length in 1..60) { "Category name must be 1–60 characters." }
            require(cleanDesc.length <= 300) { "Description must be 300 characters or fewer." }
            val category = Category(name = cleanName, description = cleanDesc, bookCount = 0L)
            db.collection("categories").document(docId).set(category.toCreateMap()).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCategory(categoryId: String, name: String, description: String): Result<Unit> {
        val cleanName = name.trim()
        val cleanDesc = description.trim()
        val docPath = "categories/$categoryId"
        return try {
            requireAdminUid()
            require(categoryId.isNotBlank()) { "Category ID must not be blank." }
            require(cleanName.length in 1..60) { "Category name must be 1–60 characters." }
            require(cleanDesc.length <= 300) { "Description must be 300 characters or fewer." }
            db.collection("categories").document(categoryId).update(
                mapOf(
                    "name" to cleanName,
                    "description" to cleanDesc,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        val docPath = "categories/$categoryId"
        return try {
            requireAdminUid()
            require(categoryId.isNotBlank()) { "Category ID must not be blank." }
            db.collection("categories").document(categoryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Admin Book Mutations ---

    fun generateNewBookId(): String =
        "book_" + UUID.randomUUID().toString().replace("-", "").take(16)

    suspend fun saveBook(book: Book, isNew: Boolean): Result<String> {
        val docId = if (isNew || book.id.isBlank()) generateNewBookId() else book.id
        val docPath = "books/$docId"
        return try {
            val uid = requireAdminUid()
            require(book.title.trim().length in 1..150) { "Title must be 1–150 characters." }
            require(book.author.trim().length in 1..100) { "Author must be 1–100 characters." }
            require(book.categoryId.trim().isNotBlank()) { "Please choose a category." }
            require(book.categoryName.trim().length in 1..60) { "Category name must be 1–60 characters." }
            require(book.description.trim().length <= 2000) { "Description max 2000 characters." }
            require(book.coverUrl.trim().length <= 1000) { "Cover URL max 1000 characters." }
            require(book.pdfUrl.trim().length in 1..1000) { "PDF file or URL is required." }

            val docRef = db.collection("books").document(docId)
            if (isNew) {
                docRef.set(book.toCreateMap(uid)).await()
            } else {
                docRef.update(book.toUpdateMap()).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (isNew) OperationType.CREATE else OperationType.UPDATE,
                docPath
            )
            Result.failure(e)
        }
    }

    suspend fun setBookPublished(bookId: String, published: Boolean): Result<Unit> {
        val docPath = "books/$bookId"
        return try {
            requireAdminUid()
            require(bookId.isNotBlank()) { "Book ID must not be blank." }
            db.collection("books").document(bookId).update(
                mapOf(
                    "published" to published,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setBookFeatured(bookId: String, featured: Boolean): Result<Unit> {
        val docPath = "books/$bookId"
        return try {
            requireAdminUid()
            require(bookId.isNotBlank()) { "Book ID must not be blank." }
            db.collection("books").document(bookId).update(
                mapOf(
                    "featured" to featured,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteBook(book: Book): Result<Unit> {
        val docPath = "books/${book.id}"
        return try {
            requireAdminUid()
            require(book.id.isNotBlank()) { "Book ID must not be blank." }
            // Best-effort cleanup of Firebase Storage files if hosted on firebasestorage
            tryDeleteStorageUrl(book.pdfUrl)
            tryDeleteStorageUrl(book.coverUrl)
            db.collection("books").document(book.id).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun tryDeleteStorageUrl(url: String) {
        if (url.contains("firebasestorage", ignoreCase = true)) {
            try {
                storage.getReferenceFromUrl(url).delete().await()
            } catch (_: Exception) {
                // Ignore if already deleted or external URL
            }
        }
    }

    // --- Admin Announcement Mutations ---

    suspend fun createAnnouncement(title: String, message: String, active: Boolean): Result<String> {
        val cleanTitle = title.trim()
        val cleanMsg = message.trim()
        val docId = "ann_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val docPath = "announcements/$docId"
        return try {
            requireAdminUid()
            require(cleanTitle.length in 1..120) { "Announcement title must be 1–120 characters." }
            require(cleanMsg.length in 1..500) { "Announcement message must be 1–500 characters." }
            val ann = Announcement(title = cleanTitle, message = cleanMsg, active = active)
            db.collection("announcements").document(docId).set(ann.toCreateMap()).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setAnnouncementActive(announcementId: String, active: Boolean): Result<Unit> {
        val docPath = "announcements/$announcementId"
        return try {
            requireAdminUid()
            require(announcementId.isNotBlank()) { "Announcement ID must not be blank." }
            db.collection("announcements").document(announcementId).update(
                mapOf(
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAnnouncement(announcementId: String): Result<Unit> {
        val docPath = "announcements/$announcementId"
        return try {
            requireAdminUid()
            require(announcementId.isNotBlank()) { "Announcement ID must not be blank." }
            db.collection("announcements").document(announcementId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun sanitizeDocId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "item" }
}
