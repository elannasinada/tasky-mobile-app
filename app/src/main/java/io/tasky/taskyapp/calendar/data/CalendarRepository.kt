package io.tasky.taskyapp.calendar.data

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.firebase.auth.FirebaseAuth
import io.tasky.taskyapp.core.util.Resource
import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * Sealed class to represent items that can appear in a calendar view
 */
sealed class CalendarItem {
    data class GoogleEventItem(val event: CalendarEvent) : CalendarItem()
    data class TaskItem(val task: Task) : CalendarItem()
}

/**
 * Data class to hold events for a specific date
 */
data class DateEvents(
    val date: LocalDate,
    val items: List<CalendarItem>
) {
    // Calculate counts by status
    val completedCount: Int = items.count { 
        it is CalendarItem.TaskItem && it.task.status == TaskStatus.COMPLETED.name 
    }
    
    val cancelledCount: Int = items.count { 
        it is CalendarItem.TaskItem && it.task.status == TaskStatus.CANCELLED.name 
    }
    
    val inProgressCount: Int = items.count { 
        it is CalendarItem.TaskItem && it.task.status == TaskStatus.IN_PROGRESS.name 
    }
    
    val pendingCount: Int = items.count { 
        it is CalendarItem.TaskItem && it.task.status == TaskStatus.PENDING.name 
    }
    
    val googleEventCount: Int = items.count { it is CalendarItem.GoogleEventItem }
}

class CalendarRepository @Inject constructor(
    private val context: Context,
    private val calendarRealtimeDatabaseClient: CalendarRealtimeDatabaseClient,
    private val auth: FirebaseAuth
) {
    companion object {
        private const val CALENDAR_ID = "primary"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            .apply { timeZone = TimeZone.getDefault() }
    }

    /**
     * Gets calendar events from the user's Google account
     * Make sure the account parameter is specific to the logged-in user
     */
    suspend fun getCalendarEvents(account: GoogleSignInAccount): List<CalendarEvent> {
        return withContext(Dispatchers.IO) {
            try {
                // Log account email to verify we're using the right account
                Log.d("CalendarRepo", "CALENDAR_DEBUG: Attempting to get calendar events for: ${account.email}")
                
                // Use a simple approach that has maximum chance of working
                val credential = GoogleAccountCredential.usingOAuth2(
                    context,
                    listOf(CalendarScopes.CALENDAR_READONLY, CalendarScopes.CALENDAR)
                ).apply {
                    selectedAccount = account.account
                }

                // Set up the calendar service with minimal configuration
                val httpTransport = NetHttpTransport()
                val jsonFactory = GsonFactory.getDefaultInstance()
                
                Log.d("CalendarRepo", "CALENDAR_DEBUG: Building calendar service")
                val calendarService = Calendar.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName("Tasky")
                    .build()
                
                // Request past and future events to ensure we get something
                val now = DateTime(System.currentTimeMillis())
                val oneMonthAgo = DateTime(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
                
                Log.d("CalendarRepo", "CALENDAR_DEBUG: Requesting events list")
                val request = calendarService.events().list("primary")
                    .setMaxResults(100)  // Get more events to ensure we have data
                    .setTimeMin(oneMonthAgo) // Include events from last month
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                
                // Execute the request with a timeout
                Log.d("CalendarRepo", "CALENDAR_DEBUG: Executing calendar request")
                val events = request.execute()
                
                // Check if we got any events
                val itemCount = events.items?.size ?: 0
                Log.d("CalendarRepo", "CALENDAR_DEBUG: Retrieved $itemCount events")
                
                if (itemCount > 0) {
                    // We have events, map them to our model
                    val mappedEvents = events.items.mapNotNull { event ->
                        try {
                            // Extract event details carefully
                            val startTime = event.start?.dateTime?.value?.toString() 
                                ?: event.start?.date?.value?.toString()
                                ?: ""
                            val endTime = event.end?.dateTime?.value?.toString()
                                ?: event.end?.date?.value?.toString() 
                                ?: ""
                                
                            // Log each event's details for debugging
                            Log.d("CalendarRepo", "CALENDAR_DEBUG: Event ${event.id}: ${event.summary}")
                            
                            CalendarEvent(
                                id = event.id,
                                title = event.summary ?: "Untitled Event",
                                description = event.description ?: "",
                                startTime = startTime,
                                endTime = endTime,
                                location = event.location ?: ""
                            )
                        } catch (e: Exception) {
                            // Skip events with bad data
                            Log.e("CalendarRepo", "CALENDAR_DEBUG: Failed to parse event: ${e.message}")
                            null
                        }
                    }
                    
                    Log.d("CalendarRepo", "CALENDAR_DEBUG: Successfully mapped ${mappedEvents.size} events")
                    mappedEvents
                } else {
                    // No events found
                    Log.w("CalendarRepo", "CALENDAR_DEBUG: No events found for account ${account.email}")
                    // Add a test event so we know the function is at least running
                    listOf(
                        CalendarEvent(
                            id = "test-event-1",
                            title = "TEST EVENT - Your calendar is working",
                            description = "This is a test event to confirm calendar access is working",
                            startTime = DateTime(System.currentTimeMillis()).toString(),
                            endTime = DateTime(System.currentTimeMillis() + 3600000).toString(),
                            location = "Tasky App"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("CalendarRepo", "CALENDAR_DEBUG: Critical error fetching calendar: ${e.message}", e)
                // Return a dummy event to indicate something went wrong
                listOf(
                    CalendarEvent(
                        id = "error-event",
                        title = "ERROR LOADING CALENDAR",
                        description = "Error: ${e.message}",
                        startTime = DateTime(System.currentTimeMillis()).toString(),
                        endTime = DateTime(System.currentTimeMillis() + 3600000).toString(),
                        location = "Error"
                    )
                )
            }
        }
    }

    /**
     * Groups events and tasks by date to create a calendar-friendly data structure
     * @param googleEvents The Google Calendar events to include
     * @param tasks The user's tasks to include
     * @return A map where the key is a date string (YYYY-MM-DD) and the value is a list of calendar items for that date
     */
    fun getCalendarItemsByDate(
        googleEvents: List<CalendarEvent>,
        tasks: List<Task>
    ): Map<String, List<CalendarItem>> {
        val allItems = mutableListOf<Pair<String, CalendarItem>>()
        
        // Add Google Calendar events
        googleEvents.forEach { event ->
            try {
                // Extract date from event's start time (could be a timestamp)
                val dateKey = if (event.startTime.isNotEmpty()) {
                    val timestamp = event.startTime.toLongOrNull()
                    if (timestamp != null) {
                        val date = Date(timestamp)
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
                    } else if (event.startTime.length >= 10) {
                        // Assuming format like 2023-10-15T09:00:00
                        event.startTime.substring(0, 10)
                    } else {
                        // Fallback to current date if format is unknown
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    }
                } else {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                }
                
                Log.d("CalendarRepo", "Google Event '${event.title}' mapped to date: $dateKey")
                allItems.add(dateKey to CalendarItem.GoogleEventItem(event))
            } catch (e: Exception) {
                // Skip events with parsing issues
                Log.e("CalendarRepo", "Failed to parse Google event date: ${e.message}")
            }
        }
        
        // Add user tasks
        Log.d("CalendarRepo", "Processing ${tasks.size} tasks")
        tasks.forEach { task ->
            val taskDateStr = task.deadlineDate
            if (!taskDateStr.isNullOrEmpty()) {
                try {
                    // Debug print for investigation - see actual date format
                    Log.d("CalendarRepo", "TASK_DEBUG: Processing task date: '$taskDateStr' for task: ${task.title}")
                    
                    // Try multiple date formats to ensure we catch all variations
                    val parsedDate = parseMultiFormatDate(taskDateStr)
                    val normalizedDate = parsedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    
                    // For debugging - show what the date was parsed as
                    Log.d("CalendarRepo", "TASK_DEBUG: Task '${task.title}' with date '$taskDateStr' parsed as: ${parsedDate.dayOfMonth}/${parsedDate.monthValue}/${parsedDate.year}, normalized to: '$normalizedDate'")
                    
                    allItems.add(normalizedDate to CalendarItem.TaskItem(task))
                    
                    // Also add today's tasks explicitly as a fallback
                    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    if (isToday(taskDateStr) || isToday(normalizedDate)) {
                        Log.d("CalendarRepo", "TASK_DEBUG: Marking task as TODAY")
                        allItems.add(today to CalendarItem.TaskItem(task))
                    }
                    
                } catch (e: Exception) {
                    // If parsing fails, use original string and also try today
                    Log.e("CalendarRepo", "TASK_DEBUG: Failed to parse date: $taskDateStr", e)
                    allItems.add(taskDateStr to CalendarItem.TaskItem(task))
                    
                    // Try to determine if this is today's task
                    if (isToday(taskDateStr)) {
                        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        Log.d("CalendarRepo", "Added task '${task.title}' to TODAY because date '$taskDateStr' appears to be today")
                        allItems.add(today to CalendarItem.TaskItem(task))
                    }
                }
            } else {
                // For tasks with no deadline, assume they're for today
                val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                allItems.add(today to CalendarItem.TaskItem(task))
            }
        }
        
        // Group by date
        val grouped = allItems.groupBy(
            keySelector = { it.first },
            valueTransform = { it.second }
        )
        
        // Log the results
        grouped.forEach { (date, items) ->
            val taskCount = items.count { it is CalendarItem.TaskItem }
            val eventCount = items.count { it is CalendarItem.GoogleEventItem }
            Log.d("CalendarRepo", "Date $date: $taskCount tasks, $eventCount Google events")
        }
        
        return grouped
    }

    /**
     * Gets a list of DateEvents objects, sorted by date
     * This is convenient for calendar views that show events grouped by date
     */
    fun getDateEvents(
        googleEvents: List<CalendarEvent>,
        tasks: List<Task>
    ): List<DateEvents> {
        // Gather items by their respective dates
        val itemsByDate = getCalendarItemsByDate(googleEvents, tasks)
        val dateEvents = mutableListOf<DateEvents>()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        
        // Process items grouped by date
        itemsByDate.forEach { (dateStr, items) ->
            try {
                val localDate = LocalDate.parse(dateStr, dateFormatter)
                dateEvents.add(DateEvents(date = localDate, items = items))
            } catch (e: Exception) {
                // If parsing fails, log it but don't crash
                Log.e("CalendarRepo", "Failed to parse date: $dateStr", e)
            }
        }
        
        // Now ensure today has all relevant tasks
        val today = LocalDate.now()
        val todayStr = today.format(dateFormatter)
        val existingToday = dateEvents.find { it.date == today }
        
        // Process all tasks to find those that should be shown today
        val todaysTasks = tasks.flatMap { task ->
            // If the task has a deadline date that matches today (in any format)
            val dateStr = task.deadlineDate
            if (dateStr != null) {
                try {
                    val taskDate = parseMultiFormatDate(dateStr)
                    if (taskDate == today) {
                        listOf(CalendarItem.TaskItem(task))
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    // If we can't parse the date but it looks like today
                    val todayParts = today.toString().split("-")
                    val dateParts = dateStr.replace("/", "-").split("-")
                    
                    // Check if it's today in any common format
                    if ((dateParts.size == 3 && todayParts.size == 3) &&
                        (
                            // yyyy-MM-dd
                            (dateParts[0] == todayParts[0] && dateParts[1] == todayParts[1] && dateParts[2] == todayParts[2]) ||
                            // MM-dd-yyyy
                            (dateParts[0] == todayParts[1] && dateParts[1] == todayParts[2] && dateParts[2] == todayParts[0]) ||
                            // dd-MM-yyyy
                            (dateParts[0] == todayParts[2] && dateParts[1] == todayParts[1] && dateParts[2] == todayParts[0])
                        )
                    ) {
                        listOf(CalendarItem.TaskItem(task))
                    } else {
                        emptyList()
                    }
                }
            } else {
                // Tasks with no date are shown today
                listOf(CalendarItem.TaskItem(task))
            }
        }
        
        // If we found any tasks for today
        if (todaysTasks.isNotEmpty()) {
            if (existingToday != null) {
                // Add these tasks to the existing today entry
                val existingTaskIds = existingToday.items
                    .filterIsInstance<CalendarItem.TaskItem>()
                    .map { it.task.uuid }
                    .toSet()
                
                val newTasks = todaysTasks.filter { 
                    it is CalendarItem.TaskItem && it.task.uuid !in existingTaskIds 
                }
                
                if (newTasks.isNotEmpty()) {
                    // Replace the existing today entry
                    dateEvents.remove(existingToday)
                    dateEvents.add(DateEvents(date = today, items = existingToday.items + newTasks))
                }
            } else {
                // Create a new today entry
                dateEvents.add(DateEvents(date = today, items = todaysTasks))
            }
        }
        
        // Return the date events sorted by date
        return dateEvents.sortedBy { it.date }
    }
    
    /**
     * Parses a date string using multiple possible formats
     * Enhanced to correctly handle European vs American date formats
     */
    private fun parseMultiFormatDate(dateStr: String): LocalDate {
        // First try the unambiguous format yyyy-MM-dd
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        } catch (e: Exception) {
            // Not in this format, continue
        }
        
        // Parse the components for ambiguous formats (MM-dd-yyyy vs dd-MM-yyyy)
        val parts = dateStr.replace("/", "-").split("-")
        if (parts.size == 3) {
            try {
                // Check for year in last position (most common)
                val yearStr = parts[2]
                if (yearStr.length == 4) { // Looks like a year
                    val firstNum = parts[0].toInt()
                    val secondNum = parts[1].toInt()
                    val yearNum = yearStr.toInt()
                    
                    // Use intelligent resolution for ambiguous MM-dd vs dd-MM
                    if (firstNum > 12 && secondNum <= 12) {
                        // First number is too large for a month, must be dd-MM-yyyy
                        Log.d("CalendarRepo", "Resolving $dateStr as dd-MM-yyyy (day-month-year)")
                        return LocalDate.of(yearNum, secondNum, firstNum)
                    } else if (secondNum > 12 && firstNum <= 12) {
                        // Second number is too large for a month, must be MM-dd-yyyy
                        Log.d("CalendarRepo", "Resolving $dateStr as MM-dd-yyyy (month-day-year)")
                        return LocalDate.of(yearNum, firstNum, secondNum)
                    } else if (firstNum <= 12 && secondNum <= 12) {
                        // Truly ambiguous case - both could be month or day
                        // Default to dd-MM-yyyy (European format) since that's what the user is using
                        Log.d("CalendarRepo", "AMBIGUOUS DATE FORMAT: $dateStr could be MM-dd-yyyy or dd-MM-yyyy, defaulting to dd-MM-yyyy")
                        return LocalDate.of(yearNum, secondNum, firstNum)
                    }
                }
            } catch (e: Exception) {
                Log.e("CalendarRepo", "Error parsing date components: $dateStr", e)
            }
        }
        
        // If we couldn't resolve using our custom logic, try standard formats in sequence
        val possibleFormats = listOf(
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),  // Try European format first
            DateTimeFormatter.ofPattern("MM-dd-yyyy"),  // Then American
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
        )
        
        for (formatter in possibleFormats) {
            try {
                return LocalDate.parse(dateStr, formatter)
            } catch (e: Exception) {
                // Try next format
            }
        }
        
        // If all parsing attempts fail, return today's date as fallback
        Log.w("CalendarRepo", "Failed to parse date: $dateStr - using today's date")
        return LocalDate.now()
    }

    /**
     * Check if a date string represents today
     */
    private fun isToday(dateString: String): Boolean {
        try {
            val today = LocalDate.now()
            
            // Try to parse with our multi-format parser
            val parsedDate = parseMultiFormatDate(dateString)
            return parsedDate == today
        } catch (e: Exception) {
            // Try manual comparison of common formats
            val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val todayMonth = LocalDate.now().monthValue
            val todayDay = LocalDate.now().dayOfMonth
            val todayYear = LocalDate.now().year
            
            // Check for different formats like MM-dd-yyyy, dd-MM-yyyy, etc.
            if (dateString.contains("-") || dateString.contains("/")) {
                val parts = dateString.replace("/", "-").split("-")
                if (parts.size == 3) {
                    try {
                        // Try MM-dd-yyyy
                        if (parts[0].toInt() == todayMonth && 
                            parts[1].toInt() == todayDay && 
                            parts[2].toInt() == todayYear) return true
                        
                        // Try dd-MM-yyyy
                        if (parts[1].toInt() == todayMonth && 
                            parts[0].toInt() == todayDay && 
                            parts[2].toInt() == todayYear) return true
                    } catch (e: Exception) {
                        // Failed to parse
                    }
                }
            }
            
            // Direct string comparison as last resort
            return dateString == todayStr
        }
    }

    /**
     * Create a Google Calendar event from a Task
     */
    suspend fun createCalendarEvent(account: GoogleSignInAccount, task: Task): Flow<Resource<String>> = flow {
        emit(Resource.Loading(true))

        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(CalendarScopes.CALENDAR_EVENTS)
            ).apply {
                selectedAccount = account.account
            }

            val calendarService = Calendar.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Tasky").build()

            // Convert task date and time to proper format
            val taskDateTime = combineDateTime(task.deadlineDate ?: "", task.deadlineTime ?: "")

            // Create event object
            val event = Event()
                .setSummary(task.title)
                .setDescription(task.description ?: "")

            // Set start time (assuming task time is the start time)
            val start = EventDateTime()
                .setDateTime(DateTime(taskDateTime.time))
            event.setStart(start)

            // Set end time (default to 1 hour after start)
            val endTime = Date(taskDateTime.time + 3600000) // 1 hour in milliseconds
            val end = EventDateTime()
                .setDateTime(DateTime(endTime))
            event.setEnd(end)

            // Set recurrence if task is recurring
            if (task.isRecurring) {
                val recurrenceRule = buildRecurrenceRule(task)
                if (recurrenceRule.isNotEmpty()) {
                    event.setRecurrence(listOf(recurrenceRule))
                }
            }

            // Insert the event
            val createdEvent = calendarService.events().insert(CALENDAR_ID, event).execute()

            // Save reference to the event
            val currentUser = auth.currentUser?.run {
                UserData(
                    userId = uid,
                    userName = displayName,
                    profilePictureUrl = photoUrl?.toString(),
                    email = email
                )
            } ?: throw Exception("User not authenticated")

            val newRef = CalendarEventRef(
                googleEventId = createdEvent.id,
                taskId = task.uuid,
                lastSyncTimestamp = System.currentTimeMillis()
            )
            calendarRealtimeDatabaseClient.insertCalendarEventRef(currentUser, newRef)

            emit(Resource.Success(createdEvent.id))
        } catch (e: Exception) {
            emit(Resource.Error(message = e.message ?: "Failed to create calendar event"))
        } finally {
            emit(Resource.Loading(false))
        }
    }

    /**
     * Sync tasks to Google Calendar
     */
    suspend fun syncTasksToCalendar(
        account: GoogleSignInAccount,
        tasks: List<Task>
    ): Flow<Resource<Int>> = flow {
        emit(Resource.Loading(true))

        try {
            var syncedCount = 0
            val currentUser = auth.currentUser?.run {
                UserData(
                    userId = uid,
                    userName = displayName,
                    profilePictureUrl = photoUrl?.toString(),
                    email = email
                )
            } ?: throw Exception("User not authenticated")

            // Get existing refs from Firebase
            val existingRefsResource = calendarRealtimeDatabaseClient.getCalendarEventRefsFromUser(currentUser)
            var existingRefs = emptyList<CalendarEventRef>()

            existingRefsResource.collect { resource ->
                if (resource is Resource.Success) {
                    existingRefs = resource.data ?: emptyList()
                }
            }

            // Create a map of task IDs to refs
            val taskToRefMap = existingRefs
                .filterNot { it.isDeleted }
                .filter { it.taskId != null }
                .associateBy { it.taskId!! }

            // For each task, check if it exists in calendar
            for (task in tasks) {
                if (task.uuid !in taskToRefMap) {
                    // Task not synced to calendar yet, create event
                    createCalendarEvent(account, task).collect { resource ->
                        if (resource is Resource.Success && resource.data != null) {
                            syncedCount++
                        }
                    }
                }
            }

            emit(Resource.Success(syncedCount))
        } catch (e: Exception) {
            emit(Resource.Error(message = e.message ?: "Failed to sync tasks to calendar"))
        } finally {
            emit(Resource.Loading(false))
        }
    }

    /**
     * Synchronizes Google Calendar events with Firebase.
     * This will compare Google Calendar events with references in Firebase
     * and update accordingly.
     */
    suspend fun syncCalendarEvents(account: GoogleSignInAccount): Flow<Resource<List<CalendarEvent>>> = flow {
        emit(Resource.Loading(true))
        
        try {
            val currentUser = auth.currentUser?.run {
                UserData(
                    userId = uid,
                    userName = displayName,
                    profilePictureUrl = photoUrl?.toString(),
                    email = email
                )
            } ?: throw Exception("User not authenticated")
            
            // Get events from Google Calendar
            val googleEvents = getCalendarEvents(account)
            
            // Get existing references from Firebase
            val existingRefsResource = calendarRealtimeDatabaseClient.getCalendarEventRefsFromUser(currentUser)
            var existingRefs = emptyList<CalendarEventRef>()
            
            existingRefsResource.collect { resource ->
                if (resource is Resource.Success) {
                    existingRefs = resource.data ?: emptyList()
                }
            }
            
            // Create a map of existing refs by Google Event ID
            val existingRefsMap = existingRefs
                .filterNot { it.isDeleted }
                .associateBy { it.googleEventId }
            
            // For each Google event, check if it exists in Firebase
            for (event in googleEvents) {
                if (event.id !in existingRefsMap) {
                    // New event, create reference in Firebase
                    val newRef = CalendarEventRef(
                        googleEventId = event.id,
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                    calendarRealtimeDatabaseClient.insertCalendarEventRef(currentUser, newRef)
                }
            }
            
            emit(Resource.Success(googleEvents))
        } catch (e: Exception) {
            emit(Resource.Error(message = e.message ?: "Failed to sync calendar events"))
        } finally {
            emit(Resource.Loading(false))
        }
    }

    /**
     * Gets all calendar items (both Google events and tasks) for a specific date
     */
    suspend fun getCalendarItemsForDate(
        account: GoogleSignInAccount,
        tasks: List<Task>,
        date: LocalDate
    ): Flow<Resource<List<CalendarItem>>> = flow {
        emit(Resource.Loading(true))
        
        try {
            // Get Google Calendar events for the entire calendar
            val googleEvents = getCalendarEvents(account)
            
            // Get items by date
            val itemsByDate = getCalendarItemsByDate(googleEvents, tasks)
            
            // Try to find items using all possible date format strings, prioritizing European format
            val allDateFormats = listOf(
                date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")), // European (day first)
                date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), // ISO
                date.format(DateTimeFormatter.ofPattern("MM-dd-yyyy"))  // American (month first)
            )
            
            // Check each format to find any items
            var itemsForDate = emptyList<CalendarItem>()
            for (dateStr in allDateFormats) {
                val items = itemsByDate[dateStr] ?: emptyList()
                if (items.isNotEmpty()) {
                    itemsForDate = items
                    Log.d("CalendarRepo", "Found items using date format: $dateStr")
                    break
                }
            }
            
            // If still no results, check all tasks for matching dates
            if (itemsForDate.isEmpty()) {
                val matchingTasks = tasks.filter { task ->
                    if (task.deadlineDate.isNullOrEmpty()) {
                        false
                    } else {
                        try {
                            // Parse the task date and check if it matches
                            val taskDate = parseMultiFormatDate(task.deadlineDate!!)
                            taskDate == date
                        } catch (e: Exception) {
                            false
                        }
                    }
                }
                
                // Add any found tasks to the result
                if (matchingTasks.isNotEmpty()) {
                    val taskItems = matchingTasks.map { CalendarItem.TaskItem(it) }
                    itemsForDate = taskItems
                    Log.d("CalendarRepo", "Found matching tasks through direct date comparison: ${matchingTasks.size}")
                }
            }
            
            emit(Resource.Success(itemsForDate))
        } catch (e: Exception) {
            emit(Resource.Error(message = e.message ?: "Failed to get calendar items for date"))
        } finally {
            emit(Resource.Loading(false))
        }
    }

    private fun combineDateTime(dateString: String, timeString: String): Date {
        return try {
            val combinedStr = "$dateString $timeString"
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(combinedStr)
                ?: Date()
        } catch (e: Exception) {
            Date()
        }
    }

    private fun buildRecurrenceRule(task: Task): String {
        if (!task.isRecurring) return ""

        val recurrenceRule = StringBuilder("RRULE:")

        when (task.recurrencePattern?.uppercase()) {
            "DAILY" -> recurrenceRule.append("FREQ=DAILY")
            "WEEKLY" -> recurrenceRule.append("FREQ=WEEKLY")
            "MONTHLY" -> recurrenceRule.append("FREQ=MONTHLY")
            "YEARLY" -> recurrenceRule.append("FREQ=YEARLY")
            else -> return ""
        }

        // Add interval if specified
        if (task.recurrenceInterval != null && task.recurrenceInterval > 0) {
            recurrenceRule.append(";INTERVAL=${task.recurrenceInterval}")
        }

        // Add end date if specified
        if (!task.recurrenceEndDate.isNullOrEmpty()) {
            try {
                val endDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(task.recurrenceEndDate)
                if (endDate != null) {
                    val formattedEndDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(endDate)
                    recurrenceRule.append(";UNTIL=$formattedEndDate")
                }
            } catch (e: Exception) {
                // Ignore date parsing errors
            }
        }

        return recurrenceRule.toString()
    }
}