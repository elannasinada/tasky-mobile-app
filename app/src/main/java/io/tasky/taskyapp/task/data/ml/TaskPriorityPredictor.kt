package io.tasky.taskyapp.task.data.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TaskPriorityPredictor(private val context: Context) {
    private var interpreter: Interpreter? = null

    init {
        try {
            val model = loadModelFile()
            interpreter = Interpreter(model)
        } catch (e: Exception) {
            Log.e("TaskPriorityPredictor", "Error loading model: ${e.message}")
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("task_priority_model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun predictPriority(
        daysUntilDeadline: Float,
        isTypeMeeting: Boolean,
        isTypePersonal: Boolean,
        isTypeWork: Boolean,
        isStatusCompleted: Boolean,
        isStatusInProgress: Boolean,
        isStatusPending: Boolean
    ): Int {
        try {
            val inputBuffer = ByteBuffer.allocateDirect(7 * 4).apply {
                order(ByteOrder.nativeOrder())
                putFloat(daysUntilDeadline)
                putFloat(if (isTypeMeeting) 1f else 0f)
                putFloat(if (isTypePersonal) 1f else 0f)
                putFloat(if (isTypeWork) 1f else 0f)
                putFloat(if (isStatusCompleted) 1f else 0f)
                putFloat(if (isStatusInProgress) 1f else 0f)
                putFloat(if (isStatusPending) 1f else 0f)
                rewind()
            }

            val outputBuffer = Array(1) { FloatArray(3) }

            interpreter?.run(inputBuffer, outputBuffer)

            return outputBuffer[0].indices.maxByOrNull { outputBuffer[0][it] } ?: 1
        } catch (e: Exception) {
            Log.e("TaskPriorityPredictor", "Error during prediction: ${e.message}")
            return 1 // Default to medium priority
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}