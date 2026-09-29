package com.brunnodev.fieldconsole

import androidx.room.*

class FieldConverters {
    @TypeConverter fun fromState(value: InspectionState): String = value.name
    @TypeConverter fun toState(value: String): InspectionState = InspectionState.valueOf(value)
}

@Dao
interface FieldDao {
    @Insert suspend fun insertInspection(row: InspectionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveTasks(rows: List<InspectionTaskEntity>)
    @Insert suspend fun queue(event: FieldOutboxEntity)
    @Query("SELECT * FROM inspections WHERE id = :id") suspend fun inspection(id: String): InspectionEntity?
    @Query("SELECT * FROM inspection_tasks WHERE inspectionId = :id ORDER BY taskId") suspend fun tasks(id: String): List<InspectionTaskEntity>
    @Query("SELECT * FROM field_outbox ORDER BY createdAt LIMIT :limit") suspend fun pending(limit: Int): List<FieldOutboxEntity>
    @Query("DELETE FROM field_outbox WHERE id IN (:ids)") suspend fun acknowledge(ids: List<String>)
    @Query("UPDATE field_outbox SET attempts = attempts + 1 WHERE id IN (:ids)") suspend fun retry(ids: List<String>)
    @Query("UPDATE inspections SET state = :state, updatedAt = :timestamp WHERE id = :id AND state = :expected")
    suspend fun transition(id: String, expected: InspectionState, state: InspectionState, timestamp: Long): Int
}

@Database(entities = [InspectionEntity::class, InspectionTaskEntity::class, FieldOutboxEntity::class], version = 1, exportSchema = true)
@TypeConverters(FieldConverters::class)
abstract class FieldDatabase : RoomDatabase() {
    abstract fun fieldDao(): FieldDao
}
