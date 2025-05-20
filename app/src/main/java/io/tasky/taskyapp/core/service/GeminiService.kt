package io.tasky.taskyapp.core.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import io.tasky.taskyapp.task.domain.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

// Request and response models for Gemini API
data class GeminiRequest(
    val contents: List<Content>,
    @SerializedName("generation_config") val generationConfig: GenerationConfig = GenerationConfig()
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class GenerationConfig(
    val temperature: Float = 0.7f,
    @SerializedName("max_output_tokens") val maxOutputTokens: Int = 1024
)

data class GeminiResponse(
    val candidates: List<Candidate>? = null,
    val promptFeedback: PromptFeedback? = null
)

data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null
)

data class PromptFeedback(
    val blockReason: String? = null
)

@Singleton
class GeminiService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val TAG = "GeminiService"
    private val API_KEY = "xxxxxxxxxxxxx" // Replace with your actual API key
    private val BASE_URL = "https://generativelanguage.googleapis.com/v1/models/gemini-pro:generateContent"
    
    private suspend fun callGemini(request: GeminiRequest): GeminiResponse? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL?key=$API_KEY")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            // Write request body
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(gson.toJson(request))
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = StringBuilder()
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                }
                
                return@withContext gson.fromJson(response.toString(), GeminiResponse::class.java)
            } else {
                Log.e(TAG, "HTTP error: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API: ${e.message}")
            null
        }
    }

    suspend fun orderTasksByPriority(tasks: List<Task>): List<Task> = withContext(Dispatchers.IO) {
        try {
            if (tasks.isEmpty()) return@withContext tasks

            // Create a prompt for Gemini to prioritize tasks
            val tasksJson = gson.toJson(tasks)
            val prompt = """
                Analyze these tasks and return them ordered by priority (highest to lowest).
                Consider deadlines, importance, and complexity.
                Tasks data: $tasksJson
                
                Return ONLY a JSON array with the task UUIDs in order of priority.
                Format: ["uuid1", "uuid2", "uuid3", ...]
            """.trimIndent()
            
            // Call Gemini API
            val request = GeminiRequest(
                contents = listOf(
                    Content(parts = listOf(Part(text = prompt)))
                )
            )
            
            val response = callGemini(request)
            val responseText = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            
            // Parse the response
            try {
                if (responseText != null) {
                    val orderedUuids = gson.fromJson(responseText.replace("```json", "").replace("```", "").trim(), Array<String>::class.java).toList()
                    val taskMap = tasks.associateBy { it.uuid }
                    
                    // Return tasks in the order specified by Gemini
                    return@withContext orderedUuids.mapNotNull { uuid -> taskMap[uuid] }
                        .plus(tasks.filter { it.uuid !in orderedUuids }) // Add any tasks that weren't in the response
                }
                return@withContext tasks
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing Gemini response: ${e.message}")
                return@withContext tasks
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error prioritizing tasks: ${e.message}")
            tasks
        }
    }

    suspend fun suggestTaskPriority(task: Task): Int {
        return try {
            val taskJson = gson.toJson(task)
            val prompt = """
                Analyze this task and suggest a priority level from 1 to 5 (where 5 is highest priority).
                Consider deadline, description, and type.
                Task data: $taskJson
                
                Return ONLY a number from 1 to 5.
            """.trimIndent()
            
            val request = GeminiRequest(
                contents = listOf(
                    Content(parts = listOf(Part(text = prompt)))
                )
            )
            
            val response = callGemini(request)
            val responseText = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            responseText?.trim()?.toIntOrNull()?.coerceIn(1, 5) ?: 3
            
        } catch (e: Exception) {
            Log.e(TAG, "Error suggesting task priority: ${e.message}")
            3 // Default to medium priority
        }
    }

    suspend fun analyzeTaskDependencies(task: Task, existingTasks: List<Task>): String {
        return try {
            val taskJson = gson.toJson(task)
            val existingTasksJson = gson.toJson(existingTasks)
            val prompt = """
                Analyze potential dependencies between this task and existing tasks.
                Identify tasks that might be prerequisites or related to the current task.
                Current task: $taskJson
                Existing tasks: $existingTasksJson
                
                Return a concise summary of dependencies found. If no dependencies, say "No dependencies found".
            """.trimIndent()
            
            val request = GeminiRequest(
                contents = listOf(
                    Content(parts = listOf(Part(text = prompt)))
                )
            )
            
            val response = callGemini(request)
            val responseText = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            responseText?.trim() ?: "No dependencies found"
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing task dependencies: ${e.message}")
            "Error analyzing dependencies"
        }
    }

    suspend fun getTaskInsights(task: Task): String {
        return try {
            // First generate insights based on our existing rules
            val insights = mutableListOf<String>()
            
            // Analyze deadline
            task.deadlineDate?.let { date ->
                val daysUntilDeadline = calculateDaysUntilDeadline(date)
                when {
                    daysUntilDeadline < 0 -> insights.add("Task is overdue")
                    daysUntilDeadline == 0f -> insights.add("Task is due today")
                    daysUntilDeadline <= 1f -> insights.add("Task is due tomorrow")
                    daysUntilDeadline <= 3f -> insights.add("Task is due soon")
                else ->{
                    insights.add("Task is due in $daysUntilDeadline days")}
                }
            }
            
            // Analyze task type
            when (task.taskType) {
                "BUSINESS" -> insights.add("Business task - may require preparation")
                "STUDY" -> insights.add("Study task - consider breaking into smaller parts")
                "HOME" -> insights.add("Home task - can be done in free time")
                else -> insights.add("Personal task - prioritize based on your schedule")
            }
            
            // Analyze status
            when (task.status) {
                "PENDING" -> insights.add("Task is pending - consider starting soon")
                "IN_PROGRESS" -> insights.add("Task is in progress - keep momentum")
                "COMPLETED" -> insights.add("Task is completed - great job!")
                "CANCELLED" -> insights.add("Task was cancelled")
                else -> insights.add("Task status: ${task.status}")
            }

            // Get additional insights from Gemini
            val taskJson = gson.toJson(task)
            val prompt = """
                Analyze this task and provide 1-2 brief, actionable insights.
                Consider deadline, priority, type, and status.
                Task data: $taskJson
                
                Return ONLY 1-2 brief insights, each no more than 10 words. 
                Do not include labels or prefixes.
            """.trimIndent()
            
            try {
                val request = GeminiRequest(
                    contents = listOf(
                        Content(parts = listOf(Part(text = prompt)))
                    )
                )
                
                val response = callGemini(request)
                val responseText = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                responseText?.let {
                    val geminiInsights = it.split("\n")
                        .filter { line -> line.isNotBlank() }
                        .take(2)
                    insights.addAll(geminiInsights)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting additional insights from Gemini: ${e.message}")
            }
            
            if (insights.isEmpty()) {
                "No specific insights available"
            } else {
                insights.joinToString(". ")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting task insights: ${e.message}")
            "Error getting insights"
        }
    }

    private fun calculateDaysUntilDeadline(deadlineDate: String): Float {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val deadline = sdf.parse(deadlineDate) ?: return 0f
            
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)
            
            val diff = deadline.time - today.timeInMillis
            (diff / (24 * 60 * 60 * 1000)).toFloat()
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating days until deadline: ${e.message}")
            0f
        }
    }
}