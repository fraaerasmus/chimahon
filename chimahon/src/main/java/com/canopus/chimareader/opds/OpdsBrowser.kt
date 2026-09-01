package com.canopus.chimareader.opds

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Browses saved OPDS catalogs and downloads books into the novel library.
 *
 * [onImportFile] receives the downloaded temp file and the name the server gave it, and owns
 * deleting the file when it is done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsBrowser(
    repository: OpdsCatalogRepository,
    onClose: () -> Unit,
    onImportFile: (file: File, displayName: String) -> Unit,
    importBusy: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val client = remember { OpdsClient() }
    val scope = rememberCoroutineScope()
    val catalogs by repository.catalogs.collectAsState()
    var selected by remember { mutableStateOf<OpdsCatalog?>(null) }
    val feedStack = remember { mutableStateListOf<OpdsFeed>() }
    var searchTemplate by remember { mutableStateOf<String?>(null) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val downloads = remember { mutableStateMapOf<String, Float>() }
    var editing by remember { mutableStateOf<OpdsCatalog?>(null) }
    var deleteCandidate by remember { mutableStateOf<OpdsCatalog?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }

    fun describe(throwable: Throwable): String = throwable.message ?: throwable::class.java.simpleName

    fun loadFeed(catalog: OpdsCatalog, url: String) {
        loadJob?.cancel()
        loading = true
        error = null
        loadJob = scope.launch {
            runCatching { client.fetchFeed(catalog, url) }
                .onSuccess { feed ->
                    feedStack.add(feed)
                    if (feedStack.size == 1) searchTemplate = client.searchTemplate(catalog, feed)
                }
                .onFailure { error = "Could not load catalog: ${describe(it)}" }
            loading = false
        }
    }

    fun openCatalog(catalog: OpdsCatalog) {
        selected = catalog
        feedStack.clear()
        searchTemplate = null
        searchVisible = false
        searchQuery = ""
        loadFeed(catalog, catalog.url)
    }

    fun back() {
        when {
            loading && feedStack.isNotEmpty() -> {
                loadJob?.cancel()
                loading = false
            }
            feedStack.size > 1 -> feedStack.removeAt(feedStack.lastIndex)
            selected != null -> {
                loadJob?.cancel()
                selected = null
                feedStack.clear()
            }
            else -> onClose()
        }
    }

    fun loadMore(catalog: OpdsCatalog, feed: OpdsFeed) {
        val next = feed.nextHref ?: return
        loadingMore = true
        scope.launch {
            runCatching { client.fetchFeed(catalog, next) }
                .onSuccess { page ->
                    val index = feedStack.indexOf(feed)
                    if (index >= 0) {
                        feedStack[index] = feed.copy(entries = feed.entries + page.entries, nextHref = page.nextHref)
                    }
                }
                .onFailure { error = "Could not load more: ${describe(it)}" }
            loadingMore = false
        }
    }

    fun download(catalog: OpdsCatalog, entry: OpdsEntry) {
        val href = entry.epubHref ?: return
        downloads[entry.id] = 0f
        scope.launch {
            runCatching {
                client.download(catalog, href, context.cacheDir, fallbackName = "${entry.title}.epub") { done, total ->
                    downloads[entry.id] = if (total != null) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
                }
            }.onSuccess { result ->
                onImportFile(result.file, result.fileName)
                message = "Downloaded ${entry.title}"
                error = null
            }.onFailure { error = "Download failed: ${describe(it)}" }
            downloads.remove(entry.id)
        }
    }

    editing?.let { catalog ->
        CatalogDialog(
            initial = catalog,
            onDismiss = { editing = null },
            onSave = { next ->
                editing = null
                scope.launch { repository.save(next) }
            },
        )
    }
    deleteCandidate?.let { catalog ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            text = { Text("Remove ${catalog.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCandidate = null
                        scope.launch { repository.delete(catalog.id) }
                    },
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            },
        )
    }

    val currentCatalog = selected
    val currentFeed = feedStack.lastOrNull()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentFeed?.title?.takeIf { it.isNotBlank() }
                            ?: currentCatalog?.name
                            ?: "OPDS catalogs",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { back() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentCatalog == null) {
                        IconButton(onClick = { editing = OpdsCatalog(id = "", name = "", url = "") }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add catalog")
                        }
                    } else if (searchTemplate != null) {
                        IconButton(onClick = { searchVisible = !searchVisible }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (currentCatalog == null) {
                CatalogList(
                    catalogs = catalogs,
                    onOpen = ::openCatalog,
                    onEdit = { editing = it },
                    onDelete = { deleteCandidate = it },
                )
                return@Column
            }
            if (searchVisible && searchTemplate != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search this catalog") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val template = searchTemplate ?: return@TextButton
                            if (searchQuery.isNotBlank()) {
                                loadFeed(currentCatalog, OpdsFeedParser.searchUrl(template, searchQuery.trim()))
                            }
                        },
                        enabled = searchQuery.isNotBlank() && !loading,
                    ) { Text("Search") }
                }
            }
            (error ?: message)?.let { text ->
                Text(
                    text = text,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                currentFeed == null -> Unit
                else -> FeedList(
                    feed = currentFeed,
                    downloads = downloads,
                    importBusy = importBusy,
                    loadingMore = loadingMore,
                    onOpen = { href -> loadFeed(currentCatalog, href) },
                    onDownload = { entry -> download(currentCatalog, entry) },
                    onLoadMore = { loadMore(currentCatalog, currentFeed) },
                )
            }
        }
    }
}

@Composable
private fun CatalogList(
    catalogs: List<OpdsCatalog>,
    onOpen: (OpdsCatalog) -> Unit,
    onEdit: (OpdsCatalog) -> Unit,
    onDelete: (OpdsCatalog) -> Unit,
) {
    if (catalogs.isEmpty()) {
        Text(
            text = "No catalogs yet. Add one with the + button, for example a calibre content server at http://host:8083/opds.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
        return
    }
    LazyColumn {
        items(catalogs) { catalog ->
            ListItem(
                headlineContent = { Text(catalog.name.ifBlank { catalog.url }) },
                supportingContent = { Text(catalog.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = {
                    Row {
                        IconButton(onClick = { onEdit(catalog) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { onDelete(catalog) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove")
                        }
                    }
                },
                modifier = Modifier.clickable { onOpen(catalog) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun FeedList(
    feed: OpdsFeed,
    downloads: Map<String, Float>,
    importBusy: Boolean,
    loadingMore: Boolean,
    onOpen: (String) -> Unit,
    onDownload: (OpdsEntry) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (feed.entries.isEmpty()) {
        Text(
            text = "This feed is empty.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(feed.entries) { entry ->
            val navigationHref = entry.navigationHref
            val progress = downloads[entry.id]
            ListItem(
                headlineContent = { Text(entry.title) },
                supportingContent = {
                    Column {
                        if (entry.authors.isNotEmpty()) {
                            Text(entry.authors.joinToString(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        entry.summary?.let {
                            Text(
                                text = it,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (progress != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            if (progress < 0f) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else {
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                trailingContent = {
                    when {
                        navigationHref != null ->
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                        entry.epubHref != null -> IconButton(
                            onClick = { onDownload(entry) },
                            enabled = progress == null && !importBusy,
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = "Download")
                        }
                        entry.hasOtherFormatsOnly -> Text(
                            text = "No EPUB",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                modifier = if (navigationHref != null) Modifier.clickable { onOpen(navigationHref) } else Modifier,
            )
            HorizontalDivider()
        }
        if (feed.nextHref != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loadingMore) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        TextButton(onClick = onLoadMore) { Text("Load more") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogDialog(
    initial: OpdsCatalog,
    onDismiss: () -> Unit,
    onSave: (OpdsCatalog) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Add catalog" else "Edit catalog") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Catalog URL") },
                    placeholder = { Text("http://host:8083/opds") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedUrl = url.trim().let { if (it.contains("://")) it else "http://$it" }
                    onSave(
                        initial.copy(
                            name = name.trim().ifBlank { trimmedUrl },
                            url = trimmedUrl,
                            username = username,
                            password = password,
                        ),
                    )
                },
                enabled = url.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
