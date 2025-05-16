package io.tasky.taskyapp.calendar.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import io.tasky.taskyapp.calendar.data.CalendarEvent
import io.tasky.taskyapp.calendar.data.CalendarItem
import io.tasky.taskyapp.calendar.data.DateEvents
import io.tasky.taskyapp.sign_in.domain.model.UserData
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import kotlinx.coroutines.flow.collectLatest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: androidx.navigation.NavController,
    userData: UserData?,
    viewModel: CalendarViewModel = hiltViewModel(),
    onGoogleSignInClick: () -> Unit,
    onRequestGoogleSignIn: (options: GoogleSignInOptions) -> Unit,
    tasks: List<Task> = emptyList()
) {
    val state = viewModel.state
    
    // Remember the currently visible month (default to current month)
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    
    // Load events when tasks change
    LaunchedEffect(tasks) {
        viewModel.loadCalendarEvents(tasks)
    }
    
    LaunchedEffect(key1 = true) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is CalendarViewModel.UiEvent.ShowToast -> {
                    // Toast is shown via viewModel
                }
                is CalendarViewModel.UiEvent.NavigateToGoogleSignIn -> {
                    onGoogleSignInClick()
                }
                is CalendarViewModel.UiEvent.RequestGoogleSignIn -> {
                    onRequestGoogleSignIn(event.options)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Calendar") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    viewModel.showToast("Syncing calendar and tasks...")
                    // Combined refresh - syncs calendar events and tasks in one operation
                    viewModel.refreshCalendarAndTasks(tasks)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Calendar"
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading your calendar...",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (state.showGoogleSignInPrompt) {
                GoogleSignInPrompt(
                    onSignInClick = onGoogleSignInClick,
                    modifier = Modifier.align(Alignment.Center),
                    isEmailUser = userData != null
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Month selector
                    MonthSelector(
                        currentMonth = currentMonth,
                        onPreviousMonth = { currentMonth = currentMonth.minusMonths(1) },
                        onNextMonth = { currentMonth = currentMonth.plusMonths(1) }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Calendar days grid
                    CalendarGrid(
                        month = currentMonth,
                        dateEvents = state.dateEvents,
                        selectedDate = state.selectedDate,
                        onDateSelected = { viewModel.selectDate(it) }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Legend
                    CalendarLegend()
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Loading indicator during sync
                    if (state.isSyncing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.padding(8.dp))
                            Text("Syncing calendar...", style = MaterialTheme.typography.bodyMedium)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // Selected date header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Events for ${formatDateSafely(state.selectedDate)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Show account email if available
                        userData?.email?.let {
                            Text(
                                text = "Account: ${it.substringBefore('@')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Events list for selected date
                    if (state.selectedDateItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No events for this date",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Button(
                                    onClick = { 
                                        viewModel.showToast("Syncing calendar and tasks...")
                                        // Combined refresh - syncs calendar events and tasks in one operation
                                        viewModel.refreshCalendarAndTasks(tasks)
                                    }
                                ) {
                                    Text("Refresh Calendar")
                                }
                            }
                        }
                    } else {
                        LazyColumn {
                            item {
                                if (state.calendarEvents.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No Google Calendar events found.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            
                            items(state.selectedDateItems) { item ->
                                when (item) {
                                    is CalendarItem.GoogleEventItem -> GoogleEventListItem(item.event)
                                    is CalendarItem.TaskItem -> TaskListItem(item.task)
                                }
                            }
                        }
                    }
                }
            }
            
            if (state.error != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(onClick = { viewModel.resetState() }) {
                            Text("EMERGENCY REFRESH")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthSelector(
    currentMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Previous Month"
            )
        }
        
        Text(
            text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleLarge
        )
        
        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Next Month"
            )
        }
    }
}

@Composable
fun CalendarGrid(
    month: YearMonth,
    dateEvents: List<DateEvents>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    // Create map of dates to their DateEvents object
    val eventsMap = remember(dateEvents) {
        dateEvents.associateBy { it.date }
    }
    
    Column {
        // Day of week headers
        Row(modifier = Modifier.fillMaxWidth()) {
            for (dayOfWeek in DayOfWeek.values()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        // Start with the first day of the month
        val firstDayOfMonth = month.atDay(1)
        
        // Determine the starting position (which day of week is the 1st)
        val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7
        
        // Get the total number of days in the month
        val numDays = month.lengthOfMonth()
        
        // Calculate the number of calendar cells we need (including blanks)
        val numCells = (numDays + firstDayOfWeek + 6) / 7 * 7
        
        // Create a grid of dates
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            userScrollEnabled = false,
            modifier = Modifier
                .height((numCells / 7 * 50).dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.Center
        ) {
            // Empty cells before the first day
            items(firstDayOfWeek) {
                Box(modifier = Modifier.aspectRatio(1f))
            }
            
            // Days of the month
            items(numDays) { day ->
                val date = month.atDay(day + 1)
                val isSelected = date == selectedDate
                val dateEvent = eventsMap[date]
                
                CalendarDay(
                    date = date,
                    isSelected = isSelected,
                    hasEvents = dateEvent != null && dateEvent.items.isNotEmpty(),
                    dateEvent = dateEvent,
                    onClick = { onDateSelected(date) }
                )
            }
            
            // Remaining empty cells to fill the grid
            val remainingCells = numCells - (firstDayOfWeek + numDays)
            items(remainingCells) {
                Box(modifier = Modifier.aspectRatio(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDay(
    date: LocalDate,
    isSelected: Boolean,
    hasEvents: Boolean,
    dateEvent: DateEvents?,
    onClick: () -> Unit
) {
    val today = LocalDate.now()
    val isToday = date == today
    
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Status indicators on top
        if (dateEvent != null && dateEvent.items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (dateEvent.googleEventCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color.Red, CircleShape)
                    )
                }
                if (dateEvent.pendingCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
                if (dateEvent.inProgressCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.secondary, CircleShape)
                    )
                }
                if (dateEvent.completedCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                    )
                }
                if (dateEvent.cancelledCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color.Gray, CircleShape)
                    )
                }
            }
        }
        
        // Day circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        isToday && !isSelected -> MaterialTheme.colorScheme.surfaceVariant
                        else -> Color.Transparent
                    }
                )
                .border(
                    width = if (isToday) 2.dp else 0.dp,
                    color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = CircleShape
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isSelected -> Color.White
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onBackground
                }
            )
        }
    }
}

@Composable
fun GoogleSignInPrompt(
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEmailUser: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isEmailUser) 
                "Connect Your Google Calendar" 
            else 
                "Sign in with Google to access your calendar events",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Tasky needs access to your Google Calendar to display your upcoming events",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Main Connect Button - this should be the primary action
        Button(
            onClick = onSignInClick,
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Text(
                    text = "Connect with Google Calendar",
                    modifier = Modifier.padding(vertical = 8.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Troubleshooting Tips:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "• If the above button doesn't work, try tapping the Refresh button\n" +
                           "• Make sure you have Google Play Services updated\n" +
                           "• Try selecting a different Google account\n" + 
                           "• Ensure you have a stable internet connection",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun GoogleEventListItem(event: CalendarEvent) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (event.id.startsWith("error")) 
                MaterialTheme.colorScheme.errorContainer
            else if (event.id.startsWith("test")) 
                MaterialTheme.colorScheme.tertiaryContainer
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator for Google event
            Box(
                modifier = Modifier
                    .size(16.dp, height = 48.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Time: ${formatTime(event.startTime)} - ${formatTime(event.endTime)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (event.location.isNotEmpty()) {
                    Text(
                        text = "Location: ${event.location}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                if (event.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun TaskListItem(task: Task) {
    // Set colors based on task status
    val statusColor = when (task.status) {
        TaskStatus.COMPLETED.name -> MaterialTheme.colorScheme.tertiary
        TaskStatus.CANCELLED.name -> Color.Gray
        TaskStatus.IN_PROGRESS.name -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary // PENDING
    }
    
    val containerColor = when (task.status) {
        TaskStatus.COMPLETED.name -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        TaskStatus.CANCELLED.name -> Color.LightGray.copy(alpha = 0.7f)
        TaskStatus.IN_PROGRESS.name -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) // PENDING
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(16.dp, height = 48.dp)
                    .background(statusColor)
            )
            
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Due: ${task.deadlineDate ?: "No date"} ${task.deadlineTime ?: ""}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                val description = task.description
                if (description != null && description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    if (description.contains("⚠️ CONFLICT")) {
                        // Special formatting for conflict messages
                        val parts = description.split("⚠️ CONFLICT")
                        
                        // Show normal description part
                        if (parts[0].isNotEmpty()) {
                            Text(
                                text = parts[0].trim(),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        
                        // Show conflict warning with highlight
                        Text(
                            text = "⚠️ CONFLICT" + parts[1],
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8B0000), // Dark red
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        // Normal description
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                if (task.isRecurring) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Recurring: ${task.recurrencePattern ?: ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                // Show task status
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, shape = CircleShape)
                    )
                    Text(
                        text = task.status.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarLegend() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            text = "Legend:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendItem(color = Color.Red, text = "Google Events")
            LegendItem(color = MaterialTheme.colorScheme.primary, text = "Pending")
            LegendItem(color = MaterialTheme.colorScheme.secondary, text = "In Progress")
            LegendItem(color = MaterialTheme.colorScheme.tertiary, text = "Completed")
            LegendItem(color = Color.Gray, text = "Cancelled")
        }
    }
}

@Composable
fun LegendItem(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp
        )
    }
}

// Helper function to format time (this is a simple placeholder)
private fun formatTime(timestamp: String): String {
    return try {
        val time = timestamp.toLongOrNull()
        if (time != null) {
            java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(time))
        } else {
            timestamp
        }
    } catch (e: Exception) {
        timestamp
    }
}

// Helper function to safely format a date
private fun formatDateSafely(date: LocalDate): String {
    return try {
        date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (e: Exception) {
        "Selected Date"
    }
}