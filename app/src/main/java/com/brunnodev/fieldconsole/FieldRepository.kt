package com.brunnodev.fieldconsole

import androidx.room.withTransaction
import java.util.UUID
import org.json.JSONObject

class FieldRepository(private val database: FieldDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao get() = database.fieldDao()

    suspend fun begin(assetCode: String, operatorId: String, tasks: List<ChecklistTask>): String {
        val assetTag = parseAssetTag(assetCode)
        require(operatorId.isNotBlank() && operatorId.length <= 120 && tasks.size in 1..100 && tasks.map { it.id }.distinct().size == tasks.size && tasks.all { it.id.matches(Regex("[A-Za-z0-9_-]{1,64}")) && it.label.isNotBlank() })
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            dao.insertInspection(InspectionEntity(id, assetTag, operatorId, InspectionState.OPEN, clock()))
            dao.saveTasks(tasks.map { InspectionTaskEntity(id, it.id, it.label, it.mandatory, false, null) })
            dao.queue(FieldOutboxEntity(inspectionId = id, eventType = "inspection.opened", payload = JSONObject().put("assetTag", assetTag).put("operator", operatorId).toString(), createdAt = clock()))
        }
        return id
    }

    suspend fun completeTask(inspectionId: String, taskId: String, evidenceCode: String? = null) {
        require(evidenceCode == null || evidenceCode.length <= 500)
        database.withTransaction {
            val inspection = dao.inspection(inspectionId) ?: error("inspection_not_found")
            check(inspection.state == InspectionState.OPEN)
            val current = dao.tasks(inspectionId)
            val task = current.firstOrNull { it.taskId == taskId } ?: error("task_not_found")
            dao.saveTasks(current.map { if (it.taskId == taskId) it.copy(completed = true, evidenceCode = evidenceCode) else it })
            dao.queue(FieldOutboxEntity(inspectionId = inspectionId, eventType = "inspection.task_completed", payload = JSONObject().put("taskId", task.taskId).put("evidence", evidenceCode.orEmpty()).toString(), createdAt = clock()))
        }
    }

    suspend fun submit(inspectionId: String) {
        database.withTransaction {
            val inspection = dao.inspection(inspectionId) ?: error("inspection_not_found")
            val tasks = dao.tasks(inspectionId)
            check(tasks.isNotEmpty() && tasks.filter { it.mandatory }.all { it.completed })
            check(dao.transition(inspectionId, InspectionState.OPEN, InspectionState.SUBMITTED, clock()) == 1)
            dao.queue(FieldOutboxEntity(inspectionId = inspectionId, eventType = "inspection.submitted", payload = """{"inspectionId":"${inspectionId}"}""", createdAt = clock()))
        }
    }

    suspend fun pending(limit: Int = 100) = dao.pending(limit)
    suspend fun acknowledge(ids: List<String>) { if (ids.isNotEmpty()) dao.acknowledge(ids) }
    suspend fun retry(ids: List<String>) { if (ids.isNotEmpty()) dao.retry(ids) }
}
