package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AdminAuthManager
import com.example.data.model.Announcement
import com.example.data.model.Book
import com.example.data.model.Category
import com.example.data.model.UiState
import com.example.data.repository.LibraryRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AdminSessionState {
    data object Unauthenticated : AdminSessionState
    data object Verifying : AdminSessionState
    data class AccessDenied(val email: String) : AdminSessionState
    data class Authenticated(val user: FirebaseUser) : AdminSessionState
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModel(
    private val repository: LibraryRepository,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _sessionState = MutableStateFlow<AdminSessionState>(AdminSessionState.Unauthenticated)
    val sessionState: StateFlow<AdminSessionState> = _sessionState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            AdminAuthManager.authStateFlow(auth).collect { user ->
                if (user == null) {
                    _sessionState.value = AdminSessionState.Unauthenticated
                } else {
                    _sessionState.value = AdminSessionState.Verifying
                    val isAdmin = AdminAuthManager.verifyIsAdmin(user, db)
                    _sessionState.value = if (isAdmin) {
                        AdminSessionState.Authenticated(user)
                    } else {
                        AdminSessionState.AccessDenied(user.email ?: user.uid)
                    }
                }
            }
        }
    }

    val allBooksState: StateFlow<UiState<List<Book>>> = sessionState
        .flatMapLatest { session ->
            if (session is AdminSessionState.Authenticated) {
                repository.observeAllBooks()
                    .map<List<Book>, UiState<List<Book>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load books"))
                    }
            } else {
                flowOf(UiState.Loading)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val allCategoriesState: StateFlow<UiState<List<Category>>> = sessionState
        .flatMapLatest { session ->
            if (session is AdminSessionState.Authenticated) {
                repository.observeCategories()
                    .map<List<Category>, UiState<List<Category>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load categories"))
                    }
            } else {
                flowOf(UiState.Loading)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val allAnnouncementsState: StateFlow<UiState<List<Announcement>>> = sessionState
        .flatMapLatest { session ->
            if (session is AdminSessionState.Authenticated) {
                repository.observeAllAnnouncements()
                    .map<List<Announcement>, UiState<List<Announcement>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load announcements"))
                    }
            } else {
                flowOf(UiState.Loading)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun setStatusMessage(message: String) {
        _statusMessage.value = message
    }

    fun saveBook(
        existingBook: Book?,
        title: String,
        author: String,
        category: Category,
        description: String,
        coverUri: Uri?,
        coverUrlInput: String,
        pdfUri: Uri?,
        pdfUrlInput: String,
        pageCount: Long,
        published: Boolean,
        featured: Boolean,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            _uploadProgress.value = 0f
            try {
                val isNew = existingBook == null
                val bookId = existingBook?.id?.takeIf { it.isNotBlank() } ?: repository.generateNewBookId()

                var finalCoverUrl = coverUrlInput.trim()
                if (coverUri != null) {
                    val coverResult = repository.uploadCoverImage(bookId, coverUri) { p ->
                        _uploadProgress.value = p * 0.35f
                    }
                    if (coverResult.isFailure) {
                        _statusMessage.value = coverResult.exceptionOrNull()?.message ?: "Cover upload failed"
                        return@launch
                    }
                    finalCoverUrl = coverResult.getOrThrow()
                }

                var finalPdfUrl = pdfUrlInput.trim()
                if (pdfUri != null) {
                    val pdfResult = repository.uploadBookPdf(bookId, pdfUri) { p ->
                        _uploadProgress.value = 0.35f + p * 0.60f
                    }
                    if (pdfResult.isFailure) {
                        _statusMessage.value = pdfResult.exceptionOrNull()?.message ?: "PDF upload failed"
                        return@launch
                    }
                    finalPdfUrl = pdfResult.getOrThrow()
                }

                if (finalPdfUrl.isBlank()) {
                    _statusMessage.value = "Please select a PDF file or enter a PDF URL."
                    return@launch
                }

                _uploadProgress.value = 0.98f
                val book = (existingBook ?: Book(id = bookId)).copy(
                    id = bookId,
                    title = title.trim(),
                    author = author.trim(),
                    categoryId = category.id,
                    categoryName = category.name,
                    description = description.trim(),
                    coverUrl = finalCoverUrl,
                    pdfUrl = finalPdfUrl,
                    pageCount = pageCount,
                    published = published,
                    featured = featured
                )
                val saveResult = repository.saveBook(book, isNew = isNew)
                if (saveResult.isSuccess) {
                    _statusMessage.value = if (isNew) "Book added to Rahifa Books" else "Book updated"
                    onComplete()
                } else {
                    _statusMessage.value = saveResult.exceptionOrNull()?.message ?: "Failed to save book"
                }
            } finally {
                _isBusy.value = false
                _uploadProgress.value = null
            }
        }
    }

    fun togglePublished(book: Book) {
        viewModelScope.launch {
            val res = repository.setBookPublished(book.id, !book.published)
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.message ?: "Failed to update visibility"
            }
        }
    }

    fun toggleFeatured(book: Book) {
        viewModelScope.launch {
            val res = repository.setBookFeatured(book.id, !book.featured)
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.message ?: "Failed to update featured flag"
            }
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val res = repository.deleteBook(book)
                _statusMessage.value = if (res.isSuccess) {
                    "Deleted \"${book.title}\""
                } else {
                    res.exceptionOrNull()?.message ?: "Failed to delete book"
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun createCategory(name: String, description: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val res = repository.createCategory(name, description)
                if (res.isSuccess) {
                    _statusMessage.value = "Category \"$name\" created"
                    onDone()
                } else {
                    _statusMessage.value = res.exceptionOrNull()?.message ?: "Failed to create category"
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun updateCategory(categoryId: String, name: String, description: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val res = repository.updateCategory(categoryId, name, description)
                if (res.isSuccess) {
                    _statusMessage.value = "Category updated"
                    onDone()
                } else {
                    _statusMessage.value = res.exceptionOrNull()?.message ?: "Failed to update category"
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val res = repository.deleteCategory(category.id)
                _statusMessage.value = if (res.isSuccess) {
                    "Category \"${category.name}\" deleted"
                } else {
                    res.exceptionOrNull()?.message ?: "Failed to delete category"
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun createAnnouncement(title: String, message: String, active: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val res = repository.createAnnouncement(title, message, active)
                if (res.isSuccess) {
                    _statusMessage.value = "Announcement published"
                    onDone()
                } else {
                    _statusMessage.value = res.exceptionOrNull()?.message ?: "Failed to create announcement"
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun toggleAnnouncement(announcement: Announcement) {
        viewModelScope.launch {
            val res = repository.setAnnouncementActive(announcement.id, !announcement.active)
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.message ?: "Failed to toggle announcement"
            }
        }
    }

    fun deleteAnnouncement(announcement: Announcement) {
        viewModelScope.launch {
            val res = repository.deleteAnnouncement(announcement.id)
            if (res.isFailure) {
                _statusMessage.value = res.exceptionOrNull()?.message ?: "Failed to delete announcement"
            }
        }
    }
}
