package no.solver.solverappdemo.features.objects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldDefaults.DecorationBox
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import no.solver.solverappdemo.R
import no.solver.solverappdemo.data.models.SolverObject
import no.solver.solverappdemo.ui.components.OfflineBanner
import no.solver.solverappdemo.ui.theme.SolverAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectsScreen(
    viewModel: ObjectsViewModel = hiltViewModel(),
    onObjectClick: (SolverObject) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val filteredObjects by viewModel.filteredObjects.collectAsState()
    val filteredFavourites by viewModel.filteredFavourites.collectAsState()
    val apiBaseUrl by viewModel.apiBaseUrl.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isFavouritesLoading by viewModel.isFavouritesLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Objects") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isOffline) {
                OfflineBanner(lastSyncedAt = lastSyncedAt)
            }

            // Segmented tabs: All / Favourites
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            ) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {}
                ) {
                    Text("All")
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {}
                ) {
                    Text("Favourites")
                }
            }

            // Search field
            val interactionSource = remember { MutableInteractionSource() }
            val colors = TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent
            )
            BasicTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    DecorationBox(
                        value = searchQuery,
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = true,
                        visualTransformation = VisualTransformation.None,
                        interactionSource = interactionSource,
                        placeholder = {
                            Text(
                                "Search objects...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = colors,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    )
                }
            )

            PullToRefreshBox(
                isRefreshing = isRefreshing || (selectedTab == 1 && isFavouritesLoading),
                onRefresh = {
                    viewModel.refreshObjects()
                    viewModel.loadFavourites()
                },
                modifier = Modifier.fillMaxSize()
            ) {
                when (selectedTab) {
                    0 -> {
                        // All Objects tab
                        when (val state = uiState) {
                            is ObjectsUiState.Loading -> {
                                LoadingContent()
                            }
                            is ObjectsUiState.Success -> {
                                ObjectsList(
                                    objects = filteredObjects,
                                    baseUrl = apiBaseUrl,
                                    onObjectClick = onObjectClick,
                                    getCachedIcon = { viewModel.getCachedIcon(it) }
                                )
                            }
                            is ObjectsUiState.Empty -> {
                                EmptyContent(onRetry = { viewModel.retry() })
                            }
                            is ObjectsUiState.EmptyOffline -> {
                                OfflineEmptyContent(onRetry = { viewModel.retry() })
                            }
                            is ObjectsUiState.Error -> {
                                ErrorContent(
                                    message = state.message,
                                    onRetry = { viewModel.retry() }
                                )
                            }
                        }
                    }
                    1 -> {
                        // Favourites tab
                        if (isFavouritesLoading && filteredFavourites.isEmpty()) {
                            LoadingContent(message = "Loading favourites...")
                        } else if (filteredFavourites.isEmpty()) {
                            FavouritesEmptyContent()
                        } else {
                            ObjectsList(
                                objects = filteredFavourites,
                                baseUrl = apiBaseUrl,
                                onObjectClick = onObjectClick,
                                getCachedIcon = { viewModel.getCachedIcon(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObjectsList(
    objects: List<SolverObject>,
    baseUrl: String,
    onObjectClick: (SolverObject) -> Unit,
    modifier: Modifier = Modifier,
    getCachedIcon: (Int) -> android.graphics.Bitmap? = { null }
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = objects,
            key = { it.id }
        ) { obj ->
            ObjectRow(
                solverObject = obj,
                baseUrl = baseUrl,
                cachedIcon = getCachedIcon(obj.objectTypeId),
                onClick = { onObjectClick(obj) }
            )
        }
    }
}

@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier,
    message: String = "Loading objects..."
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyContent(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_cube),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No Objects Found",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "You don't have access to any objects yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun FavouritesEmptyContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No Favourites",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Add objects to your favourites from the object details screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OfflineEmptyContent(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No Cached Data",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Connect to the internet to load your objects.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingContentPreview() {
    SolverAppTheme {
        LoadingContent()
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyContentPreview() {
    SolverAppTheme {
        EmptyContent(onRetry = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun FavouritesEmptyContentPreview() {
    SolverAppTheme {
        FavouritesEmptyContent()
    }
}

@Preview(showBackground = true)
@Composable
private fun OfflineEmptyContentPreview() {
    SolverAppTheme {
        OfflineEmptyContent(onRetry = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorContentPreview() {
    SolverAppTheme {
        ErrorContent(
            message = "Network connection failed. Please check your internet connection.",
            onRetry = {}
        )
    }
}
