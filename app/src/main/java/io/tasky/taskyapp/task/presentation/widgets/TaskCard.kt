package io.tasky.taskyapp.task.presentation.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tasky.taskyapp.core.presentation.widgets.DefaultIcon
import io.tasky.taskyapp.core.presentation.widgets.SwipeableCard
import io.tasky.taskyapp.task.domain.model.RecurrencePattern
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.model.TaskType

@ExperimentalMaterial3Api
@Composable
fun TaskCard(
    task: Task,
    onClickCard: () -> Unit,
    onClickDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    SwipeableCard(
        onClickSwiped = onClickDelete,
        swipedContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.error)
                )
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        },
        onClickCard = onClickCard,
        cardContent = {
            TaskCardContent(task)
        }
    )
}

@ExperimentalMaterial3Api
@Composable
private fun TaskCardContent(task: Task) {
    val showDescription = remember {
        mutableStateOf(false)
    }

    val isCompleted = task.status == TaskStatus.COMPLETED.name
    val isCancelled = task.status == TaskStatus.CANCELLED.name
    val cardBackgroundColor = when {
        isCompleted -> Color(0xFF90EE90).copy(alpha = 0.4f)  // Light green background for completed
        isCancelled -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)  // Light red background for cancelled
        task.status == TaskStatus.IN_PROGRESS.name -> Color(0xFFADD8E6).copy(alpha = 0.4f)  // Light blue for in progress
        task.status == TaskStatus.PENDING.name -> Color(0xFFFFD580).copy(alpha = 0.4f)  // Light orange/yellow for pending
        else -> MaterialTheme.colorScheme.surface
    }

    val textColor = when {
        isCompleted -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        isCancelled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val statusChipColor = when(task.status) {
        TaskStatus.PENDING.name -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
        TaskStatus.IN_PROGRESS.name -> Color(0xFF1E90FF).copy(alpha = 0.7f)
        TaskStatus.COMPLETED.name -> Color(0xFF006400).copy(alpha = 0.7f)
        TaskStatus.CANCELLED.name -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(0.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = cardBackgroundColor
            )
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    DefaultIcon(
                        modifier = Modifier.size(48.dp),
                        iconModifier = Modifier.fillMaxSize(0.7F),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.background.copy(0.9F),
                        painter = painterResource(
                            id = TaskType.valueOf(task.taskType).painterId
                        ),
                        contentDescription = TaskType.valueOf(task.taskType).name,
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(0.85F)
                    ) {
                        Text(
                            text = task.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            textDecoration = if (isCompleted || isCancelled) TextDecoration.LineThrough else TextDecoration.None,
                            color = textColor
                        )
                        Text(
                            text = "${task.deadlineDate?.replace("-", "/")}" +
                                    " ${task.deadlineTime}",
                            fontSize = 12.sp,
                            color = textColor
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Status chip
                        Card(
                            shape = RoundedCornerShape(50),
                            colors = CardDefaults.cardColors(
                                containerColor = statusChipColor
                            )
                        ) {
                            Text(
                                text = task.status,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Priority indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (task.priority) {
                                2 -> {
                                    Card(
                                        modifier = Modifier.size(8.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.Red)
                                    ) {}
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "High priority",
                                        fontSize = 10.sp,
                                        color = Color.Red
                                    )
                                }
                                1 -> {
                                    Card(
                                        modifier = Modifier.size(8.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA500)) // Orange
                                    ) {}
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Medium priority",
                                        fontSize = 10.sp,
                                        color = Color(0xFFFFA500) // Orange
                                    )
                                }
                                else -> {
                                    Card(
                                        modifier = Modifier.size(8.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.Green)
                                    ) {}
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Low priority",
                                        fontSize = 10.sp,
                                        color = Color.Green
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (task.isRecurring) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Recurring",
                                    modifier = Modifier.size(12.dp),
                                    tint = if (isCompleted) Color(0xFF006400) else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Recurring: ${getRecurrenceText(task)}",
                                    fontSize = 10.sp,
                                    color = textColor
                                )
                            }
                        }
                    }

                    task.description?.takeIf {
                        it.isNotBlank()
                    }?.let {
                        IconButton(
                            onClick = {
                                showDescription.value = showDescription.value.not()
                            }
                        ) {
                            Icon(
                                imageVector = if (showDescription.value)
                                    Icons.Default.KeyboardArrowDown
                                else Icons.Default.KeyboardArrowRight,
                                contentDescription = "showDescription",
                                tint = textColor
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showDescription.value) {
                    Text(
                        text = task.description ?: "",
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 12.dp),
                        fontSize = 16.sp,
                        textDecoration = TextDecoration.None,
                        color = textColor
                    )
                }
            }
        }
    }
}

/**
 * Returns a readable string representation of a task's recurrence pattern
 */
private fun getRecurrenceText(task: Task): String {
    return when (task.recurrencePattern) {
        RecurrencePattern.DAILY.name -> "Daily"
        RecurrencePattern.WEEKLY.name -> "Weekly"
        RecurrencePattern.MONTHLY.name -> "Monthly"
        RecurrencePattern.YEARLY.name -> "Yearly"
        else -> "Unknown"
    }
}