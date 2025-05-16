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
            // Charger le modèle
            val model = loadModelFile()
            interpreter = Interpreter(model)
            
            // Log model input and output details
            val inputs = interpreter?.inputTensorCount ?: 0
            val outputs = interpreter?.outputTensorCount ?: 0
            
            Log.d("TaskPriorityPredictor", "Model loaded successfully. Input tensors: $inputs, Output tensors: $outputs")
            
            if (inputs > 0) {
                val inputTensor = interpreter?.getInputTensor(0)
                val inputShape = inputTensor?.shape()?.joinToString(", ")
                val inputType = inputTensor?.dataType()
                Log.d("TaskPriorityPredictor", "Input tensor shape: [$inputShape], type: $inputType")
            }
            
            if (outputs > 0) {
                val outputTensor = interpreter?.getOutputTensor(0)
                val outputShape = outputTensor?.shape()?.joinToString(", ")
                val outputType = outputTensor?.dataType()
                Log.d("TaskPriorityPredictor", "Output tensor shape: [$outputShape], type: $outputType")
            }
        } catch (e: Exception) {
            Log.e("TaskPriorityPredictor", "Error loading model: ${e.message}", e)
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
        // Préparer les données d'entrée - modèle attend 9 features (36 bytes)
        val inputBuffer = ByteBuffer.allocateDirect(9 * 4).apply {
            order(ByteOrder.nativeOrder())
            putFloat(daysUntilDeadline)
            putFloat(if (isTypeMeeting) 1f else 0f)
            putFloat(if (isTypePersonal) 1f else 0f)
            putFloat(if (isTypeWork) 1f else 0f)
            putFloat(if (isStatusCompleted) 1f else 0f)
            putFloat(if (isStatusInProgress) 1f else 0f)
            putFloat(if (isStatusPending) 1f else 0f)
            // Ajouter deux features supplémentaires avec des valeurs par défaut
            putFloat(0f) // 8ème feature
            putFloat(0f) // 9ème feature
            rewind()
        }

        // Préparer le buffer de sortie
        val outputBuffer = Array(1) { FloatArray(3) }

        try {
            // Log input values
            Log.d("TaskPriorityPredictor", "Input values: daysUntilDeadline=$daysUntilDeadline, " +
                   "isTypeMeeting=${if (isTypeMeeting) 1 else 0}, " +
                   "isTypePersonal=${if (isTypePersonal) 1 else 0}, " +
                   "isTypeWork=${if (isTypeWork) 1 else 0}, " +
                   "isStatusCompleted=${if (isStatusCompleted) 1 else 0}, " +
                   "isStatusInProgress=${if (isStatusInProgress) 1 else 0}, " +
                   "isStatusPending=${if (isStatusPending) 1 else 0}")
            
            // Exécuter l'inférence
            Log.d("TaskPriorityPredictor", "Running inference with input buffer size: ${inputBuffer.capacity()} bytes")
            interpreter?.run(inputBuffer, outputBuffer)
            
            // Log output values
            val outputValues = outputBuffer[0].toList()
            Log.d("TaskPriorityPredictor", "Output values: $outputValues")
            
            // Déterminer la priorité (index de la valeur maximale)
            val priorityIndex = outputBuffer[0].indices.maxByOrNull { outputBuffer[0][it] } ?: 0
            Log.d("TaskPriorityPredictor", "Predicted priority: $priorityIndex")
            return priorityIndex
        } catch (e: Exception) {
            Log.e("TaskPriorityPredictor", "Error during inference: ${e.message}", e)
            // Default to medium priority (1) in case of error
            return 1
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
