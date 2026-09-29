package com.brunnodev.fieldconsole

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL

class FieldSyncWorker(
    context: Context,
    parameters: WorkerParameters,
    private val repository: FieldRepository
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val batch = repository.pending()
        if (batch.isEmpty()) return Result.success()
        val endpoint = inputData.getString("endpoint") ?: return Result.failure()
        return try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Idempotency-Key", batch.first().id)
            }
            val payload = batch.joinToString(prefix = "[", postfix = "]") { it.payload }
            connection.outputStream.use { it.write(payload.toByteArray()) }
            val status = connection.responseCode
            connection.disconnect()
            if (status in 200..299) {
                repository.acknowledge(batch.map { it.id })
                Result.success()
            } else {
                repository.retry(batch.map { it.id })
                Result.retry()
            }
        } catch (_: Exception) {
            repository.retry(batch.map { it.id })
            Result.retry()
        }
    }
}
