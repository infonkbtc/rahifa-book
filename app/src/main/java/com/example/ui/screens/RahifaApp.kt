package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.model.ReadingTheme
import com.example.data.model.UiState
import com.example.data.pdf.PdfBookLoader
import com.example.data.repository.LibraryRepository
import com.example.data.repository.ReaderPreferencesRepository
import com.example.ui.theme.RahifaBooksTheme
import com.example.ui.viewmodel.AdminSessionState
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private object RahifaRoutes {
    const val LIBRARY = "library"
    const val CATEGORIES = "categories"
    const val BOOKMARKS = "bookmarks"
    const val ADMIN = "admin"
    const val READER = "reader/{bookId}"
    fun reader(bookId: String) = "reader/$bookId"
}

@Composable
fun RahifaApp() {
    val mainViewModel: MainViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                MainViewModel(
                    libraryRepository = LibraryRepository(db),
                    preferencesRepository = ReaderPreferencesRepository(app)
                )
            }
        }
    )

    val adminViewModel: AdminViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                AdminViewModel(
                    repository = LibraryRepository(db),
                    db = db,
                    auth = Firebase.auth
                )
            }
        }
    )

    val preferences by mainViewModel.readerPreferences.collectAsStateWithLifecycle()
    val filteredBooksState by mainViewModel.filteredBooksState.collectAsStateWithLifecycle()
    val publishedBooksState by mainViewModel.publishedBooksState.collectAsStateWithLifecycle()
    val categoriesState by mainViewModel.categoriesState.collectAsStateWithLifecycle()
    val announcements by mainViewModel.activeAnnouncements.collectAsStateWithLifecycle()
    val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategoryId by mainViewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val adminSessionState by adminViewModel.sessionState.collectAsStateWithLifecycle()

    val publishedBooks = (publishedBooksState as? UiState.Success)?.data.orEmpty()

    RahifaBooksTheme(readingTheme = preferences.theme) {
        val navController = rememberNavController()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        val isReaderRoute = currentRoute?.startsWith("reader/") == true

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !isReaderRoute,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.width(304.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .navigationBarsPadding()
                    ) {
                        // Editorial Hero Banner inside Drawer
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(164.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_library_hero),
                                contentDescription = "Rahifa Books reading room",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.15f),
                                                Color.Black.copy(alpha = 0.72f)
                                            )
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Digital Reading Room",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.nav_library)) },
                            icon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null) },
                            selected = currentRoute == RahifaRoutes.LIBRARY,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(RahifaRoutes.LIBRARY) {
                                    popUpTo(RahifaRoutes.LIBRARY) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier
                                .padding(NavigationDrawerItemDefaults.ItemPadding)
                                .testTag("drawer_library")
                        )

                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.nav_categories)) },
                            icon = { Icon(Icons.Rounded.Category, contentDescription = null) },
                            selected = currentRoute == RahifaRoutes.CATEGORIES,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(RahifaRoutes.CATEGORIES) {
                                    popUpTo(RahifaRoutes.LIBRARY)
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier
                                .padding(NavigationDrawerItemDefaults.ItemPadding)
                                .testTag("drawer_categories")
                        )

                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.nav_bookmarks)) },
                            icon = { Icon(Icons.Rounded.Bookmarks, contentDescription = null) },
                            badge = if (preferences.bookmarkedBookIds.isNotEmpty()) {
                                { Text(preferences.bookmarkedBookIds.size.toString()) }
                            } else null,
                            selected = currentRoute == RahifaRoutes.BOOKMARKS,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(RahifaRoutes.BOOKMARKS) {
                                    popUpTo(RahifaRoutes.LIBRARY)
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier
                                .padding(NavigationDrawerItemDefaults.ItemPadding)
                                .testTag("drawer_bookmarks")
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Reading Theme Switcher (Light / Sepia / Dark)
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.nav_reading_theme),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ReadingTheme.entries.forEach { theme ->
                                    FilterChip(
                                        selected = preferences.theme == theme,
                                        onClick = { mainViewModel.setReadingTheme(theme) },
                                        label = {
                                            Text(
                                                text = theme.label,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        modifier = Modifier.testTag("drawer_theme_${theme.name.lowercase()}")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Admin Panel Entry at Bottom
                        NavigationDrawerItem(
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.nav_admin_panel))
                                    if (adminSessionState is AdminSessionState.Authenticated) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(999.dp)
                                        ) {
                                            Text(
                                                text = "Active",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            icon = { Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null) },
                            selected = currentRoute == RahifaRoutes.ADMIN,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(RahifaRoutes.ADMIN) {
                                    popUpTo(RahifaRoutes.LIBRARY)
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier
                                .padding(NavigationDrawerItemDefaults.ItemPadding)
                                .padding(bottom = 12.dp)
                                .testTag("drawer_admin_panel")
                        )
                    }
                }
            }
        ) {
            NavHost(
                navController = navController,
                startDestination = RahifaRoutes.LIBRARY
            ) {
                composable(RahifaRoutes.LIBRARY) {
                    LibraryScreen(
                        booksState = filteredBooksState,
                        categoriesState = categoriesState,
                        announcements = announcements,
                        preferences = preferences,
                        searchQuery = searchQuery,
                        selectedCategoryId = selectedCategoryId,
                        onSearchQueryChange = mainViewModel::updateSearchQuery,
                        onSelectCategory = mainViewModel::selectCategory,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenReader = { book ->
                            navController.navigate(RahifaRoutes.reader(book.id))
                        },
                        onOpenSamplePreview = {
                            navController.navigate(RahifaRoutes.reader(PdfBookLoader.SAMPLE_BOOK_ID))
                        },
                        onToggleBookmark = mainViewModel::toggleBookmark,
                        lastReadPageFor = mainViewModel::lastReadPageFor
                    )
                }

                composable(RahifaRoutes.CATEGORIES) {
                    CategoriesScreen(
                        categoriesState = categoriesState,
                        publishedBooks = publishedBooks,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onCategoryClick = { category ->
                            mainViewModel.selectCategory(category.id)
                            navController.navigate(RahifaRoutes.LIBRARY) {
                                popUpTo(RahifaRoutes.LIBRARY) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(RahifaRoutes.BOOKMARKS) {
                    BookmarksScreen(
                        allPublishedBooks = publishedBooks,
                        samplePreviewBook = mainViewModel.samplePreviewBook(),
                        preferences = preferences,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onGoToLibrary = {
                            navController.navigate(RahifaRoutes.LIBRARY) {
                                popUpTo(RahifaRoutes.LIBRARY) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onOpenReader = { book ->
                            navController.navigate(RahifaRoutes.reader(book.id))
                        },
                        onToggleBookmark = mainViewModel::toggleBookmark,
                        lastReadPageFor = mainViewModel::lastReadPageFor
                    )
                }

                composable(
                    route = RahifaRoutes.READER,
                    arguments = listOf(navArgument("bookId") { type = NavType.StringType })
                ) { entry ->
                    val bookId = entry.arguments?.getString("bookId").orEmpty()
                    val book = if (bookId == PdfBookLoader.SAMPLE_BOOK_ID) {
                        mainViewModel.samplePreviewBook()
                    } else {
                        publishedBooks.firstOrNull { it.id == bookId }
                    }

                    if (book == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Book not found or unpublished.")
                        }
                    } else {
                        PdfReaderScreen(
                            book = book,
                            initialPage = mainViewModel.lastReadPageFor(book.id),
                            preferences = preferences,
                            isBookmarked = preferences.bookmarkedBookIds.contains(book.id),
                            onBack = { navController.popBackStack() },
                            onPageChanged = { page ->
                                mainViewModel.saveLastReadPage(book.id, page)
                            },
                            onThemeChange = mainViewModel::setReadingTheme,
                            onBrightnessChange = mainViewModel::setBrightness,
                            onToggleMute = mainViewModel::toggleSoundMuted,
                            onToggleBookmark = { mainViewModel.toggleBookmark(book.id) }
                        )
                    }
                }

                composable(RahifaRoutes.ADMIN) {
                    val goBackToLibrary: () -> Unit = {
                        navController.navigate(RahifaRoutes.LIBRARY) {
                            popUpTo(RahifaRoutes.LIBRARY) { inclusive = true }
                            launchSingleTop = true
                        }
                    }

                    when (val session = adminSessionState) {
                        is AdminSessionState.Authenticated -> {
                            key(session.user.uid) {
                                AdminDashboardScreen(
                                    adminEmail = session.user.email ?: session.user.uid,
                                    adminViewModel = adminViewModel,
                                    onBackToLibrary = goBackToLibrary
                                )
                            }
                        }

                        else -> {
                            AdminLoginScreen(
                                sessionState = session,
                                onBackToLibrary = goBackToLibrary
                            )
                        }
                    }
                }
            }
        }
    }
}
