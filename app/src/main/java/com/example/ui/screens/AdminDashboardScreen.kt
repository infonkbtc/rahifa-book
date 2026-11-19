package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.AdminAuthManager
import com.example.data.model.Announcement
import com.example.data.model.Book
import com.example.data.model.Category
import com.example.data.model.UiState
import com.example.ui.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    adminEmail: String,
    adminViewModel: AdminViewModel,
    onBackToLibrary: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val booksState by adminViewModel.allBooksState.collectAsStateWithLifecycle()
    val categoriesState by adminViewModel.allCategoriesState.collectAsStateWithLifecycle()
    val announcementsState by adminViewModel.allAnnouncementsState.collectAsStateWithLifecycle()
    val isBusy by adminViewModel.isBusy.collectAsStateWithLifecycle()
    val uploadProgress by adminViewModel.uploadProgress.collectAsStateWithLifecycle()
    val statusMessage by adminViewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            adminViewModel.clearStatusMessage()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showBookEditor by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<Book?>(null) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    var showAnnouncementDialog by remember { mutableStateOf(false) }

    val books = (booksState as? UiState.Success)?.data.orEmpty()
    val categories = (categoriesState as? UiState.Success)?.data.orEmpty()
    val announcements = (announcementsState as? UiState.Success)?.data.orEmpty()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Admin Dashboard", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = adminEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToLibrary) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Library"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { AdminAuthManager.signOut(credentialManager, scope) },
                        modifier = Modifier.testTag("admin_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Sign out Admin"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    when (selectedTab) {
                        0 -> {
                            editingBook = null
                            showBookEditor = true
                        }
                        1 -> {
                            editingCategory = null
                            showCategoryDialog = true
                        }
                        else -> showAnnouncementDialog = true
                    }
                },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = {
                    Text(
                        when (selectedTab) {
                            0 -> "Add Book"
                            1 -> "Add Category"
                            else -> "New Banner"
                        }
                    )
                },
                modifier = Modifier.testTag("admin_fab_add")
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Summary KPI Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdminStatCard(
                    label = "Total Books",
                    value = books.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                AdminStatCard(
                    label = "Published",
                    value = books.count { it.published }.toString(),
                    modifier = Modifier.weight(1f)
                )
                AdminStatCard(
                    label = "Categories",
                    value = categories.size.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Books") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Categories") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Banners") }
                )
            }

            when (selectedTab) {
                0 -> AdminBooksTab(
                    booksState = booksState,
                    onEdit = { book ->
                        editingBook = book
                        showBookEditor = true
                    },
                    onTogglePublished = adminViewModel::togglePublished,
                    onToggleFeatured = adminViewModel::toggleFeatured,
                    onDeleteRequest = { bookToDelete = it }
                )

                1 -> AdminCategoriesTab(
                    categoriesState = categoriesState,
                    books = books,
                    onEdit = { cat ->
                        editingCategory = cat
                        showCategoryDialog = true
                    },
                    onDeleteRequest = { categoryToDelete = it }
                )

                2 -> AdminAnnouncementsTab(
                    announcementsState = announcementsState,
                    onToggleActive = adminViewModel::toggleAnnouncement,
                    onDelete = adminViewModel::deleteAnnouncement
                )
            }
        }
    }

    if (showBookEditor) {
        AdminBookEditorSheet(
            existingBook = editingBook,
            categories = categories,
            isBusy = isBusy,
            uploadProgress = uploadProgress,
            onDismiss = { showBookEditor = false },
            onSave = { title, author, category, description, coverUri, coverUrl, pdfUri, pdfUrl, pages, pub, feat ->
                adminViewModel.saveBook(
                    existingBook = editingBook,
                    title = title,
                    author = author,
                    category = category,
                    description = description,
                    coverUri = coverUri,
                    coverUrlInput = coverUrl,
                    pdfUri = pdfUri,
                    pdfUrlInput = pdfUrl,
                    pageCount = pages,
                    published = pub,
                    featured = feat,
                    onComplete = { showBookEditor = false }
                )
            }
        )
    }

    bookToDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("Delete Book?") },
            text = { Text("Permanently remove \"${book.title}\" and its files from Rahifa Books?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        adminViewModel.deleteBook(book)
                        bookToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCategoryDialog) {
        CategoryEditorDialog(
            existingCategory = editingCategory,
            onDismiss = { showCategoryDialog = false },
            onConfirm = { name, desc ->
                val existing = editingCategory
                if (existing == null) {
                    adminViewModel.createCategory(name, desc) { showCategoryDialog = false }
                } else {
                    adminViewModel.updateCategory(existing.id, name, desc) { showCategoryDialog = false }
                }
            }
        )
    }

    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category?") },
            text = { Text("Remove \"${cat.name}\"? Books currently referencing this category should be reassigned first.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        adminViewModel.deleteCategory(cat)
                        categoryToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAnnouncementDialog) {
        AnnouncementEditorDialog(
            onDismiss = { showAnnouncementDialog = false },
            onConfirm = { title, message, active ->
                adminViewModel.createAnnouncement(title, message, active) {
                    showAnnouncementDialog = false
                }
            }
        )
    }
}

@Composable
private fun AdminStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AdminBooksTab(
    booksState: UiState<List<Book>>,
    onEdit: (Book) -> Unit,
    onTogglePublished: (Book) -> Unit,
    onToggleFeatured: (Book) -> Unit,
    onDeleteRequest: (Book) -> Unit
) {
    when (booksState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(booksState.message, color = MaterialTheme.colorScheme.error)
        }
        is UiState.Success -> {
            if (booksState.data.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No books yet. Create a category first, then tap Add Book.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(booksState.data, key = { it.id }) { book ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(book.title, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${book.author} • ${book.categoryName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onToggleFeatured(book) }) {
                                        Icon(
                                            imageVector = if (book.featured) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                            contentDescription = "Toggle Featured",
                                            tint = if (book.featured) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onEdit(book) }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit book")
                                    }
                                    IconButton(onClick = { onDeleteRequest(book) }) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Delete book",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (book.published) "Published (Visible to readers)" else "Hidden Draft",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (book.published) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Switch(
                                        checked = book.published,
                                        onCheckedChange = { onTogglePublished(book) }
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AdminCategoriesTab(
    categoriesState: UiState<List<Category>>,
    books: List<Book>,
    onEdit: (Category) -> Unit,
    onDeleteRequest: (Category) -> Unit
) {
    when (categoriesState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(categoriesState.message, color = MaterialTheme.colorScheme.error)
        }
        is UiState.Success -> {
            if (categoriesState.data.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No categories yet. Tap Add Category to organize your library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(categoriesState.data, key = { it.id }) { cat ->
                        val count = books.count { it.categoryId == cat.id }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cat.name, style = MaterialTheme.typography.titleMedium)
                                    if (cat.description.isNotBlank()) {
                                        Text(
                                            cat.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "$count books assigned",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { onEdit(cat) }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Rename category")
                                }
                                IconButton(onClick = { onDeleteRequest(cat) }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Delete category",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AdminAnnouncementsTab(
    announcementsState: UiState<List<Announcement>>,
    onToggleActive: (Announcement) -> Unit,
    onDelete: (Announcement) -> Unit
) {
    when (announcementsState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(announcementsState.message, color = MaterialTheme.colorScheme.error)
        }
        is UiState.Success -> {
            if (announcementsState.data.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No announcements yet. Tap New Banner to broadcast a library notice.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(announcementsState.data, key = { it.id }) { ann ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Campaign,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        ann.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { onDelete(ann) }) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Delete banner",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Text(ann.message, style = MaterialTheme.typography.bodySmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (ann.active) "Active on Home Screen" else "Paused",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Switch(
                                        checked = ann.active,
                                        onCheckedChange = { onToggleActive(ann) }
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryEditorDialog(
    existingCategory: Category?,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember(existingCategory) { mutableStateOf(existingCategory?.name ?: "") }
    var description by remember(existingCategory) { mutableStateOf(existingCategory?.description ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingCategory == null) "New Category" else "Rename Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name, description) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AnnouncementEditorDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Library Banner") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Headline *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Announcement Message *") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show immediately", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank() && message.isNotBlank()) onConfirm(title, message, active) },
                enabled = title.isNotBlank() && message.isNotBlank()
            ) {
                Text("Publish")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
