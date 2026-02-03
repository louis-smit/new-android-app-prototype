package no.solver.solverappdemo.features.objects.filter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelFiltersSheet(
    filters: List<LabelFilter>,
    onFilterChange: (filterId: String, optionValue: String, isChecked: Boolean) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val activeFilterCount = countActiveFilters(filters)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter by Label",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (activeFilterCount > 0) {
                    TextButton(onClick = onClearFilters) {
                        Text("Clear")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search filters...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (activeFilterCount > 0) {
                ActiveFiltersChips(
                    filters = filters,
                    onRemoveFilter = { filterId, optionValue ->
                        onFilterChange(filterId, optionValue, false)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            val filteredFilters = remember(filters, searchQuery) {
                if (searchQuery.isBlank()) {
                    filters
                } else {
                    val query = searchQuery.lowercase()
                    filters.mapNotNull { filter ->
                        val matchingOptions = filter.options.filter {
                            it.value.lowercase().contains(query)
                        }
                        if (matchingOptions.isNotEmpty() || filter.id.lowercase().contains(query)) {
                            if (matchingOptions.isNotEmpty()) {
                                filter.copy(options = matchingOptions)
                            } else {
                                filter
                            }
                        } else {
                            null
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(filteredFilters) { filter ->
                    ExpandableFilterSection(
                        filter = filter,
                        onOptionChange = { optionValue, isChecked ->
                            onFilterChange(filter.id, optionValue, isChecked)
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun BottomSheetDragHandle() {
    Surface(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Spacer(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
        )
    }
}

@Composable
private fun ActiveFiltersChips(
    filters: List<LabelFilter>,
    onRemoveFilter: (filterId: String, optionValue: String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { filter ->
            filter.options.filter { it.isChecked }.forEach { option ->
                FilterChip(
                    selected = true,
                    onClick = { onRemoveFilter(filter.id, option.value) },
                    label = { Text("${filter.id}: ${option.value}") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove filter"
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ExpandableFilterSection(
    filter: LabelFilter,
    onOptionChange: (optionValue: String, isChecked: Boolean) -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(filter.hasSelection) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = filter.id,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (filter.hasSelection) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${filter.selectedCount})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(start = 8.dp)) {
                filter.options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clickable { onOptionChange(option.value, !option.isChecked) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = option.isChecked,
                            onCheckedChange = { onOptionChange(option.value, it) }
                        )
                        Text(
                            text = option.value,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        HorizontalDivider()
    }
}
