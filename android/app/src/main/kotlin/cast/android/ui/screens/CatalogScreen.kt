package cast.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import cast.android.ui.UiState
import cast.android.ui.components.AddPodcastSheet
import cast.android.ui.components.CatalogScreenSkeleton
import cast.android.ui.nav.PodcastDetail
import cast.android.ui.viewmodel.CatalogViewModel
import cast.api.PodcastSummaryDto
import coil3.compose.AsyncImage

@Composable
fun CatalogScreen(navController: NavHostController) {
    val vm: CatalogViewModel = hiltViewModel()
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
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
