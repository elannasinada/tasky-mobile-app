package io.tasky.taskyapp.task.presentation.listing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.tasky.taskyapp.core.util.Resource
import io.tasky.taskyapp.core.util.filterBy
import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.model.RecurrencePattern
import io.tasky.taskyapp.task.domain.use_cases.TaskUseCases
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val useCases: TaskUseCases
) : ViewModel() {
    private val _state = MutableStateFlow(TaskState())
    val state: StateFlow<TaskState> = _state

    private val tasks = mutableListOf<Task>()
    private var searchJob: Job? = null
    private var getTasksJob: Job? = null

    private var recentlyDeletedTask: Task? = null
    var userData: UserData? = null

    fun onEvent(event: TaskEvent) {
        when (event) {
            is TaskEvent.SearchedForTask -> {
                searchJob?.cancel()
                searchJob = viewModelScope.launch {
                    event.filter.takeIf {
                        it.isNotEmpty()
                    }?.let {
                        delay(300L)
                    }
                    _state.update {
                        state.value.copy(
                            tasks = tasks.filterBy(event.filter),
                        )
                    }
                }
            }
            is TaskEvent.RequestDelete -> {
                with(event.task) {
                    deleteTask(this)
                    recentlyDeletedTask = this
                }
            }
            is TaskEvent.RestoreTask -> {
                recentlyDeletedTask?.let {
                    restoreTask(it)
                }
            }
            is TaskEvent.CompleteTask -> {
                completeTask(event.task)
            }
            is TaskEvent.UpdateTaskStatus -> {
                updateTaskStatus(event.task, event.newStatus)
            }
        }
    }

    fun getTasks(userData: UserData) {
        getTasksJob?.cancel()
        getTasksJob = useCases.getTasksUseCase(userData).onEach { result ->
            when (result) {
                is Resource.Success -> {
                    tasks.clear()

                    result.data?.let { tasks ->
                        this.tasks.addAll(tasks)

                        _state.update {
                            it.copy(
                                tasks = tasks,
                            )
                        }
                    }
                }
                is Resource.Loading -> {
                    _state.update {
                        it.copy(
                            loading = result.isLoading,
                        )
                    }
                }
                is Resource.Error -> {
                    _state.update {
                        it.copy(
                            error = result.message ?: "Unknown error"
                        )
                    }
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun restoreTask(task: Task) {
        viewModelScope.launch {
            with(task) {
                userData?.let {
                    useCases.insertTaskUseCase(
                        userData = it,
                        title = title,
                        description = description ?: "",
                        taskType = taskType,
                        deadlineDate = deadlineDate ?: "",
                        deadlineTime = deadlineTime ?: "",
                        status = TaskStatus.valueOf(status)
                    )
                    getTasks(it)
                }
            }
        }
    }

    private fun deleteTask(task: Task) {
        viewModelScope.launch {
            userData?.let {
                useCases.deleteTaskUseCase(it, task)
                getTasks(it)
            }
        }
    }

    private fun updateTaskStatus(task: Task, newStatus: TaskStatus) {
        viewModelScope.launch {
            userData?.let { userData ->
                // Update task status
                val updatedTask = task.copy(status = newStatus.name)

                useCases.updateTaskUseCase(
                    userData = userData,
                    task = task,
                    title = task.title,
                    description = task.description ?: "",
                    taskType = task.taskType,
                    deadlineDate = task.deadlineDate ?: "",
                    deadlineTime = task.deadlineTime ?: "",
                    status = newStatus.name
                )

                // If task is completed and it's recurring, create next occurrence
                if (newStatus == TaskStatus.COMPLETED && task.isRecurring) {
                    createNextOccurrence(task, userData)
                }

                getTasks(userData)
            }
        }
    }

    private fun completeTask(task: Task) {
        updateTaskStatus(task, TaskStatus.COMPLETED)
    }

    private fun createNextOccurrence(task: Task, userData: UserData) {
        // Calculate the next deadline based on recurrence pattern
        val nextDeadline = calculateNextDeadline(
            task.deadlineDate,
            task.recurrencePattern,
            task.recurrenceInterval
        )

        // Don't create next occurrence if we've passed the end date
        task.recurrenceEndDate?.let { endDate ->
            if (nextDeadline != null && isDateAfter(nextDeadline, endDate)) {
                return
            }
        }

        // Create a new task with the next deadline
        val nextTask = task.copy(
            uuid = UUID.randomUUID().toString(),
            status = TaskStatus.PENDING.name,
            deadlineDate = nextDeadline
        )

        // Insert the new task
        viewModelScope.launch {
            useCases.insertTaskUseCase(
                userData = userData,
                title = nextTask.title,
                description = nextTask.description ?: "",
                taskType = nextTask.taskType,
                deadlineDate = nextTask.deadlineDate ?: "",
                deadlineTime = nextTask.deadlineTime ?: "",
                status = TaskStatus.PENDING,
                isRecurring = nextTask.isRecurring,
                recurrencePattern = nextTask.recurrencePattern,
                recurrenceInterval = nextTask.recurrenceInterval,
                recurrenceEndDate = nextTask.recurrenceEndDate
            )
        }
    }

    private fun calculateNextDeadline(currentDeadline: String?, pattern: String?, interval: Int): String? {
        if (currentDeadline == null || pattern == null) return null

        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(currentDeadline) ?: return null
            val calendar = Calendar.getInstance()
            calendar.time = date

            when (pattern) {
                RecurrencePattern.DAILY.name -> calendar.add(Calendar.DAY_OF_YEAR, interval)
                RecurrencePattern.WEEKLY.name -> calendar.add(Calendar.WEEK_OF_YEAR, interval)
                RecurrencePattern.MONTHLY.name -> calendar.add(Calendar.MONTH, interval)
                RecurrencePattern.YEARLY.name -> calendar.add(Calendar.YEAR, interval)
            }

            return sdf.format(calendar.time)
        } catch (e: Exception) {
            return null
        }
    }

    private fun isDateAfter(date1: String, date2: String): Boolean {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val parsedDate1 = sdf.parse(date1) ?: return false
            val parsedDate2 = sdf.parse(date2) ?: return false
            return parsedDate1.after(parsedDate2)
        } catch (e: Exception) {
            return false
        }
    }

    fun clearState() {
        _state.update { TaskState() }
    }
}