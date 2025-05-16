package io.tasky.taskyapp.task.presentation.widgets

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tasky.taskyapp.R
import io.tasky.taskyapp.core.presentation.widgets.DefaultIconButton
import io.tasky.taskyapp.core.presentation.widgets.DefaultTextButton
import io.tasky.taskyapp.core.util.shimmerAnimation
import io.tasky.taskyapp.task.domain.model.TaskType
import io.tasky.taskyapp.ui.theme.Blue70
import io.tasky.taskyapp.ui.theme.Purple80

@Composable
fun TaskShimmerCard(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Shimmer for title
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(24.dp)
                        .shimmerAnimation(),
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                ) {}
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Shimmer for date
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(16.dp)
                        .shimmerAnimation(),
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                ) {}
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Shimmer for status chip
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(20.dp)
                        .shimmerAnimation(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                ) {}
            }
        }
    }
}

@Composable
fun NewTaskBottomSheetContent(
    selectedTaskType: MutableState<String>,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.choose_an_activity),
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = Color.DarkGray
        )

        LazyVerticalGrid(
            modifier = Modifier.padding(24.dp),
            columns = GridCells.Adaptive(88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(TaskType.values()) {
                IconButtonTextWidget(
                    taskType = it,
                    selectedTaskType = selectedTaskType
                )
            }
        }

        DefaultTextButton(
            modifier = Modifier.width(200.dp),
            text = stringResource(id = R.string.create_new_task),
            enabled = selectedTaskType.value.isNotEmpty(),
            onClick = onClick
        )
    }
}

@Composable
fun IconButtonTextWidget(
    taskType: TaskType,
    selectedTaskType: MutableState<String>,
) {
    Column(
        modifier = Modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DefaultIconButton(
            modifier = Modifier.takeIf {
                taskType.name == selectedTaskType.value
            }?.border(
                width = 2.dp,
                shape = RoundedCornerShape(16.dp),
                brush = Brush.horizontalGradient(
                    listOf(
                        Blue70,
                        Purple80,
                        Blue70
                    )
                )
            ) ?: Modifier.size(48.dp),
            iconModifier = Modifier.fillMaxSize(0.7f),
            painter = painterResource(id = taskType.painterId),
            contentDescription = "${taskType.name}Icon",
            shape = RoundedCornerShape(16.dp),
            onClick = {
                selectedTaskType.value = taskType.name
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = taskType.titleId),
//            color = MaterialTheme.colorScheme.onPrimaryContainer
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            textAlign = TextAlign.Center
        )
    }
}
