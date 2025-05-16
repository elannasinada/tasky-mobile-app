package io.tasky.taskyapp.task.domain.use_cases

import android.content.Context
import io.tasky.taskyapp.task.data.ml.TaskPriorityPredictor
import io.tasky.taskyapp.task.domain.model.Task
import io.tasky.taskyapp.task.domain.model.TaskStatus
import io.tasky.taskyapp.task.domain.model.TaskType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Use case for predicting task priority using the ML model.
 */
class PredictTaskPriorityUseCase @Inject constructor(
    private val context: Context
) {
    private val predictor by lazy { TaskPriorityPredictor(context) }
    
    /**
     * Predicts the priority for a given task using the TensorFlow Lite model.
     * 
     * @param task The task to analyze
     * @return The priority level (0=low, 1=medium, 2=high)
     */
    operator fun invoke(task: Task): Int {
        try {
            // Calculate days until deadline
            val daysUntilDeadline = calculateDaysUntilDeadline(task.deadlineDate)

            // Determine task type flags
            val taskTypeEnum = try {
                TaskType.valueOf(task.taskType)
            } catch (e: IllegalArgumentException) {
                // Default to HOME if type not found
                TaskType.HOME
            }

            val isTypeMeeting = taskTypeEnum == TaskType.BUSINESS
            val isTypePersonal = taskTypeEnum == TaskType.HOME || taskTypeEnum == TaskType.HOBBIES
            val isTypeWork = taskTypeEnum == TaskType.BUSINESS || taskTypeEnum == TaskType.STUDY

            // Determine task status flags
            val isStatusCompleted = task.status == TaskStatus.COMPLETED.name
            val isStatusInProgress = task.status == TaskStatus.IN_PROGRESS.name
            val isStatusPending = task.status == TaskStatus.PENDING.name

            // Use the ML model to predict priority
            return try {
                predictor.predictPriority(
                    daysUntilDeadline = daysUntilDeadline,
                    isTypeMeeting = isTypeMeeting,
                    isTypePersonal = isTypePersonal,
                    isTypeWork = isTypeWork,
                    isStatusCompleted = isStatusCompleted,
                    isStatusInProgress = isStatusInProgress,
                    isStatusPending = isStatusPending
                )
            } catch (e: Exception) {
                // Fallback to simple rules-based prediction if ML model fails
                predictPriorityWithRules(task, daysUntilDeadline.toInt())
            }
        } catch (e: Exception) {
            // Ultimate fallback
            return 1 // Medium priority as safe default
        }
    }

    /**
     * Simple rules-based fallback method for priority prediction.
     */
    private fun predictPriorityWithRules(task: Task, daysUntilDeadline: Int): Int {
        // Check for urgent keywords in title or description
        val text = "${task.title} ${task.description ?: ""}".lowercase()
        val urgentKeywords = listOf("urgent", "asap", "immediately", "today", "critical")
        val importantKeywords = listOf("important", "priority", "soon", "tomorrow")

        // Priority by keywords
        if (urgentKeywords.any { text.contains(it) }) {
            return 2 // High priority
        }

        if (importantKeywords.any { text.contains(it) }) {
            return 1 // Medium priority
        }

        // Priority by deadline
        return when {
            daysUntilDeadline <= 1 -> 2 // High priority if due today or tomorrow
            daysUntilDeadline <= 3 -> 1 // Medium priority if due within 3 days
            else -> 0 // Low priority
        }
    }

    /**
     * Calculates the number of days between today and the deadline date.
     */
    private fun calculateDaysUntilDeadline(deadlineDateString: String?): Float {
        if (deadlineDateString.isNullOrBlank()) {
            return 7.0f // Default to a week if no deadline
        }
        
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val deadlineDate = sdf.parse(deadlineDateString) ?: return 7.0f
            
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)
            
            val diff = deadlineDate.time - today.timeInMillis
            return (diff / (24 * 60 * 60 * 1000)).toFloat()
        } catch (e: Exception) {
            return 7.0f
        }
    }
}