package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Book
import com.example.data.model.Category
import com.example.data.pdf.PdfBookLoader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminBookEditorSheet(
    existingBook: Book?,
    categories: List<Category>,
    isBusy: Boolean,
    uploadProgress: Float?,
    onDismiss: () -> Unit,
    onSave: (
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
        featured: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember(existingBook) { mutableStateOf(existingBook?.title ?: "") }
    var author by remember(existingBook) { mutableStateOf(existingBook?.author ?: "") }
    var description by remember(existingBook) { mutableStateOf(existingBook?.description ?: "") }
    var selectedCategory by remember(existingBook, categories) {
        mutableStateOf(
            categories.firstOrNull { it.id == existingBook?.categoryId } ?: categories.firstOrNull()
        )
    }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    var coverUri by remember { mutableStateOf<Uri?>(null) }
    var coverUrlInput by remember(existingBook) { mutableStateOf(existingBook?.coverUrl ?: "") }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfUrlInput by remember(existingBook) { mutableStateOf(existingBook?.pdfUrl ?: "") }
    var pageCount by remember(existingBook) { mutableLongStateOf(existingBook?.pageCount ?: 0L) }

    var published by remember(existingBook) { mutableStateOf(existingBook?.published ?: true) }
    var featured by remember(existingBook) { mutableStateOf(existingBook?.featured ?: false) }
    var showAdvancedUrls by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Zero-permission Photo Picker per Play Policy
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) coverUri = uri
    }

    // SAF OpenDocument for PDF selection (zero broad storage permissions)
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pdfUri = uri
            scope.launch {
                val pages = PdfBookLoader.countPdfPages(context, uri)
                if (pages > 0) pageCount = pages.toLong()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isBusy) onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (existingBook == null) "Add New Book" else "Edit Book",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it; validationError = null },
                label = { Text("Book Title *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_book_title_input")
            )

            OutlinedTextField(
                value = author,
                onValueChange = { author = it; validationError = null },
                label = { Text("Author *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_book_author_input")
            )

            // Category Selector
            ExposedDropdownMenuBox(
                expanded = categoryMenuExpanded,
                onExpandedChange = { categoryMenuExpanded = !categoryMenuExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "Create a category first",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category *") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                selectedCategory = cat
                                categoryMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description / Synopsis") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            // Device Pickers (PDF & Cover Image)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            pdfUri != null -> "PDF Selected ($pageCount pp)"
                            pdfUrlInput.isNotBlank() -> "Replace PDF"
                            else -> "Pick PDF *"
                        }
                    )
                }

                OutlinedButton(
                    onClick = {
                        coverPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            coverUri != null -> "Cover Selected"
                            coverUrlInput.isNotBlank() -> "Replace Cover"
                            else -> "Pick Cover"
                        }
                    )
                }
            }

            TextButton(onClick = { showAdvancedUrls = !showAdvancedUrls }) {
                Text(if (showAdvancedUrls) "Hide direct URL fields" else "Or enter PDF / Cover URLs directly")
            }

            if (showAdvancedUrls) {
                OutlinedTextField(
                    value = pdfUrlInput,
                    onValueChange = { pdfUrlInput = it; validationError = null },
                    label = { Text("Direct PDF HTTPS URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = coverUrlInput,
                    onValueChange = { coverUrlInput = it },
                    label = { Text("Direct Cover Image HTTPS URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Published", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Visible to all readers in Library",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = published, onCheckedChange = { published = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Featured Edition", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Highlight in the Featured shelf",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = featured, onCheckedChange = { featured = it })
            }

            if (validationError != null) {
                Text(
                    text = validationError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (uploadProgress != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Uploading & syncing to Firebase… ${(uploadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { uploadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val cat = selectedCategory
                        when {
                            title.isBlank() -> validationError = "Please enter a book title."
                            author.isBlank() -> validationError = "Please enter the author name."
                            cat == null -> validationError = "Please create a category first in the Categories tab."
                            pdfUri == null && pdfUrlInput.isBlank() ->
                                validationError = "Please pick a PDF file or enter a PDF URL."
                            else -> onSave(
                                title,
                                author,
                                cat,
                                description,
                                coverUri,
                                coverUrlInput,
                                pdfUri,
                                pdfUrlInput,
                                pageCount,
                                published,
                                featured
                            )
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_save_book_button")
                ) {
                    Text(if (existingBook == null) "Upload & Save" else "Save Changes")
                }
            }
        }
    }
}
