package com.example.data

import kotlinx.coroutines.flow.Flow

class ReminderRepository(private val reminderDao: ReminderDao) {
    val allReminders: Flow<List<ReminderItem>> = reminderDao.getAllRemindersFlow()
    val activeReminders: Flow<List<ReminderItem>> = reminderDao.getActiveRemindersFlow()

    suspend fun getActiveReminders(): List<ReminderItem> = reminderDao.getActiveReminders()

    suspend fun getAllRemindersList(): List<ReminderItem> = reminderDao.getAllRemindersList()

    suspend fun getReminderById(id: Long): ReminderItem? = reminderDao.getReminderById(id)

    suspend fun findRemindersByTaskKeyword(keyword: String): List<ReminderItem> =
        reminderDao.findRemindersByTaskKeyword(keyword)

    suspend fun insert(reminder: ReminderItem): Long = reminderDao.insertReminder(reminder)

    suspend fun update(reminder: ReminderItem) = reminderDao.updateReminder(reminder)

    suspend fun markCompleted(id: Long) = reminderDao.markCompleted(id)

    suspend fun deleteById(id: Long) = reminderDao.deleteReminderById(id)

    suspend fun deleteByTaskKeyword(keyword: String): Int =
        reminderDao.deleteRemindersByTaskKeyword(keyword)

    suspend fun deleteAll() = reminderDao.deleteAllReminders()
}
