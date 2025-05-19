package io.tasky.taskyapp.task.presentation.listing

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.tasky.taskyapp.core.presentation.PremiumDialog
import io.tasky.taskyapp.task.domain.model.Task

@Composable
fun TaskScreen(
    viewModel: TaskViewModel = hiltViewModel(),
    onTaskClick: (Task) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Existing task list content
        // ... existing code ...

        // Show premium dialog when needed
        if (state.showPremiumDialog) {
            PremiumDialog(
                onDismiss = { viewModel.onPremiumDialogDismiss() },
                onUpgrade = { viewModel.onPremiumUpgrade(context as Activity) }
            )
        }

        // Show loading indicator
        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Show error message if any
        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        }
    }
} 