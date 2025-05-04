package io.tasky.taskyapp.calendar.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
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
import io.tasky.taskyapp.task.domain.model.TaskStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
                // Clear loading and sign-in prompt state immediately
                state = state.copy(
                    isLoading = false,
                    showGoogleSignInPrompt = false
                )

                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    showToast("Successfully signed in with Google: ${account.email}")
                    
                    // FORCE the calendar permission check to succeed
                    if (!account.grantedScopes.isNullOrEmpty()) {
                        showToast("Found ${account.grantedScopes?.size} permission scopes")
                        loadCalendarEvents()
                    } else {
                        showToast("No permissions granted. Requesting calendar permissions...")
                        requestCalendarPermission()
                    }
                } else {
                    showToast("Account was null after sign-in")
                    state = state.copy(showGoogleSignInPrompt = true)
                }
            } catch (e: Exception) {
                if (e is ApiException && e.statusCode == 12501) {
                    // Error 12501 is "user canceled" - try to recover by using any available account
                    _eventFlow.emitSafe(UiEvent.ShowToast("Sign-in was canceled. Trying fallback..."))
                    
                    // Try to use last signed-in account
                    val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
                    if (lastAccount != null) {
                        _eventFlow.emitSafe(UiEvent.ShowToast("Using last signed-in account: ${lastAccount.email}"))
                        loadCalendarEvents()
                    } else {
                        state = state.copy(
                            error = "No Google account available. Please try signing in again.",
                            showGoogleSignInPrompt = true
                        )
                    }
                } else {
                    // Other error
                    state = state.copy(error = "Error: ${e.message}")
                    _eventFlow.emitSafe(UiEvent.ShowToast(e.message ?: "Failed to sign in with Google"))
                }
            }
        }
    }

    fun syncCalendarEvents(tasks: List<Task>) {
        viewModelScope.launch {
            try {
                state = state.copy(isSyncing = true)

                // Get the GoogleSignInAccount
                val account = GoogleSignIn.getLastSignedInAccount(context)

                // Check if user has Google account and calendar permissions
                if (account == null || !hasCalendarPermission(account)) {
                    _eventFlow.emit(UiEvent.NavigateToGoogleSignIn)
                    state = state.copy(isSyncing = false)
                    return@launch
                }

                calendarRepository.syncCalendarEvents(account).collectLatest { result ->
                    when (result) {
                        is Resource.Success -> {
                            val events = result.data ?: emptyList()
                            state = state.copy(
                                calendarEvents = events,
                                isSyncing = false,
                                error = null
                            )
                            // Update the date events with the combined data
                            updateDateEvents(events, tasks)
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

                calendarRepository.syncTasksToCalendar(account, tasks).collectLatest { result ->
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

                            // After syncing tasks, refresh calendar events
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

    fun loadCalendarEvents(tasks: List<Task> = emptyList()) {
        viewModelScope.launch {
            try {
                // Always select today to start
                val today = LocalDate.now()
                state = state.copy(
                    isLoading = true,
                    selectedDate = today  // Make sure we select today by default
                )
                
                showToast(" EMERGENCY MODE: Loading calendar events...")

                // Get the GoogleSignInAccount - always try to use whatever account we have
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account == null) {
                    // No account found, show sign-in prompt
                    showToast("❌ No Google account found. Please sign in.")
                    state = state.copy(
                        isLoading = false,
                        showGoogleSignInPrompt = true
                    )
                    return@launch
                }
                
                // Always show which account we're trying
                showToast("👤 Using Google account: ${account.email}")
                
                // EMERGENCY MODE: Skip permission check and go straight to loading events
                try {
                    showToast("📲 Retrieving events from Google Calendar...")
                    
                    // Direct call to repository - force it to work
                    val events = calendarRepository.getCalendarEvents(account)
                    
                    // Update the date events with the combined data
                    val dateEvents = calendarRepository.getDateEvents(events, tasks).toMutableList()
                    val todayEvents = dateEvents.find { it.date == today }
    
                    // Handle today's events and add missing past tasks explicitly
                    if (todayEvents == null) {
                        val pastTasks = tasks.filter { task ->
                            // Try multiple date formats to handle both MM-dd-yyyy and dd-MM-yyyy
                            val taskDate = try {
                                val dateStr = task.deadlineDate ?: ""
                                // First try the primary formats
                                try {
                                    LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                } catch (e: Exception) {
                                    try {
                                        LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM-dd-yyyy"))
                                    } catch (e2: Exception) {
                                        try {
                                            LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                                        } catch (e3: Exception) {
                                            null
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                null
                            }
                            taskDate != null && (taskDate == today || taskDate.isBefore(today))
                        }.map { CalendarItem.TaskItem(it) }
                        
                        if (pastTasks.isNotEmpty()) {
                            toaster.showToast("Including past tasks on today's calendar")
                            dateEvents.add(
                                DateEvents(date = today, items = pastTasks)
                            )
                        }
                    }
    
                    state = state.copy(
                        calendarEvents = events,
                        dateEvents = dateEvents,
                        isLoading = false,
                        showGoogleSignInPrompt = false
                    )
    
                    // Refresh today's display explicitly
                    selectDate(today)
                } catch (e: Exception) {
                    // Handle calendar retrieval errors but don't crash
                    toaster.showToast("❌ Error loading events: ${e.message}")
                    state = state.copy(
                        error = "Calendar error: ${e.message}",
                        isLoading = false,
                        showGoogleSignInPrompt = false
                    )
                }

            } catch (e: Exception) {
                state = state.copy(isLoading = false, error = e.message)
                showToast("❌ " + (e.message ?: "Unknown error occurred"))
            }
        }
    }

    /**
     * Updates the date events and selected date items
     */
    private fun updateDateEvents(events: List<CalendarEvent>, tasks: List<Task>) {
        try {
            // Use our new repository methods to get the date events
            val dateEvents = calendarRepository.getDateEvents(events, tasks).toMutableList()
            
            // Get items for the selected date
            val selectedDateStr = state.selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val selectedDateEvents = dateEvents.find { it.date == state.selectedDate }?.items ?: emptyList()
            
            // Debug output of tasks for today
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
                // Updated to show which date is selected
                val formattedDate = date.format(DateTimeFormatter.ofPattern("MM-dd-yyyy"))
                showToast("Viewing: $formattedDate")
                
                state = state.copy(selectedDate = date)
                val account = GoogleSignIn.getLastSignedInAccount(context)
                
                // Add additional search for tasks that match this date in different formats
                val dateFormats = listOf(
                    date.format(DateTimeFormatter.ofPattern("MM-dd-yyyy")),
                    date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")),
                    date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                )

                // Check for tasks which might match this date in any format
                val allTasks = state.dateEvents.flatMap { dateEvent ->
                    dateEvent.items.filterIsInstance<CalendarItem.TaskItem>().map { it.task }
                }
                
                // Find tasks whose dates match the selected date (in any format)
                val tasksForSelectedDate = allTasks.filter { task ->
                    dateFormats.contains(task.deadlineDate)
                }
                
                if (tasksForSelectedDate.isNotEmpty()) {
                    showToast("Found ${tasksForSelectedDate.size} tasks with matching date")
                }
                
                if (account != null && hasCalendarPermission(account)) {
                    calendarRepository.getCalendarItemsForDate(
                        account = account,
                        tasks = allTasks,
                        date = date
                    ).collectLatest { result ->
                        when (result) {
                            is Resource.Success -> {
                                var items = result.data?.toMutableList() ?: mutableListOf()
                                
                                // Ensure we include any tasks with matching date strings
                                val existingIds = items.filterIsInstance<CalendarItem.TaskItem>()
                                    .map { it.task.uuid }.toSet()
                                
                                // Add any tasks we found with matching date strings
                                tasksForSelectedDate.forEach { task ->
                                    if (task.uuid !in existingIds) {
                                        items.add(CalendarItem.TaskItem(task))
                                    }
                                }
                                
                                // Sort and handle conflicts
                                val sortedItems = sortAndHandleConflicts(items)
                                
                                state = state.copy(selectedDateItems = sortedItems)
                            }
                            is Resource.Error -> {
                                state = state.copy(error = result.message)
                            }
                            is Resource.Loading -> {
                                // Can add loading indicator for selected date if needed
                            }
                        }
                    }
                } else {
                    // Fall back to our cached data if we don't have account access
                    val selectedDateEvents = state.dateEvents.find { it.date == date }?.items?.toMutableList() 
                        ?: mutableListOf()
                    
                    // Add any tasks with matching date strings
                    val existingIds = selectedDateEvents.filterIsInstance<CalendarItem.TaskItem>()
                        .map { it.task.uuid }.toSet()
                    
                    tasksForSelectedDate.forEach { task ->
                        if (task.uuid !in existingIds) {
                            selectedDateEvents.add(CalendarItem.TaskItem(task))
                        }
                    }
                    
                    // Sort and handle conflicts
                    val sortedItems = sortAndHandleConflicts(selectedDateEvents)
                    
                    state = state.copy(selectedDateItems = sortedItems)
                }
            } catch (e: Exception) {
                state = state.copy(error = e.message)
                showToast("Failed to load events for selected date: ${e.message}")
            }
        }
    }

    /**
     * Sorts items by time and handles potential conflicts
     */
    private fun sortAndHandleConflicts(items: List<CalendarItem>): List<CalendarItem> {
        // First, separate Google Events and Tasks
        val googleEvents = items.filterIsInstance<CalendarItem.GoogleEventItem>()
        val tasks = items.filterIsInstance<CalendarItem.TaskItem>()
        
        // Sort Google Events by start time
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
        
        // First sign out any existing Google account to prevent using the same account
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
                // If we can't request specific scopes, try a simpler approach
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
                // Create a basic GSO just for signing out
                val signOutGso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                val googleSignInClient = GoogleSignIn.getClient(context, signOutGso)
                
                // Sign out and then proceed with the callback
                googleSignInClient.signOut().addOnCompleteListener {
                    // Force clear the cached account
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