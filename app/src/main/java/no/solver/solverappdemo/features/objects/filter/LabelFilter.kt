package no.solver.solverappdemo.features.objects.filter

import no.solver.solverappdemo.data.models.SolverObject

data class LabelFilter(
    val id: String,
    val options: List<LabelOption>
) {
    val selectedCount: Int
        get() = options.count { it.isChecked }

    val hasSelection: Boolean
        get() = selectedCount > 0
}

data class LabelOption(
    val value: String,
    val isChecked: Boolean
)

fun buildLabelFilters(
    objects: List<SolverObject>,
    existingFilters: List<LabelFilter> = emptyList()
): List<LabelFilter> {
    val labelMap = mutableMapOf<String, MutableSet<String>>()

    objects.forEach { obj ->
        obj.labels?.forEach { label ->
            val key = label.key ?: return@forEach
            val value = label.value ?: return@forEach
            labelMap.getOrPut(key) { mutableSetOf() }.add(value)
        }
    }

    val existingSelections = existingFilters.associate { filter ->
        filter.id to filter.options.filter { it.isChecked }.map { it.value }.toSet()
    }

    return labelMap.map { (key, values) ->
        val selectedValues = existingSelections[key] ?: emptySet()
        LabelFilter(
            id = key,
            options = values.sorted().map { value ->
                LabelOption(
                    value = value,
                    isChecked = value in selectedValues
                )
            }
        )
    }.sortedBy { it.id.lowercase() }
}

fun filterObjectsByLabels(
    objects: List<SolverObject>,
    filters: List<LabelFilter>
): List<SolverObject> {
    val activeFilters = filters.filter { it.hasSelection }
    
    if (activeFilters.isEmpty()) {
        return objects
    }

    return objects.filter { obj ->
        activeFilters.all { filter ->
            val selectedOptions = filter.options
                .filter { it.isChecked }
                .map { it.value }
            
            selectedOptions.any { selectedValue ->
                obj.labels?.any { label ->
                    label.key == filter.id && label.value == selectedValue
                } == true
            }
        }
    }
}

fun countActiveFilters(filters: List<LabelFilter>): Int {
    return filters.sumOf { it.selectedCount }
}
