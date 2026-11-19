package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Announcement
import com.example.data.model.Book
import com.example.data.model.Category
import com.example.data.model.ReaderPreferences
import com.example.data.model.ReadingTheme
import com.example.data.model.UiState
import com.example.data.pdf.PdfBookLoader
import com.example.data.repository.LibraryRepository
import com.example.data.repository.ReaderPreferencesRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: ReaderPreferencesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    val readerPreferences: StateFlow<ReaderPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ReaderPreferences()
        )

    val publishedBooksState: StateFlow<UiState<List<Book>>> = libraryRepository.observePublishedBooks()
        .map<List<Book>, UiState<List<Book>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(UiState.Error(root.message ?: "Unable to load books"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val categoriesState: StateFlow<UiState<List<Category>>> = libraryRepository.observeCategories()
        .map<List<Category>, UiState<List<Category>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(UiState.Error(root.message ?: "Unable to load categories"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val activeAnnouncements: StateFlow<List<Announcement>> = libraryRepository.observeActiveAnnouncements()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val filteredBooksState: StateFlow<UiState<List<Book>>> = combine(
        publishedBooksState,
        searchQuery,
        selectedCategoryId
    ) { state, query, categoryId ->
        when (state) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> state
            is UiState.Success -> {
                val q = query.trim().lowercase()
                val filtered = state.data.filter { book ->
                    val matchesCategory = categoryId == null || book.categoryId == categoryId
                    val matchesSearch = q.isEmpty() ||
                        book.title.lowercase().contains(q) ||
                        book.author.lowercase().contains(q) ||
                        book.categoryName.lowercase().contains(q)
                    matchesCategory && matchesSearch
                }
                UiState.Success(filtered)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(categoryId: String?) {
        _selectedCategoryId.value = categoryId
    }

    fun setReadingTheme(theme: ReadingTheme) {
        viewModelScope.launch {
            preferencesRepository.setReadingTheme(theme)
        }
    }

    fun setBrightness(brightness: Float) {
        viewModelScope.launch {
            preferencesRepository.setBrightness(brightness)
        }
    }

    fun toggleSoundMuted() {
        val current = readerPreferences.value.soundMuted
        viewModelScope.launch {
            preferencesRepository.setSoundMuted(!current)
        }
    }

    fun toggleBookmark(bookId: String) {
        viewModelScope.launch {
            preferencesRepository.toggleBookmark(bookId)
        }
    }

    fun saveLastReadPage(bookId: String, pageIndex: Int) {
        viewModelScope.launch {
            preferencesRepository.saveLastReadPage(bookId, pageIndex)
        }
    }

    fun lastReadPageFor(bookId: String): Int {
        val key = preferencesRepository.sanitizedBookKey(bookId)
        return readerPreferences.value.lastReadPages[key] ?: 0
    }

    fun samplePreviewBook(): Book = Book(
        id = PdfBookLoader.SAMPLE_BOOK_ID,
        title = "The Art of Slow Reading",
        author = "Rahifa Editorial Press",
        categoryId = "sample",
        categoryName = "Editorial Preview",
        description = "An interactive five-page folio demonstrating Rahifa Books' 3D page-curl reader, Warm Sepia paper comfort mode, Dark Ink mode, pinch-to-zoom, and automatic bookmarking.",
        coverUrl = "",
        pdfUrl = "local://sample_preview",
        published = true,
        featured = true,
        pageCount = 5L
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
