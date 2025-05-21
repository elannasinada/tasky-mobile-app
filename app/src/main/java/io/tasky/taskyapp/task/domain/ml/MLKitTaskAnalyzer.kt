package io.tasky.taskyapp.task.domain.ml

import com.google.firebase.ml.naturallanguage.FirebaseNaturalLanguage
import com.google.firebase.ml.naturallanguage.languageid.FirebaseLanguageIdentification
import io.tasky.taskyapp.task.domain.model.Task
import java.util.Calendar

/**
 * Uses Firebase ML Kit to analyze tasks and determine their priority levels.
 */
class MLKitTaskAnalyzer {
    private val languageIdentifier = FirebaseNaturalLanguage.getInstance().languageIdentification
    
    /**
     * Analyzes a task and determines its priority level.
     * @param task The task to analyze
     * @param callback A callback function that receives the priority level (0=low, 1=medium, 2=high)
     */
    fun analyzePriority(task: Task, callback: (Int) -> Unit) {
        // Combine title and description for analysis
        val text = "${task.title} ${task.description ?: ""}"
          
        // Identify the language (to demonstrate ML Kit usage)
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                // Simple analysis based on keywords
                val priority = when {
                    containsUrgentKeywords(text) -> 2 // High priority
                    containsImportantKeywords(text) -> 1 // Medium priority
                    else -> {
                        // Analysis based on deadline date
                        val deadlineDate = task.deadlineDate?.let { parseDeadlineDate(it) }
                        val daysUntilDeadline = deadlineDate?.let { calculateDaysUntilDeadline(it) } ?: Int.MAX_VALUE
                          
                        when {
                            daysUntilDeadline <= 1 -> 2 // High priority if deadline is within 24 hours
                            daysUntilDeadline <= 3 -> 1 // Medium priority if deadline is within 2-3 days
                            else -> 0 // Low priority
                        }
                    }
                }
                  
                callback(priority)
            }
            .addOnFailureListener {
                // In case of failure, use fallback logic
                val deadlineDate = task.deadlineDate?.let { parseDeadlineDate(it) }
                val daysUntilDeadline = deadlineDate?.let { calculateDaysUntilDeadline(it) } ?: Int.MAX_VALUE
                  
                val priority = when {
                    daysUntilDeadline <= 1 -> 2
                    daysUntilDeadline <= 3 -> 1
                    else -> 0
                }
                  
                callback(priority)
            }
    }
      
    /**
     * Analyzes task priority synchronously for immediate use.
     * This simpler version doesn't use ML Kit's language detection but uses the same keyword and deadline logic.
     * 
     * @param task The task to analyze
     * @return The priority level (0=low, 1=medium, 2=high)
     */
    fun analyzePrioritySync(task: Task): Int {
        // Combine title and description for analysis
        val text = "${task.title} ${task.description ?: ""}"
        
        // Simple analysis based on keywords
        return when {
            containsUrgentKeywords(text) -> 2 // High priority
            containsImportantKeywords(text) -> 1 // Medium priority
            else -> {
                // Analysis based on deadline date
                val deadlineDate = task.deadlineDate?.let { parseDeadlineDate(it) }
                val daysUntilDeadline = deadlineDate?.let { calculateDaysUntilDeadline(it) } ?: Int.MAX_VALUE
                  
                when {
                    daysUntilDeadline <= 1 -> 2 // High priority if deadline is within 24 hours
                    daysUntilDeadline <= 3 -> 1 // Medium priority if deadline is within 2-3 days
                    else -> 0 // Low priority
                }
            }
        }
    }
      
    /**
     * Checks if text contains urgent keywords.
     */
    private fun containsUrgentKeywords(text: String): Boolean {
        val urgentKeywords = listOf("urgent", "immédiat", "critique", "aujourd'hui", "asap", "emergency", "projet urgent", "dépôt urgent")
        return urgentKeywords.any { text.lowercase().contains(it.lowercase()) }
    }
      
    /**
     * Checks if text contains important keywords.
     */
    private fun containsImportantKeywords(text: String): Boolean {
        val importantKeywords = listOf("important", "priorité", "nécessaire", "bientôt", "soon")
        return importantKeywords.any { text.lowercase().contains(it.lowercase()) }
    }
      
    /**
     * Parses a date string into a Calendar object.
     */
    private fun parseDeadlineDate(dateString: String): Calendar {
        // Convert date string to Calendar object
        val parts = dateString.split("-")
        val calendar = Calendar.getInstance()
        if (parts.size == 3) {
            try {
                // Check the format - Tasky appears to use yyyy-MM-dd format
                val year = parts[0].toInt()
                val month = parts[1].toInt() - 1 // Months start at 0 in Calendar
                val day = parts[2].toInt()
                calendar.set(year, month, day)
            } catch (e: Exception) {
                // Fallback in case of parsing error
                return Calendar.getInstance()
            }
        }
        return calendar
    }
      
    /**
     * Calculates days until a deadline.
     */
    private fun calculateDaysUntilDeadline(deadlineDate: Calendar): Int {
        val today = Calendar.getInstance()
        // Clear time components to compare dates only
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)
        
        deadlineDate.set(Calendar.HOUR_OF_DAY, 0)
        deadlineDate.set(Calendar.MINUTE, 0)
        deadlineDate.set(Calendar.SECOND, 0)
        deadlineDate.set(Calendar.MILLISECOND, 0)
        
        val diff = deadlineDate.timeInMillis - today.timeInMillis
        return (diff / (24 * 60 * 60 * 1000)).toInt()
    }
}