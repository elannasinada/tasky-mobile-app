package io.tasky.taskyapp.task.domain.use_cases

import io.tasky.taskyapp.core.service.GeminiService
import io.tasky.taskyapp.task.domain.model.Task
import javax.inject.Inject

/**
 * Use case for predicting task priority using the Gemini AI.
 * This uses Google's Gemini API for advanced task analysis.
 */
class GeminiPriorityUseCase @Inject constructor(
    private val geminiService: GeminiService
) {
    /**
     * Predicts the priority for a given task using Gemini AI.
     * 
     * @param task The task to analyze
     * @return The priority level (0=low, 1=medium, 2=high)
     */
    suspend operator fun invoke(task: Task): Int {
        return geminiService.suggestTaskPriority(task)
    }
    
    /**
     * Analyzes task dependencies with existing tasks
     */
    suspend fun analyzeTaskDependencies(task: Task, existingTasks: List<Task>): String {
        return geminiService.analyzeTaskDependencies(task, existingTasks)
    }
    
    /**
     * Gets general insights about a task
     */
    suspend fun getTaskInsights(task: Task): String {
        return geminiService.getTaskInsights(task)
    }
    
    /**
     * Orders tasks by priority based on AI analysis
     */
    suspend fun orderTasksByPriority(tasks: List<Task>): List<Task> {
        return geminiService.orderTasksByPriority(tasks)
    }
}