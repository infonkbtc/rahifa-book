package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Book
import com.example.data.model.ReaderPreferences
import com.example.ui.components.BookDetailBottomSheet
import com.example.ui.components.BookGridCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    allPublishedBooks: List<Book>,
    samplePreviewBook: Book,
    preferences: ReaderPreferences,
    onOpenDrawer: () -> Unit,
    onGoToLibrary: () -> Unit,
    onOpenReader: (Book) -> Unit,
    onToggleBookmark: (String) -> Unit,
    lastReadPageFor: (String) -> Int
) {
    var selectedBook by remember { mutableStateOf<Book?>(null) }

    val bookmarkedBooks = remember(allPublishedBooks, preferences.bookmarkedBookIds) {
        val catalog = (allPublishedBooks + samplePreviewBook).distinctBy { it.id }
        catalog.filter { preferences.bookmarkedBookIds.contains(it.id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_bookmarks),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Open navigation menu"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (bookmarkedBooks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Bookmarks,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No bookmarks saved",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap the bookmark ribbon on any book card or inside the reader to save it here alongside your last-read page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = onGoToLibrary) {
                        Text("Browse Library")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(bookmarkedBooks, key = { it.id }) { book ->
                    BookGridCard(
                        book = book,
                        isBookmarked = true,
                        lastReadPage = lastReadPageFor(book.id),
                        onBookClick = { selectedBook = book },
                        onBookmarkToggle = { onToggleBookmark(book.id) }
                    )
                }
            }
        }
    }

    selectedBook?.let { book ->
        BookDetailBottomSheet(
            book = book,
            isBookmarked = preferences.bookmarkedBookIds.contains(book.id),
            lastReadPage = lastReadPageFor(book.id),
            onDismiss = { selectedBook = null },
            onReadClick = {
                selectedBook = null
                onOpenReader(book)
            },
            onBookmarkToggle = { onToggleBookmark(book.id) }
        )
    }
}
