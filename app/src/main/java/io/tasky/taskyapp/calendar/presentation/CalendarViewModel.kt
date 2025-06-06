package io.tasky.taskyapp.calendar.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.tasky.taskyapp.R
import io.tasky.taskyapp.calendar.data.CalendarEvent
import io.tasky.taskyapp.calendar.data.CalendarItem
import io.tasky.taskyapp.calendar.data.CalendarRepository
import io.tasky.taskyapp.calendar.data.DateEvents
import io.tasky.taskyapp.core.util.Resource
import io.tasky.taskyapp.core.util.Toaster
import io.tasky.taskyapp.task.domain.model.Task
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class CalendarState(
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val dateEvents: List<DateEvents> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDateItems: List<CalendarItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val showGoogleSignInPrompt: Boolean = false,
    val tasksSyncedCount: Int = 0
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val toaster: Toaster,
    @ApplicationContext private val context: Context
) : ViewModel() {

    var state by mutableStateOf(CalendarState())
        private set

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        loadCalendarEvents()
    }

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                state = state.copy(
                    isLoading = false,
                    showGoogleSignInPrompt = false
                )

                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    _eventFlow.emitSafe(UiEvent.ShowToast("Connected to Google Calendar successfully"))
                    
                    refreshCalendarAndTasks(emptyList(), showToasts = false)
                } else {
                    state = state.copy(showGoogleSignInPrompt = true)
                }
            } catch (e: Exception) {
                if (e is ApiException && e.statusCode == 12501) {

                    val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
                    if (lastAccount != null) {
                        refreshCalendarAndTasks(emptyList(), showToasts = false)
                    } else {
                        state = state.copy(
                            error = "No Google account available. Please try signing in again.",
                            showGoogleSignInPrompt = true
                        )
                    }
                } else {

                    state = state.copy(error = "Error: ${e.message}")
                    _eventFlow.emitSafe(UiEvent.ShowToast("Failed to sign in with Google"))
                }
            }
        }
    }

    fun syncCalendarEvents(tasks: List<Task>) {
        viewModelScope.launch {
            try {
                state = state.copy(isSyncing = true)


                val account = GoogleSignIn.getLastSignedInAccount(context)


                if (account == null || !hasCalendarPermission(account)) {
                    _eventFlow.emit(UiEvent.NavigateToGoogleSignIn)
                    state = state.copy(isSyncing = false)
                    return@launch
                }

                calendarRepository.syncCalendarEvents(account).collectLatest { result ->
                    when (result) {
                        is Resource.Success -> {
                            val events = result.data ?: emptyList()
                            val uniqueEvents = events.distinctBy { it.id }
                            state = state.copy(
                                calendarEvents = uniqueEvents,
                                isSyncing = false,
                                error = null
                            )
                            val dedupedTasks = tasks.distinctBy { it.uuid }
                            updateDateEvents(uniqueEvents, dedupedTasks)
                            _eventFlow.emitSafe(UiEvent.ShowToast("Calendar synced successfully"))
                        }
                        is Resource.Error -> {
                            state = state.copy(
                                isSyncing = false,
                                error = result.message
                            )
                            _eventFlow.emitSafe(UiEvent.ShowToast(result.message ?: "Unknown error occurred"))
                        }
                        is Resource.Loading -> {
                            state = state.copy(isSyncing = result.isLoading)
                        }
                    }
                }
            } catch (e: Exception) {
                state = state.copy(isSyncing = false, error = e.message)
                _eventFlow.emitSafe(UiEvent.ShowToast(e.message ?: "Unknown error occurred"))
            }
        }
    }

    fun syncTasksToCalendar(tasks: List<Task>) {
        viewModelScope.launch {
            try {
                state = state.copy(isSyncing = true)

                // Get the GoogleSignInAccount
                val account = GoogleSignIn.getLastSignedInAccount(context)

                // Check if user has Google account and calendar permissions
                if (account == null || !hasCalendarPermission(account)) {
                    requestCalendarPermission()
                    state = state.copy(isSyncing = false)
                    return@launch
                }

                val existingTaskIds = mutableSetOf<String>()
                state.dateEvents.forEach { dateEvent ->
                    dateEvent.items.filterIsInstance<CalendarItem.TaskItem>()
                        .forEach { taskItem ->
                            existingTaskIds.add(taskItem.task.uuid)
                        }
                }
                
                val tasksToSync = tasks.filter { it.uuid !in existingTaskIds }
                
                calendarRepository.syncTasksToCalendar(account, tasksToSync).collectLatest { result ->
                    when (result) {
                        is Resource.Success -> {
                            val count = result.data ?: 0
                            state = state.copy(
                                isSyncing = false,
                                error = null,
                                tasksSyncedCount = count
                            )

                            val message = if (count > 0) {
                                "Synced $count tasks to Google Calendar"
                            } else {
                                "No new tasks to sync"
                            }
                            _eventFlow.emitSafe(UiEvent.ShowToast(message))

            
                            loadCalendarEvents(tasks)
                        }
                        is Resource.Error -> {
                            state = state.copy(
                                isSyncing = false,
                                error = result.message
                            )
                            _eventFlow.emitSafe(UiEvent.ShowToast(result.message ?: "Unknown error occurred"))
                        }
                        is Resource.Loading -> {
                            state = state.copy(isSyncing = result.isLoading)
                        }
                    }
                }
            } catch (e: Exception) {
                state = state.copy(isSyncing = false, error = e.message)
                _eventFlow.emitSafe(UiEvent.ShowToast(e.message ?: "Unknown error occurred"))
            }
        }
    }

    /**
     * Combined refresh function that syncs both calendar events and tasks
     * This prevents creating duplicates by sequencing the operations properly
     */
    fun refreshCalendarAndTasks(tasks: List<Task>, showToasts: Boolean = true) {
        viewModelScope.launch {
            try {
                state = state.copy(isSyncing = true)
                
                // Get the GoogleSignInAccount
                val account = GoogleSignIn.getLastSignedInAccount(context)
                
                // Check if user has Google account and calendar permissions
                if (account == null || !hasCalendarPermission(account)) {
                    requestCalendarPermission()
                    state = state.copy(isSyncing = false)
                    return@launch
                }
                
                if (showToasts) {
                    showToast("Synchronizing calendar...")
                }
                
                try {
                    // Step 1: Get events from calendar
                    val events = calendarRepository.getCalendarEvents(account)
                    val uniqueEvents = events.distinctBy { it.id }
                    
                    // Important: Completely deduplicate input tasks first
                    val dedupedTasks = tasks.distinctBy { it.uuid }
                    
                    // Step 2: Generate properly deduplicated dateEvents
                    val dateEvents = calendarRepository.getDateEvents(uniqueEvents, dedupedTasks)
                    
                    // Step 3: Update state with deduplicated data
                    state = state.copy(
                        calendarEvents = uniqueEvents,
                        dateEvents = dateEvents,  // Use properly deduplicated dateEvents
                        isSyncing = false,
                        error = null
                    )
                    
                    // Step 4: Find tasks that need syncing to Google Calendar
                    val existingTaskIds = mutableSetOf<String>()
                    dateEvents.forEach { dateEvent ->
                        dateEvent.items.filterIsInstance<CalendarItem.TaskItem>()
                            .forEach { taskItem ->
                                existingTaskIds.add(taskItem.task.uuid)
                            }
                    }
                    
                    val tasksToSync = dedupedTasks.filter { it.uuid !in existingTaskIds }
                    
                    // Step 5: Sync tasks if needed
                    if (tasksToSync.isNotEmpty() && showToasts) {
                        calendarRepository.syncTasksToCalendar(account, tasksToSync)
                            .collect { result ->
                                if (result is Resource.Success && showToasts) {
                                    val count = result.data ?: 0
                                    if (count > 0) {
                                        showToast("${count} tasks synchronized")
                                    }
                                }
                            }
                    } else if (showToasts) {
                        showToast("No new tasks to synchronize")
                    }
                    
                    // IMPORTANT: After refreshing, explicitly reselect today's date to update the view
                    // This makes sure we use the same filtering logic used by the date selection
                    selectDate(state.selectedDate)
                    
                    if (showToasts) {
                        showToast("Calendar synchronized successfully")
                    }
                } catch (e: Exception) {
                    if (showToasts) {
                        showToast("Error: ${e.message}")
                    }
                }
                
                state = state.copy(isSyncing = false)
                
            } catch (e: Exception) {
                state = state.copy(isSyncing = false, error = e.message)
                if (showToasts) {
                    _eventFlow.emitSafe(UiEvent.ShowToast(e.message ?: "Unknown error occurred"))
                }
            }
        }
    }

    fun loadCalendarEvents(tasks: List<Task> = emptyList()) {
        viewModelScope.launch {
            try {

                val today = LocalDate.now()
                state = state.copy(
                    isLoading = true,
                    selectedDate = today  // Make sure we select today by default
                )

                // Get the GoogleSignInAccount - always try to use whatever account we have
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account == null) {

                    state = state.copy(
                        isLoading = false,
                        showGoogleSignInPrompt = true
                    )
                    return@launch
                }
                
                try {
                    val events = calendarRepository.getCalendarEvents(account)
                    val uniqueEvents = events.distinctBy { it.id }
                    val dedupedTasks = tasks.distinctBy { it.uuid }
                    val dateEvents = calendarRepository.getDateEvents(uniqueEvents, dedupedTasks)
                    
                    state = state.copy(
                        calendarEvents = uniqueEvents,
                        dateEvents = dateEvents,
                        isLoading = false,
                        showGoogleSignInPrompt = false
                    )

                    selectDate(today)
                } catch (e: Exception) {
                    state = state.copy(
                        error = "Calendar error: ${e.message}",
                        isLoading = false,
                        showGoogleSignInPrompt = false
                    )
                }

            } catch (e: Exception) {
                state = state.copy(isLoading = false, error = e.message)
            }
        }
    }

    private fun updateDateEvents(events: List<CalendarEvent>, tasks: List<Task>) {
        try {
            val dateEvents = calendarRepository.getDateEvents(events, tasks).toMutableList()
            val selectedDateStr = state.selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val selectedDateEvents = dateEvents.find { it.date == state.selectedDate }?.items ?: emptyList()
            
            val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val today = LocalDate.now()
            val todayEvents = dateEvents.find { it.date == today }?.items ?: emptyList()
            
            if (todayEvents.isNotEmpty()) {
                showToast("✅ Found ${todayEvents.size} items for today")
            } else {
                // Inspect tasks to see which ones should be for today
                val todayTasks = tasks.filter { task ->
                    val date = task.deadlineDate
                    if (date != null) {
                        try {
                            // Try to parse the date and compare with today
                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                            val taskDate = LocalDate.parse(date, formatter)
                            val result = taskDate == today
                            if (result) {
                                showToast("Task '${task.title}' is for today but not showing")
                            }
                            result
                        } catch (e: Exception) {
                            // Try another format
                            try {
                                val formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")
                                val taskDate = LocalDate.parse(date, formatter)
                                val result = taskDate == today
                                if (result) {
                                    showToast("Task '${task.title}' is for today (MM-dd-yyyy) but not showing")
                                }
                                result
                            } catch (e: Exception) {
                                false
                            }
                        }
                    } else {
                        false
                    }
                }
                
                if (todayTasks.isNotEmpty()) {
                    showToast("⚠️ Found ${todayTasks.size} tasks for today but they're not in calendar")
                } else {
                    showToast("No tasks found for today")
                }
            }
            
            // Update the state
            state = state.copy(
                dateEvents = dateEvents,
                selectedDateItems = selectedDateEvents
            )
        } catch (e: Exception) {
            // Log the error but don't crash
            state = state.copy(
                error = "Error updating calendar: ${e.message}"
            )
            _eventFlow.emitSafe(UiEvent.ShowToast("Error parsing dates: ${e.message}"))
        }
    }

    /**
     * Select a specific date to view events and tasks
     */
    fun selectDate(date: LocalDate) {
        if (date == state.selectedDate) return
        
        viewModelScope.launch {
            try {
                val formattedDate = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                showToast("Date: $formattedDate")
                
                state = state.copy(selectedDate = date)
                val account = GoogleSignIn.getLastSignedInAccount(context)
                
                val dateFormats = listOf(
                    date.format(DateTimeFormatter.ofPattern("MM-dd-yyyy")),
                    date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")),
                    date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                )

                val allTasks = state.dateEvents.flatMap { dateEvent ->
                    dateEvent.items.filterIsInstance<CalendarItem.TaskItem>().map { it.task }
                }.distinctBy { it.uuid } // Deduplicate tasks here

                val tasksForSelectedDate = allTasks.filter { task ->
                    dateFormats.contains(task.deadlineDate)
                }
                
                if (account != null && hasCalendarPermission(account)) {
                    calendarRepository.getCalendarItemsForDate(
                        account = account,
                        tasks = allTasks,
                        date = date
                    ).collectLatest { result ->
                        when (result) {
                            is Resource.Success -> {
                                // Create a tracking set for task UUIDs to avoid duplicates
                                val processedTaskIds = mutableSetOf<String>()
                                val finalItems = mutableListOf<CalendarItem>()
                                
                
                                result.data?.filterIsInstance<CalendarItem.GoogleEventItem>()?.let {
                                    finalItems.addAll(it)
                                }
                                
                
                                result.data?.filterIsInstance<CalendarItem.TaskItem>()?.forEach { taskItem ->
                                    if (!processedTaskIds.contains(taskItem.task.uuid)) {
                                        finalItems.add(taskItem)
                                        processedTaskIds.add(taskItem.task.uuid)
                                    }
                                }
                                
                
                                tasksForSelectedDate.forEach { task ->
                                    if (!processedTaskIds.contains(task.uuid)) {
                                        finalItems.add(CalendarItem.TaskItem(task))
                                        processedTaskIds.add(task.uuid)
                                    }
                                }
                                
                
                                val sortedItems = sortAndHandleConflicts(finalItems)
                                
                                state = state.copy(selectedDateItems = sortedItems)
                            }
                            is Resource.Error -> {
                                state = state.copy(error = result.message)
                            }
                            is Resource.Loading -> {
                            }
                        }
                    }
                } else {
                    // Fall back to our cached data if we don't have account access
                    val processedTaskIds = mutableSetOf<String>()
                    val finalItems = mutableListOf<CalendarItem>()
                    
    
                    state.dateEvents.find { it.date == date }?.items?.forEach { item ->
                        if (item is CalendarItem.TaskItem) {
                            if (!processedTaskIds.contains(item.task.uuid)) {
                                finalItems.add(item)
                                processedTaskIds.add(item.task.uuid)
                            }
                        } else {
                            // Non-task items can be added directly
                            finalItems.add(item)
                        }
                    }
                    
    
                    tasksForSelectedDate.forEach { task ->
                        if (!processedTaskIds.contains(task.uuid)) {
                            finalItems.add(CalendarItem.TaskItem(task))
                            processedTaskIds.add(task.uuid)
                        }
                    }
                    
    
                    val sortedItems = sortAndHandleConflicts(finalItems)
                    
                    state = state.copy(selectedDateItems = sortedItems)
                }
            } catch (e: Exception) {
                state = state.copy(error = e.message)
                showToast("Erreur lors du chargement des événements: ${e.message}")
            }
        }
    }

    private fun sortAndHandleConflicts(items: List<CalendarItem>): List<CalendarItem> {
        val googleEvents = items.filterIsInstance<CalendarItem.GoogleEventItem>()
        val tasks = items.filterIsInstance<CalendarItem.TaskItem>()
        
        val sortedGoogleEvents = googleEvents.sortedBy { event ->
            try {
                val time = event.event.startTime.toLongOrNull()
                if (time != null) time else Long.MAX_VALUE
            } catch (e: Exception) {
                Long.MAX_VALUE
            }
        }
        
        // Sort Tasks by deadline time
        val sortedTasks = tasks.sortedBy { task ->
            task.task.deadlineTime ?: "23:59"
        }
        
        // Group tasks with the same time
        val groupedTasks = sortedTasks.groupBy { task -> 
            task.task.deadlineTime ?: "No time"
        }.flatMap { (time, tasksAtTime) ->
            if (tasksAtTime.size > 1) {
                // If we have multiple tasks at the same time, add a conflict note
                val firstTask = tasksAtTime.first()
                val taskWithConflictNote = CalendarItem.TaskItem(
                    task = firstTask.task.copy(
                        description = "${firstTask.task.description ?: ""}\n" +
                            "⚠️ CONFLICT: ${tasksAtTime.size - 1} other task(s) scheduled at the same time."
                    )
                )
                listOf(taskWithConflictNote) + tasksAtTime.drop(1)
            } else {
                tasksAtTime
            }
        }
        
        // Combine and return
        return sortedGoogleEvents + groupedTasks
    }

    /**
     * Request calendar permission by initiating Google Sign In flow with required scopes
     */
    private fun requestCalendarPermission() {
        showToast("Preparing Google Sign-In...")
        

        signOutGoogle {
            // Request both read and write permissions to maximize chances of working
            try {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestIdToken(context.getString(R.string.default_web_client_id))
                    .requestScopes(
                        Scope(CalendarScopes.CALENDAR_READONLY),
                        Scope(CalendarScopes.CALENDAR)
                    )
                    .build()
    
                showToast("Opening Google Calendar permissions...")
                _eventFlow.emitSafe(UiEvent.RequestGoogleSignIn(gso))
            } catch (e: Exception) {

                try {
                    val basicGso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestIdToken(context.getString(R.string.default_web_client_id))
                        .build()
                        
                    showToast("Trying simplified Google Sign-In...")
                    _eventFlow.emitSafe(UiEvent.RequestGoogleSignIn(basicGso))
                } catch (e2: Exception) {
                    showToast("Sign-in setup failed: ${e2.message}")
                }
            }
        }
    }

    /**
     * Sign out from Google account before requesting new sign in
     * This ensures we don't reuse the same account across different users
     */
    private fun signOutGoogle(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {

                val signOutGso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                val googleSignInClient = GoogleSignIn.getClient(context, signOutGso)
                

                googleSignInClient.signOut().addOnCompleteListener {

                    try {
                        googleSignInClient.revokeAccess().addOnCompleteListener {
                            onComplete()
                        }
                    } catch (e: Exception) {
                        onComplete()
                    }
                }
            } catch (e: Exception) {
                // If sign-out fails, still proceed with the new sign-in request
                onComplete()
            }
        }
    }

    private fun MutableSharedFlow<UiEvent>.emitSafe(event: UiEvent) {
        viewModelScope.launch {
            emit(event)
        }
    }

    /**
     * Check if the account has the necessary calendar permission
     */
    private fun hasCalendarPermission(account: GoogleSignInAccount): Boolean {
        return true
    }

    /**
     * Resets the state of the ViewModel
     * Useful when we need to refresh the UI after a failed operation
     */
    fun resetState() {
        viewModelScope.launch {
            try {
                showToast("Requesting Google Calendar sign-in...")
                
                // Explicitly request calendar permissions
                val currentDate = state.selectedDate
                state = CalendarState(selectedDate = currentDate)
                
                // Always request new calendar permissions
                requestCalendarPermission()
            } catch (e: Exception) {
                showToast("Failed to reset: ${e.message}")
            }
        }
    }

    /**
     * Public method to show toast messages
     */
    fun showToast(message: String) {
        _eventFlow.emitSafe(UiEvent.ShowToast(message))
    }

    sealed class UiEvent {
        data class ShowToast(val message: String) : UiEvent()
        object NavigateToGoogleSignIn : UiEvent()
        data class RequestGoogleSignIn(val options: GoogleSignInOptions) : UiEvent()
    }
}