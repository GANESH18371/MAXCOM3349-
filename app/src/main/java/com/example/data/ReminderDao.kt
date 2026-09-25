package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY triggerTimeMillis ASC")
    fun getAllRemindersFlow(): Flow<List<ReminderItem>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY triggerTimeMillis ASC")
    fun getActiveRemindersFlow(): Flow<List<ReminderItem>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY triggerTimeMillis ASC")
    suspend fun getActiveReminders(): List<ReminderItem>

    @Query("SELECT * FROM reminders ORDER BY triggerTimeMillis ASC")
    suspend fun getAllRemindersList(): List<ReminderItem>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ReminderItem?

    @Query("SELECT * FROM reminders WHERE LOWER(task) LIKE '%' || LOWER(:taskKeyword) || '%' ORDER BY triggerTimeMillis ASC")
    suspend fun findRemindersByTaskKeyword(taskKeyword: String): List<ReminderItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(item: ReminderItem): Long

    @Update
    suspend fun updateReminder(item: ReminderItem)

    @Query("UPDATE reminders SET isCompleted = 1 WHERE id = :id")
    suspend fun markCompleted(id: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)

    @Query("DELETE FROM reminders WHERE LOWER(task) LIKE '%' || LOWER(:taskKeyword) || '%'")
    suspend fun deleteRemindersByTaskKeyword(taskKeyword: String): Int

    @Query("DELETE FROM reminders")
    suspend fun deleteAllReminders()
}
