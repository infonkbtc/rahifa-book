package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ReaderPreferences
import com.example.data.model.ReadingTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "rahifa_reader_prefs"
)

class ReaderPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.readerDataStore

    private object Keys {
        val READING_THEME = stringPreferencesKey("reading_theme")
        val BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val SOUND_MUTED = booleanPreferencesKey("sound_muted")
        val BOOKMARKED_IDS = stringSetPreferencesKey("bookmarked_book_ids")
        fun lastPageKey(bookId: String) = intPreferencesKey("last_page_${bookId.replace(Regex("[^a-zA-Z0-9_]"), "_")}")
    }

    val preferencesFlow: Flow<ReaderPreferences> = dataStore.data.map { prefs ->
        val theme = ReadingTheme.fromName(prefs[Keys.READING_THEME])
        val brightness = prefs[Keys.BRIGHTNESS] ?: 0.85f
        val soundMuted = prefs[Keys.SOUND_MUTED] ?: false
        val bookmarks = prefs[Keys.BOOKMARKED_IDS] ?: emptySet()

        val lastPages = buildMap {
            prefs.asMap().forEach { (key, value) ->
                if (key.name.startsWith("last_page_") && value is Int) {
                    put(key.name.removePrefix("last_page_"), value)
                }
            }
        }

        ReaderPreferences(
            theme = theme,
            brightness = brightness,
            soundMuted = soundMuted,
            bookmarkedBookIds = bookmarks,
            lastReadPages = lastPages
        )
    }

    suspend fun setReadingTheme(theme: ReadingTheme) {
        dataStore.edit { it[Keys.READING_THEME] = theme.name }
    }

    suspend fun setBrightness(brightness: Float) {
        dataStore.edit { it[Keys.BRIGHTNESS] = brightness.coerceIn(0.08f, 1.0f) }
    }

    suspend fun setSoundMuted(muted: Boolean) {
        dataStore.edit { it[Keys.SOUND_MUTED] = muted }
    }

    suspend fun toggleBookmark(bookId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.BOOKMARKED_IDS]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(bookId)) {
                current.remove(bookId)
            }
            prefs[Keys.BOOKMARKED_IDS] = current
        }
    }

    suspend fun saveLastReadPage(bookId: String, pageIndex: Int) {
        if (bookId.isBlank()) return
        dataStore.edit { prefs ->
            prefs[Keys.lastPageKey(bookId)] = pageIndex.coerceAtLeast(0)
        }
    }

    fun sanitizedBookKey(bookId: String): String =
        bookId.replace(Regex("[^a-zA-Z0-9_]"), "_")
}
