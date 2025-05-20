package io.tasky.taskyapp.core.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.tasky.taskyapp.task.domain.model.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskPrioritizationScreen(
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskPrioritizationViewModel = hiltViewModel()
) {
    val prioritizedTasks by viewModel.prioritizedTasks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // Prioritize Button
        Button(
            onClick = { viewModel.prioritizeTasks(tasks) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            enabled = !isLoading && tasks.isNotEmpty()
        ) {
            Icon(
                imageVector = Icons.Default.PriorityHigh,
                contentDescription = "Prioritize Tasks"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Prioritize Tasks")
        }

        // Error Message
        error?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Main Content
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (prioritizedTasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No prioritized tasks yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Prioritized Task List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = prioritizedTasks,
                    key = { it.task.uuid }
                ) { prioritizedTask ->
                    PrioritizedTaskItem(
                        prioritizedTask = prioritizedTask,
                        onClick = { onTaskClick(prioritizedTask.task) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrioritizedTaskItem(
    prioritizedTask: PrioritizedTask,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = prioritizedTask.task.title,
                    style = MaterialTheme.typography.titleMedium
                )
                PriorityBadge(priority = prioritizedTask.priority)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = prioritizedTask.task.description ?: "",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Due: ${prioritizedTask.task.deadlineDate ?: "No due date"}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun PriorityBadge(
    priority: Int,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (priority) {
        5 -> MaterialTheme.colorScheme.error to "High"
        4 -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f) to "High"
        3 -> MaterialTheme.colorScheme.tertiary to "Medium"
        2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) to "Low"
        else -> MaterialTheme.colorScheme.primary to "Low"
    }

    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}