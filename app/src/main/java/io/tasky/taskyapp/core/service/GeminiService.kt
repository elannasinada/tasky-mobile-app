package io.tasky.taskyapp.core.service

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import io.tasky.taskyapp.task.domain.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import io.tasky.taskyapp.BuildConfig

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

    private val API_KEY = BuildConfig.GEMINI_API_KEY
    private val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
    private val FALLBACK_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-vision:generateContent"

    private var lastRequestTime = 0L
    private val minRequestInterval = 3000L
    private var requestsInLastMinute = 0
    private var lastMinuteResetTime = 0L
    private val maxRequestsPerMinute = 15

    private suspend fun callGemini(request: GeminiRequest): GeminiResponse? = withContext(Dispatchers.IO) {
        val modelEndpoints = listOf(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent",
            "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent",
            "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-pro:generateContent"
        )

        try {
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastMinuteResetTime > 60000) {
                requestsInLastMinute = 0
                lastMinuteResetTime = currentTime
            }

            if (requestsInLastMinute >= maxRequestsPerMinute) {
                Log.w(TAG, "Rate limit exceeded: waiting until next minute")
                delay(60000 - (currentTime - lastMinuteResetTime) + 1000)
                return@withContext callGemini(request)
            }

            val timeSinceLastRequest = currentTime - lastRequestTime
            if (timeSinceLastRequest < minRequestInterval) {
                delay(minRequestInterval - timeSinceLastRequest)
            }

            lastRequestTime = System.currentTimeMillis()
            requestsInLastMinute++

            for (endpoint in modelEndpoints) {
                try {
                    Log.d(TAG, "Trying Gemini endpoint: $endpoint")
                    val url = URL("$endpoint?key=$API_KEY")

                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.doOutput = true

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

                        Log.d(TAG, "Successfully used Gemini endpoint: $endpoint")
                        return@withContext gson.fromJson(response.toString(), GeminiResponse::class.java)
                    } else if (responseCode == 429) {
                        var retryDelay = 2000L
                        try {
                            val errorResponse = StringBuilder()
                            BufferedReader(InputStreamReader(connection.errorStream ?: return@withContext null)).use { reader ->
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    errorResponse.append(line)
                                }
                            }

                            val errorJson = errorResponse.toString()
                            if (errorJson.contains("retryDelay")) {
                                val retryDelayString = errorJson.substringAfter("retryDelay\": \"").substringBefore("s\"")
                                retryDelay = (retryDelayString.toIntOrNull() ?: 2) * 1000L
                                Log.d(TAG, "Using retry delay from response: $retryDelay ms")
                            }

                            Log.e(TAG, "Rate limit error: $errorResponse")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing retry delay: ${e.message}")
                        }

                        delay(retryDelay)
                    } else {
                        val errorResponse = StringBuilder()
                        BufferedReader(InputStreamReader(connection.errorStream ?: return@withContext null)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                errorResponse.append(line)
                            }
                        }
                        Log.e(TAG, "HTTP error with endpoint $endpoint: $responseCode - $errorResponse")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error with endpoint $endpoint: ${e.message}")
                }

                delay(1000)
            }

            Log.e(TAG, "All Gemini endpoints failed")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API: ${e.message}")
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun prioritizeTasks(tasks: List<Task>): List<Task> {
        Log.i(TAG, "Using local prioritization algorithm for ${tasks.size} tasks")

        return tasks.sortedWith(compareByDescending { task ->
            var score = 0.0

            task.deadlineDate?.let { date ->
                val daysUntilDeadline = calculateDaysUntilDeadline(date)
                score += when {
                    daysUntilDeadline < 0 -> 100.0  // Overdue tasks are highest priority
                    daysUntilDeadline == 0f -> 90.0 // Due today
                    daysUntilDeadline <= 1f -> 80.0 // Due tomorrow
                    daysUntilDeadline <= 3f -> 70.0 // Due within 3 days
                    daysUntilDeadline <= 7f -> 60.0 // Due within a week
                    daysUntilDeadline <= 14f -> 50.0 // Due within two weeks
                    else -> 40.0 // Due later
                }
            } ?: run {
                score += 30.0 // No deadline - lower priority
            }

            score += when (task.status) {
                "IN_PROGRESS" -> 20.0 // Already started tasks
                "PENDING" -> 15.0     // Not started yet
                "COMPLETED" -> -50.0  // Completed tasks at bottom
                "CANCELLED" -> -100.0 // Cancelled tasks at very bottom
                else -> 0.0
            }

            score += task.priority * 10.0

            score += when (task.taskType) {
                "BUSINESS" -> 5.0
                "STUDY" -> 4.0
                "HOME" -> 3.0
                else -> 2.0 // Personal or other
            }

            score
        })
    }

    suspend fun orderTasksByPriority(tasks: List<Task>): List<Task> = withContext(Dispatchers.IO) {
        try {
            if (tasks.isEmpty()) return@withContext tasks

            if (tasks.size > 10) {
                Log.i(TAG, "Using fallback prioritization method for large task list: ${tasks.size} tasks")
                return@withContext prioritizeTasks(tasks)
            }

            val simplifiedTasks = tasks.map { task ->
                mapOf(
                    "uuid" to task.uuid,
                    "title" to task.title,
                    "deadlineDate" to task.deadlineDate,
                    "status" to task.status,
                    "priority" to task.priority
                )
            }
            val tasksJson = gson.toJson(simplifiedTasks)
            val prompt = """
                Order these tasks by priority (highest to lowest).
                Consider deadlines, status, and priority.
                Tasks: $tasksJson
                
                Return ONLY a JSON array with the task UUIDs in order of priority.
                Format: ["uuid1", "uuid2", "uuid3", ...]
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(
                    Content(parts = listOf(Part(text = prompt)))
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.2f,
                    maxOutputTokens = 200
                )
            )

            val response = callGemini(request)
            val responseText = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            try {
                if (responseText != null) {
                    val cleanedResponse = responseText
                        .replace("```json", "")
                        .replace("```", "")
                        .trim()

                    val orderedUuids = gson.fromJson(cleanedResponse, Array<String>::class.java).toList()
                    val taskMap = tasks.associateBy { it.uuid }

                    return@withContext orderedUuids.mapNotNull { uuid -> taskMap[uuid] }
                        .plus(tasks.filter { it.uuid !in orderedUuids })
                }

                Log.i(TAG, "Using fallback prioritization method")
                return@withContext prioritizeTasks(tasks)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing Gemini response: ${e.message}")
                return@withContext prioritizeTasks(tasks)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error prioritizing tasks: ${e.message}")
            return@withContext prioritizeTasks(tasks)
        }
    }

    suspend fun suggestTaskPriority(task: Task): Int {
        return try {
            val taskJson = gson.toJson(task)
            val prompt = """
                Analyze this task and suggest a priority level from 0 to 2, where:
                0 = Low Priority
                1 = Medium Priority
                2 = High Priority
                
                Consider deadline, description, and type.
                Task data: $taskJson
                
                Return ONLY a single number: 0, 1, or 2.
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(
                    Content(parts = listOf(Part(text = prompt)))
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.1f,
                    maxOutputTokens = 10
                )
            )

            val response = callGemini(request)
            val responseText = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            val priority = responseText?.trim()?.toIntOrNull()?.coerceIn(0, 2)

            if (priority != null) {
                return priority
            } else {
                return suggestPriorityFallback(task)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error suggesting task priority: ${e.message}")
            suggestPriorityFallback(task)
        }
    }

    private fun suggestPriorityFallback(task: Task): Int {
        Log.i(TAG, "Using local priority suggestion algorithm for task: ${task.title}")

        var priorityScore = 0.0

        task.deadlineDate?.let { date ->
            val daysUntilDeadline = calculateDaysUntilDeadline(date)
            priorityScore += when {
                daysUntilDeadline < 0 -> 2.0  // Overdue
                daysUntilDeadline <= 1 -> 1.5 // Due very soon
                daysUntilDeadline <= 3 -> 1.0 // Due soon
                daysUntilDeadline <= 7 -> 0.5 // Due this week
                else -> 0.0                   // Due later
            }
        }

        priorityScore += when (task.taskType) {
            "BUSINESS" -> 0.7
            "STUDY" -> 0.5
            "HOME" -> 0.3
            else -> 0.0 // Personal or other
        }

        val urgentKeywords = listOf("urgent", "important", "critical", "asap", "deadline", "due", "emergency", "projet urgent", "dépôt urgent", "immédiat", "critique", "aujourd'hui")
        val title = task.title.lowercase()
        val description = task.description?.lowercase() ?: ""

        if (title.contains("urgent", ignoreCase = true)) {
            priorityScore += 2.0  // Higher score for "urgent"
        }

        for (keyword in urgentKeywords) {
            if (title.contains(keyword)) {
                priorityScore += 0.5
                break
            }
            if (description.contains(keyword)) {
                priorityScore += 0.3
                break
            }
        }

        return when {
            priorityScore >= 1.5 -> 2 // High priority
            priorityScore >= 0.7 -> 1 // Medium priority
            else -> 0                 // Low priority
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
            val insights = mutableListOf<String>()

            task.deadlineDate?.let { date ->
                val daysUntilDeadline = calculateDaysUntilDeadline(date)
                when {
                    daysUntilDeadline < 0 -> insights.add("Task is overdue")
                    daysUntilDeadline == 0f -> insights.add("Task is due today")
                    daysUntilDeadline <= 1f -> insights.add("Task is due tomorrow")
                    daysUntilDeadline <= 3f -> insights.add("Task is due soon")
                    else -> insights.add("Task is due in $daysUntilDeadline days")
                }
            }

            when (task.taskType) {
                "BUSINESS" -> insights.add("Business task - may require preparation")
                "STUDY" -> insights.add("Study task - consider breaking into smaller parts")
                "HOME" -> insights.add("Home task - can be done in free time")
                else -> insights.add("Personal task - prioritize based on your schedule")
            }

            when (task.status) {
                "PENDING" -> insights.add("Task is pending - consider starting soon")
                "IN_PROGRESS" -> insights.add("Task is in progress - keep momentum")
                "COMPLETED" -> insights.add("Task is completed - great job!")
                "CANCELLED" -> insights.add("Task was cancelled")
                else -> insights.add("Task status: ${task.status}")
            }

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
            val dateFormats = listOf(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
                SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()),
                SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
            )

            var deadline: Date? = null
            for (format in dateFormats) {
                try {
                    deadline = format.parse(deadlineDate)
                    if (deadline != null) {
                        Log.d(TAG, "Successfully parsed date '${deadlineDate}' with format: ${format.toPattern()}")
                        break
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to parse date '${deadlineDate}' with format: ${format.toPattern()}")
                }
            }

            if (deadline == null) {
                Log.e(TAG, "Failed to parse deadline date: $deadlineDate")
                return 0f
            }

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