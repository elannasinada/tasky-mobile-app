package io.tasky.taskyapp.core.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.tasky.taskyapp.core.service.GeminiService
import io.tasky.taskyapp.task.domain.model.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrioritizedTask(
    val task: Task,
    val priority: Int
)

@HiltViewModel
class TaskPrioritizationViewModel @Inject constructor(
    private val geminiService: GeminiService
) : ViewModel() {

    private val _prioritizedTasks = MutableStateFlow<List<PrioritizedTask>>(emptyList())
    val prioritizedTasks: StateFlow<List<PrioritizedTask>> = _prioritizedTasks

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun prioritizeTasks(tasks: List<Task>) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                val orderedTasks = geminiService.orderTasksByPriority(tasks)
                
                val prioritizedTasks = orderedTasks.map { task ->
                    PrioritizedTask(
                        task = task,
                        priority = task.priority
                    )
                }
                
                _prioritizedTasks.value = prioritizedTasks
            } catch (e: Exception) {
                _error.value = "Failed to prioritize tasks: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}