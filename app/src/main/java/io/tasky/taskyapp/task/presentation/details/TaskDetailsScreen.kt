package io.tasky.taskyapp.task.presentation.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import io.tasky.taskyapp.R
import io.tasky.taskyapp.core.presentation.widgets.DefaultFilledTextField
import io.tasky.taskyapp.core.presentation.widgets.DefaultTextButton
import io.tasky.taskyapp.core.util.createDatePickerDialog
import io.tasky.taskyapp.core.util.createTimePickerDialog
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.model.RecurrencePattern
import io.tasky.taskyapp.core.presentation.PremiumDialog
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@ExperimentalMaterial3Api
@ExperimentalComposeUiApi
@Composable
fun TaskDetailsScreen(
    navController: NavHostController,
    state: TaskDetailsState,
    onRequestInsert: (String, String, String, String, String, Int, Boolean, String?, Int, String?) -> Unit,
    onRequestUpdate: (String, String, String, String, String, Int, Boolean, String?, Int, String?) -> Unit,
    viewModel: TaskDetailsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val title = remember {
        mutableStateOf(state.task?.title ?: "")
    }

    val description = remember {
        mutableStateOf(state.task?.description ?: "")
    }

    val date = remember {
        mutableStateOf(state.task?.deadlineDate?.replace("-", "/") ?: "")
    }

    val time = remember {
        mutableStateOf(state.task?.deadlineTime ?: "")
    }

    val status = remember {
        mutableStateOf(state.task?.status ?: TaskStatus.PENDING.name)
    }

    val showStatusDropdown = remember { mutableStateOf(false) }

    // Keep track of whether this task is recurring - this is the single source of truth
    val isRecurring = remember { 
        mutableStateOf(false) 
    }

    val recurrencePattern = remember {
        mutableStateOf(state.task?.recurrencePattern ?: RecurrencePattern.DAILY.name)
    }

    val recurrenceInterval = remember {
        mutableStateOf(state.task?.recurrenceInterval ?: 1)
    }

    val recurrenceEndDate = remember {
        mutableStateOf(state.task?.recurrenceEndDate?.replace("-", "/") ?: "")
    }

    val priority = remember {
        mutableStateOf(state.task?.priority ?: 0)
    }

    var showPremiumDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.task?.uuid) {
        state.task?.let { task ->
            isRecurring.value = task.isRecurring
            recurrencePattern.value = task.recurrencePattern ?: RecurrencePattern.DAILY.name
            recurrenceInterval.value = task.recurrenceInterval
            recurrenceEndDate.value = task.recurrenceEndDate?.replace("-", "/") ?: ""
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                TaskDetailsViewModel.UiEvent.ShowPremiumDialog -> {
                    showPremiumDialog = true
                }
                TaskDetailsViewModel.UiEvent.Finish -> {
                    navController.popBackStack()
                }
                is TaskDetailsViewModel.UiEvent.ShowToast -> {
                    // Toast is handled in MainActivity
                }
            }
        }
    }

    val showRecurrencePatternDropdown = remember { mutableStateOf(false) }

    val recurrenceEndDatePicker = LocalContext.current.createDatePickerDialog(
        recurrenceEndDate,
        isSystemInDarkTheme()
    )

    LaunchedEffect(state.task) {
        state.task?.let { task ->
            title.value = task.title
            description.value = task.description ?: ""
            date.value = task.deadlineDate?.replace("-", "/") ?: ""
            time.value = task.deadlineTime ?: ""
            status.value = task.status

            isRecurring.value = task.isRecurring
            recurrencePattern.value = task.recurrencePattern ?: RecurrencePattern.DAILY.name
            recurrenceInterval.value = task.recurrenceInterval
            recurrenceEndDate.value = task.recurrenceEndDate?.replace("-", "/") ?: ""
        }
    }

    if (showPremiumDialog) {
        PremiumDialog(
            onDismiss = { showPremiumDialog = false },
            onUpgrade = {
                // You can trigger the upgrade flow here if needed
                showPremiumDialog = false
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text("Task Details") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Arrow Back",
                        )
                    }
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            DefaultFilledTextField(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = title,
                label = stringResource(R.string.title),
            )

            DefaultFilledTextField(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = description,
                isSingleLined = false,
                label = stringResource(R.string.description),
            )

            DateAndTimePickers(date, time)

            // Priority selection
            PrioritySelector(
                currentPriority = priority.value,
                suggestedPriority = state.suggestedPriority,
                isPriorityManuallySet = state.task?.isPriorityManuallySet ?: false,
                onPrioritySelected = { newPriority -> 
                    priority.value = newPriority 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Status selector
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status: ",
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = status.value,
                        modifier = Modifier.clickable { showStatusDropdown.value = true }
                    )

                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Select status",
                        modifier = Modifier.clickable { showStatusDropdown.value = true }
                    )

                    DropdownMenu(
                        expanded = showStatusDropdown.value,
                        onDismissRequest = { showStatusDropdown.value = false }
                    ) {
                        TaskStatus.values().forEach { taskStatus ->
                            DropdownMenuItem(
                                text = { Text(text = taskStatus.name) },
                                onClick = {
                                    status.value = taskStatus.name
                                    showStatusDropdown.value = false
                                }
                            )
                        }
                    }
                }
            }

            // Recurrence section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                // Make the entire card clickable to toggle recurring status
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Simply toggle our single source of truth
                            isRecurring.value = !isRecurring.value
                        }
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Recurring Task",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Create a custom toggle indicator with ON/OFF text
                        Card(
                            shape = RectangleShape,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isRecurring.value) 
                                                  MaterialTheme.colorScheme.primary
                                               else 
                                                  MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = if (isRecurring.value) "ON" else "OFF",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isRecurring.value)
                                          MaterialTheme.colorScheme.onPrimary
                                        else
                                          MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    
                    // Add an explanatory text that adapts to the current state
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isRecurring.value) 
                                 "This task will repeat based on your settings below"
                               else 
                                 "Task will occur only once (not recurring)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Recurrence options - only show if recurring is checked
            if (isRecurring.value) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Recurrence pattern dropdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Repeat: ")
                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = recurrencePattern.value,
                                modifier = Modifier.clickable { showRecurrencePatternDropdown.value = true }
                            )

                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Select pattern",
                                modifier = Modifier.clickable { showRecurrencePatternDropdown.value = true }
                            )

                            DropdownMenu(
                                expanded = showRecurrencePatternDropdown.value,
                                onDismissRequest = { showRecurrencePatternDropdown.value = false }
                            ) {
                                RecurrencePattern.values().forEach { pattern ->
                                    DropdownMenuItem(
                                        text = { Text(text = pattern.name) },
                                        onClick = {
                                            recurrencePattern.value = pattern.name
                                            showRecurrencePatternDropdown.value = false
                                        }
                                    )
                                }
                            }
                        }

                        // Interval
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(text = "Every ${recurrenceInterval.value} ${getIntervalText(recurrencePattern.value, recurrenceInterval.value)}")

                        Slider(
                            value = recurrenceInterval.value.toFloat(),
                            onValueChange = { recurrenceInterval.value = it.toInt() },
                            valueRange = 1f..30f,
                            steps = 29
                        )

                        // End date
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(text = "End date (optional):")

                        Spacer(modifier = Modifier.height(4.dp))

                        DefaultFilledTextField(
                            modifier = Modifier.fillMaxWidth(0.7f),
                            text = recurrenceEndDate,
                            isSingleLined = true,
                            isEnabled = false,
                            hasCloseButton = true,
                            label = "End date",
                            icon = {
                                Icon(
                                    imageVector = Icons.Filled.DateRange,
                                    modifier = Modifier.clickable {
                                        recurrenceEndDatePicker.show()
                                    },
                                    contentDescription = "endDateIcon"
                                )
                            },
                        )
                    }
                }
            }

            DefaultTextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 72.dp, vertical = 16.dp),
                text = stringResource(R.string.finish)
            ) {
                if (title.value.isBlank()) {
                    return@DefaultTextButton
                }
                
                if (state.task?.uuid != null) {
                    onRequestUpdate.invoke(
                        title.value.trim(),
                        description.value.trim(),
                        date.value.trim(),
                        time.value.trim(),
                        status.value,
                        priority.value,
                        isRecurring.value,  
                        if (isRecurring.value) recurrencePattern.value else null,
                        if (isRecurring.value) recurrenceInterval.value else 1,
                        if (isRecurring.value && recurrenceEndDate.value.isNotBlank()) recurrenceEndDate.value.trim() else null
                    )
                } else {
                    onRequestInsert.invoke(
                        title.value.trim(),
                        description.value.trim(),
                        date.value.trim(),
                        time.value.trim(),
                        status.value,
                        priority.value,
                        isRecurring.value,  
                        if (isRecurring.value) recurrencePattern.value else null,
                        if (isRecurring.value) recurrenceInterval.value else 1,
                        if (isRecurring.value && recurrenceEndDate.value.isNotBlank()) recurrenceEndDate.value.trim() else null
                    )
                }
            }
        }
    }
}

@ExperimentalMaterial3Api
@ExperimentalComposeUiApi
@Composable
private fun DateAndTimePickers(
    date: MutableState<String>,
    time: MutableState<String>,
) {
    val context = LocalContext.current
    
    // Set default values for date and time if empty
    if (date.value.isEmpty()) {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        date.value = dateFormat.format(Date())
    }
    
    if (time.value.isEmpty()) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        time.value = timeFormat.format(Date())
    }
    
    val datePicker = context.createDatePickerDialog(date, isSystemInDarkTheme())
    val timePicker = context.createTimePickerDialog(time, isSystemInDarkTheme())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        DefaultFilledTextField(
            modifier = Modifier.fillMaxWidth(0.55f),
            text = date,
            isSingleLined = false,
            isEnabled = false,
            hasCloseButton = true,
            label = stringResource(id = R.string.deadline),
            icon = {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    modifier = Modifier.clickable {
                        datePicker.show()
                    },
                    contentDescription = "dateIcon"
                )
            },
        )
        Spacer(modifier = Modifier.width(8.dp))
        DefaultFilledTextField(
            modifier = Modifier.fillMaxWidth(),
            text = time,
            isSingleLined = false,
            isEnabled = false,
            hasCloseButton = true,
            label = "",
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_clock),
                    modifier = Modifier.clickable {
                        timePicker.show()
                    },
                    contentDescription = "timeIcon"
                )
            },
        )
    }
}

// Helper function to provide the appropriate interval text
private fun getIntervalText(pattern: String?, interval: Int): String {
    if (pattern == null) return ""
    return when (pattern) {
        RecurrencePattern.DAILY.name -> if (interval == 1) "day" else "days"
        RecurrencePattern.WEEKLY.name -> if (interval == 1) "week" else "weeks"
        RecurrencePattern.MONTHLY.name -> if (interval == 1) "month" else "months"
        RecurrencePattern.YEARLY.name -> if (interval == 1) "year" else "years"
        else -> ""
    }
}

@Composable
fun PrioritySelector(
    currentPriority: Int,
    suggestedPriority: Int?,
    isPriorityManuallySet: Boolean,
    onPrioritySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // AI Suggested Priority (if available and not manually set)
            if (suggestedPriority != null && !isPriorityManuallySet) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                            .fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PriorityHigh,
                            contentDescription = "AI Priority",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Column {
                            Text(
                                text = "AI priority applied: ${when(suggestedPriority) {
                                    0 -> "Low"
                                    1 -> "Medium"
                                    else -> "High"
                                }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = "You can override it manually using the buttons below",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            
            // Show manual priority notice
            if (isPriorityManuallySet) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = Color(0xFFEADDFF), // Light purple background
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                            .fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PriorityHigh,
                            contentDescription = "Manual Priority",
                            tint = Color(0xFF6750A4), // Purple accent
                            modifier = Modifier.size(20.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Column {
                            Text(
                                text = "Manual priority selected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF6750A4) // Purple accent
                            )
                            
                            Text(
                                text = "Your manual selection will override AI suggestions",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6750A4).copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            
            // Priority Selection
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, top = if (suggestedPriority == null && !isPriorityManuallySet) 0.dp else 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PriorityHigh,
                    contentDescription = "Priority",
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "Select Priority Manually:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // High Priority Button
                PriorityButton(
                    text = "High",
                    isSelected = currentPriority == 2,
                    color = Color(0xFFFFB4AB), // Red for high priority
                    onClick = { onPrioritySelected(2) },
                    modifier = Modifier.weight(1f)
                )
                
                // Medium Priority Button
                PriorityButton(
                    text = "Medium",
                    isSelected = currentPriority == 1,
                    color = Color(0xFFFFD8A9), // Orange/amber for medium priority
                    onClick = { onPrioritySelected(1) },
                    modifier = Modifier.weight(1f)
                )
                
                // Low Priority Button
                PriorityButton(
                    text = "Low",
                    isSelected = currentPriority == 0,
                    color = Color(0xFFA9DFFF), // Blue for low priority
                    onClick = { onPrioritySelected(0) },
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Small note explaining priority persistence
            if (currentPriority > 0 && !isPriorityManuallySet) { // Only show for medium/high priorities that aren't already marked as manual
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your manual priority selection will be saved permanently",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun PriorityButton(
    text: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = if (isSelected) {
            modifier
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(2.dp)
        } else {
            modifier
        }
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.clickable { onClick() },
            colors = CardDefaults.cardColors(
                containerColor = color
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = Color.Black,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}