package com.brunnodev.fieldconsole

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkerFactory
import androidx.work.ListenableWorker

class FieldSyncWorker(context: Context, parameters: WorkerParameters, private val repository: FieldRepository, private val accessToken: () -> String?) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val token = accessToken() ?: return Result.failure()
        val batch = repository.pending()
        val delivery = ArchiveDelivery(token)
        for (row in batch) {
            try {
                if (!delivery.send(row.id, row.eventType, row.payload)) { repository.retry(listOf(row.id)); return Result.retry() }
                repository.acknowledge(listOf(row.id))
            } catch (_: Exception) { repository.retry(listOf(row.id)); return Result.retry() }
        }
        return Result.success()
    }
}
class ArchiveWorkerFactory(private val repository: FieldRepository, private val token: () -> String?) : WorkerFactory() {
    override fun createWorker(context: Context, className: String, parameters: WorkerParameters): ListenableWorker? =
        if (className == FieldSyncWorker::class.java.name) FieldSyncWorker(context, parameters, repository, token) else null
}
