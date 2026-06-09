# Catalog View Toggle & Sort Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a list/grid view toggle and sort-by control to the catalog screen, with podcasts split into Listening / Not listening sections.

**Architecture:** Two new enums (`ViewMode`, `SortBy`) and computed list properties live in `CatalogViewModel`. `CatalogScreen` gains a `TopAppBar` with sort dropdown and view toggle. The success branch renders either a sectioned `LazyVerticalGrid` (grid mode) or a `LazyColumn` with sticky headers (list mode).

**Tech Stack:** Jetpack Compose, Material3, `mutableStateOf`, `collectAsStateWithLifecycle`

---

### Task 1: Add ViewMode/SortBy enums and state to CatalogViewModel

**Files:**
- Modify: `android/app/src/main/kotlin/cast/android/ui/viewmodel/CatalogViewModel.kt`

- [ ] **Step 1: Add enums, state fields, and computed properties**

Replace the entire file contents with:

```kotlin
package cast.android.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cast.android.domain.repository.PodcastRepository
import cast.android.ui.UiState
import cast.api.PodcastSummaryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

enum class ViewMode { Grid, List }
enum class SortBy { Name, Updated }

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val podcastRepository: PodcastRepository,
    @ApplicationContext private val context: Context,
) : LoadableViewModel<List<PodcastSummaryDto>>(
    podcastRepository.cachedPodcasts()?.let { UiState.Success(it) } ?: UiState.Loading
) {

    var showAddSheet by mutableStateOf(false)
        private set
    var isAdding by mutableStateOf(false)
        private set
    var addError: String? by mutableStateOf(null)
        private set
    var viewMode: ViewMode by mutableStateOf(ViewMode.Grid)
        private set
    var sortBy: SortBy by mutableStateOf(SortBy.Name)
        private set

    init { load() }

    fun load() = load("Failed to load podcasts") { podcastRepository.listPodcasts() }

    fun openAddSheet() { showAddSheet = true; addError = null }
    fun dismissAddSheet() { showAddSheet = false; addError = null }

    fun toggleViewMode() {
        viewMode = if (viewMode == ViewMode.Grid) ViewMode.List else ViewMode.Grid
    }

    fun setSortBy(sort: SortBy) { sortBy = sort }

    val listeningPodcasts: List<PodcastSummaryDto>
        get() = (uiState.value as? UiState.Success)?.data
            .orEmpty()
            .filter { it.listening }
            .applySortBy()

    val notListeningPodcasts: List<PodcastSummaryDto>
        get() = (uiState.value as? UiState.Success)?.data
            .orEmpty()
            .filter { !it.listening }
            .applySortBy()

    private fun List<PodcastSummaryDto>.applySortBy() = when (sortBy) {
        SortBy.Name -> sortedBy { it.name.lowercase() }
        SortBy.Updated -> sortedByDescending { it.updated }
    }

    fun addPodcast(feedUrl: String) {
        viewModelScope.launch {
            isAdding = true
            addError = null
            try {
                podcastRepository.addPodcast(feedUrl)
                showAddSheet = false
                load()
            } catch (e: Exception) {
                addError = e.message ?: "Failed to add podcast"
            } finally {
                isAdding = false
            }
        }
    }

    fun toggleListening(podcastId: String, listening: Boolean) {
        viewModelScope.launch {
            try {
                podcastRepository.setListening(podcastId, listening)
                load()
            } catch (_: Exception) {}
        }
    }

    fun importOpml(uri: Uri) {
        viewModelScope.launch {
            isAdding = true
            addError = null
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.readBytes()
                        ?: throw Exception("Could not read file")
                }
                val requestBody = bytes.toRequestBody("application/octet-stream".toMediaType())
                val part = MultipartBody.Part.createFormData("opml", "import.opml", requestBody)
                podcastRepository.importOpml(part)
                showAddSheet = false
                load()
            } catch (e: Exception) {
                addError = e.message ?: "Failed to import OPML"
            } finally {
                isAdding = false
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd android && ./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/cast/android/ui/viewmodel/CatalogViewModel.kt
git commit -m "feat(android): add ViewMode/SortBy state to CatalogViewModel"
```

---

### Task 2: Add TopAppBar with sort dropdown and view toggle

**Files:**
- Modify: `android/app/src/main/kotlin/cast/android/ui/screens/CatalogScreen.kt`

- [ ] **Step 1: Replace CatalogScreen.kt with the version that includes the TopAppBar**

The TopAppBar has two actions: a sort icon that opens a dropdown, and a view toggle icon. The FAB remains unchanged.

```kotlin
package cast.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import cast.android.ui.UiState
import cast.android.ui.components.AddPodcastSheet
import cast.android.ui.components.CatalogScreenSkeleton
import cast.android.ui.nav.PodcastDetail
import cast.android.ui.viewmodel.CatalogViewModel
import cast.android.ui.viewmodel.SortBy
import cast.android.ui.viewmodel.ViewMode
import cast.api.PodcastSummaryDto
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(navController: NavHostController) {
    val vm: CatalogViewModel = hiltViewModel()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Name") },
                                onClick = { vm.setSortBy(SortBy.Name); sortMenuExpanded = false },
                                trailingIcon = if (vm.sortBy == SortBy.Name) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                            )
                            DropdownMenuItem(
                                text = { Text("Recently updated") },
                                onClick = { vm.setSortBy(SortBy.Updated); sortMenuExpanded = false },
                                trailingIcon = if (vm.sortBy == SortBy.Updated) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                            )
                        }
                    }
                    IconButton(onClick = { vm.toggleViewMode() }) {
                        Icon(
                            imageVector = if (vm.viewMode == ViewMode.Grid) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle view",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.openAddSheet() }) {
                Icon(Icons.Default.Add, contentDescription = "Add podcast")
            }
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is UiState.Loading -> CatalogScreenSkeleton()
            is UiState.Error -> Box(Modifier.fillMaxSize()) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )
            }
            is UiState.Success -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.data, key = { it.id }) { podcast ->
                    PodcastCard(
                        podcast = podcast,
                        onClick = { navController.navigate(PodcastDetail(podcast.id)) },
                        onToggleListening = { vm.toggleListening(podcast.id, !podcast.listening) },
                    )
                }
            }
        }
    }

    if (vm.showAddSheet) {
        AddPodcastSheet(
            onDismiss = { vm.dismissAddSheet() },
            onSubmit = { vm.addPodcast(it) },
            onImportOpml = { vm.importOpml(it) },
            isLoading = vm.isAdding,
            error = vm.addError,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun PodcastCard(podcast: PodcastSummaryDto, onClick: () -> Unit, onToggleListening: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = podcast.image,
            contentDescription = podcast.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
        )
        if (!podcast.listening) {
            Text(
                text = "Not listening",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onToggleListening)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PodcastListItem(podcast: PodcastSummaryDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = podcast.image,
            contentDescription = podcast.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column {
            Text(
                text = podcast.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = podcast.updated.take(10),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd android && ./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/cast/android/ui/screens/CatalogScreen.kt
git commit -m "feat(android): add TopAppBar with sort dropdown and view toggle to catalog"
```

---

### Task 3: Wire up sections and list view in the success branch

**Files:**
- Modify: `android/app/src/main/kotlin/cast/android/ui/screens/CatalogScreen.kt`

- [ ] **Step 1: Replace the `is UiState.Success` branch**

Find this block inside the `Scaffold` content lambda:

```kotlin
            is UiState.Success -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.data, key = { it.id }) { podcast ->
                    PodcastCard(
                        podcast = podcast,
                        onClick = { navController.navigate(PodcastDetail(podcast.id)) },
                        onToggleListening = { vm.toggleListening(podcast.id, !podcast.listening) },
                    )
                }
            }
```

Replace it with:

```kotlin
            is UiState.Success -> if (vm.viewMode == ViewMode.Grid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = innerPadding,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Listening") }
                    items(vm.listeningPodcasts, key = { it.id }) { podcast ->
                        PodcastCard(
                            podcast = podcast,
                            onClick = { navController.navigate(PodcastDetail(podcast.id)) },
                            onToggleListening = { vm.toggleListening(podcast.id, !podcast.listening) },
                        )
                    }
                    if (vm.notListeningPodcasts.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Not listening") }
                        items(vm.notListeningPodcasts, key = { it.id }) { podcast ->
                            PodcastCard(
                                podcast = podcast,
                                onClick = { navController.navigate(PodcastDetail(podcast.id)) },
                                onToggleListening = { vm.toggleListening(podcast.id, !podcast.listening) },
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = innerPadding,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    stickyHeader { SectionHeader("Listening") }
                    items(vm.listeningPodcasts, key = { it.id }) { podcast ->
                        PodcastListItem(
                            podcast = podcast,
                            onClick = { navController.navigate(PodcastDetail(podcast.id)) },
                        )
                        HorizontalDivider()
                    }
                    if (vm.notListeningPodcasts.isNotEmpty()) {
                        stickyHeader { SectionHeader("Not listening") }
                        items(vm.notListeningPodcasts, key = { it.id }) { podcast ->
                            PodcastListItem(
                                podcast = podcast,
                                onClick = { navController.navigate(PodcastDetail(podcast.id)) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
```

- [ ] **Step 2: Verify it compiles**

```bash
cd android && ./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/cast/android/ui/screens/CatalogScreen.kt
git commit -m "feat(android): catalog sections by listening status, grid + list view"
```
