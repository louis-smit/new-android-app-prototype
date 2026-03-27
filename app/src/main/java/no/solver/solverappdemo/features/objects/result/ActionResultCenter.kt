package no.solver.solverappdemo.features.objects.result

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ActionResultCenter @Inject constructor() {
    private val lock = Any()
    private val queue = mutableListOf<ActionResultPresentation>()

    private val _current = MutableStateFlow<ActionResultPresentation?>(null)
    val current: StateFlow<ActionResultPresentation?> = _current.asStateFlow()

    val hasActiveResult: Boolean
        get() = _current.value != null

    fun publish(presentation: ActionResultPresentation) {
        synchronized(lock) {
            var normalized = presentation.ensuringCompletionTimestamp()
            val correlationKey = normalized.correlationKey
            val current = _current.value

            if (correlationKey != null) {
                if (current != null && current.correlationKey == correlationKey) {
                    normalized = normalized.copy(id = current.id)
                    _current.value = normalized
                    return
                }

                val queueIndex = queue.indexOfFirst { it.correlationKey == correlationKey }
                if (queueIndex >= 0) {
                    normalized = normalized.copy(id = queue[queueIndex].id)
                    queue[queueIndex] = normalized
                    return
                }
            }

            if (current == null) {
                _current.value = normalized
            } else {
                queue.add(normalized)
            }
        }
    }

    fun dismissCurrent() {
        synchronized(lock) {
            _current.value = if (queue.isNotEmpty()) {
                queue.removeAt(0)
            } else {
                null
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            queue.clear()
            _current.value = null
        }
    }
}
