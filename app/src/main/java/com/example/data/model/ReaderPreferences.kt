package com.example.data.model

data class ReaderPreferences(
    val theme: ReadingTheme = ReadingTheme.LIGHT,
    val brightness: Float = 0.85f,
    val soundMuted: Boolean = false,
    val bookmarkedBookIds: Set<String> = emptySet(),
    val lastReadPages: Map<String, Int> = emptyMap()
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
