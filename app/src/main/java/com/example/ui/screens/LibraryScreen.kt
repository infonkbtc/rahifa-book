package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Announcement
import com.example.data.model.Book
import com.example.data.model.Category
import com.example.data.model.ReaderPreferences
import com.example.data.model.UiState
import com.example.ui.components.BookCoverArt
import com.example.ui.components.BookDetailBottomSheet
import com.example.ui.components.BookGridCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    booksState: UiState<List<Book>>,
    categoriesState: UiState<List<Category>>,
    announcements: List<Announcement>,
    preferences: ReaderPreferences,
    searchQuery: String,
    selectedCategoryId: String?,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenReader: (Book) -> Unit,
    onOpenSamplePreview: () -> Unit,
    onToggleBookmark: (String) -> Unit,
    lastReadPageFor: (String) -> Int
) {
    var selectedBookForDetails by remember { mutableStateOf<Book?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.testTag("open_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Open navigation menu"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_books_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("library_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Chips
            val categories = (categoriesState as? UiState.Success)?.data.orEmpty()
            if (categories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { onSelectCategory(null) },
                            label = { Text("All Books") },
                            modifier = Modifier.testTag("chip_all_categories")
                        )
                    }
                    items(categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = {
                                onSelectCategory(
                                    if (selectedCategoryId == category.id) null else category.id
                                )
                            },
                            label = { Text(category.name) },
                            modifier = Modifier.testTag("chip_cat_${category.id}")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            when (booksState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Could not load library",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = booksState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(onClick = onOpenSamplePreview) {
                                Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.preview_sample_reader))
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    val books = booksState.data
                    val featuredBooks = remember(books, searchQuery, selectedCategoryId) {
                        if (searchQuery.isBlank() && selectedCategoryId == null) {
                            books.filter { it.featured }
                        } else {
                            emptyList()
                        }
                    }

                    if (books.isEmpty()) {
                        EmptyLibraryView(
                            announcements = announcements,
                            isFiltered = searchQuery.isNotBlank() || selectedCategoryId != null,
                            onClearFilters = {
                                onSearchQueryChange("")
                                onSelectCategory(null)
                            },
                            onOpenSamplePreview = onOpenSamplePreview
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 156.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Active Announcements Banner
                            if (announcements.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    AnnouncementBanner(announcement = announcements.first())
                                }
                            }

                            // Featured Books Shelf
                            if (featuredBooks.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Star,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Featured Editions",
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                        }
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(featuredBooks, key = { "feat_${it.id}" }) { book ->
                                                FeaturedBookCard(
                                                    book = book,
                                                    onClick = { selectedBookForDetails = book }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedCategoryId != null) {
                                            categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Books"
                                        } else {
                                            "All Books"
                                        },
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = "${books.size} ${if (books.size == 1) "title" else "titles"}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            items(books, key = { it.id }) { book ->
                                BookGridCard(
                                    book = book,
                                    isBookmarked = preferences.bookmarkedBookIds.contains(book.id),
                                    lastReadPage = lastReadPageFor(book.id),
                                    onBookClick = { selectedBookForDetails = book },
                                    onBookmarkToggle = { onToggleBookmark(book.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedBookForDetails?.let { book ->
        BookDetailBottomSheet(
            book = book,
            isBookmarked = preferences.bookmarkedBookIds.contains(book.id),
            lastReadPage = lastReadPageFor(book.id),
            onDismiss = { selectedBookForDetails = null },
            onReadClick = {
                selectedBookForDetails = null
                onOpenReader(book)
            },
            onBookmarkToggle = { onToggleBookmark(book.id) }
        )
    }
}

@Composable
private fun AnnouncementBanner(announcement: Announcement) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.Campaign,
                contentDescription = "Announcement",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = announcement.message,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun FeaturedBookCard(
    book: Book,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(240.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverArt(
                book = book,
                modifier = Modifier
                    .width(68.dp)
                    .aspectRatio(0.68f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.categoryName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryView(
    announcements: List<Announcement>,
    isFiltered: Boolean,
    onClearFilters: () -> Unit,
    onOpenSamplePreview: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (announcements.isNotEmpty()) {
            AnnouncementBanner(announcement = announcements.first())
            Spacer(modifier = Modifier.height(20.dp))
        }

        Image(
            painter = painterResource(id = R.drawable.img_empty_library),
            contentDescription = "Empty library illustration",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(20.dp))
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (isFiltered) "No matching books" else stringResource(R.string.empty_library_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isFiltered) {
                "Try adjusting your search query or selecting a different category."
            } else {
                stringResource(R.string.empty_library_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (isFiltered) {
            OutlinedButton(onClick = onClearFilters) {
                Text("Clear Filters")
            }
        } else {
            Button(
                onClick = onOpenSamplePreview,
                modifier = Modifier.testTag("preview_sample_book_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.preview_sample_reader))
            }
        }
    }
}
