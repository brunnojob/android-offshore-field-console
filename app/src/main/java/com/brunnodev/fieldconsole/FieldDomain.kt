package com.brunnodev.fieldconsole

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class InspectionState { OPEN, SUBMITTED, SYNCED }

@Entity(tableName = "inspections")
data class InspectionEntity(@PrimaryKey val id: String = UUID.randomUUID().toString(), val assetTag: String, val operatorId: String, val state: InspectionState, val updatedAt: Long)

@Entity(tableName = "inspection_tasks", primaryKeys = ["inspectionId", "taskId"])
data class InspectionTaskEntity(val inspectionId: String, val taskId: String, val label: String, val mandatory: Boolean, val completed: Boolean, val evidenceCode: String?)

@Entity(tableName = "field_outbox")
data class FieldOutboxEntity(@PrimaryKey val id: String = UUID.randomUUID().toString(), val inspectionId: String, val eventType: String, val payload: String, val createdAt: Long, val attempts: Int = 0)

data class ChecklistTask(val id: String, val label: String, val mandatory: Boolean)

class InspectionWorkflow(private val tasks: List<ChecklistTask>) {
    private val completed = mutableSetOf<String>()
    var state = InspectionState.OPEN
        private set
    fun complete(taskId: String) {
        check(state == InspectionState.OPEN)
        require(tasks.any { it.id == taskId })
        completed.add(taskId)
    }
    fun submit() {
        check(state == InspectionState.OPEN)
        check(tasks.filter { it.mandatory }.all { it.id in completed })
        state = InspectionState.SUBMITTED
    }
    fun progress() = completed.size to tasks.size
}

fun parseAssetTag(raw: String): String {
    val tag = raw.trim().removePrefix("RF:")
    require(tag.matches(Regex("[A-Z0-9][A-Z0-9-]{2,31}")))
    return tag
}
