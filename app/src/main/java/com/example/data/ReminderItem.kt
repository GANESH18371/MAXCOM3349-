package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val task: String,
    val triggerTimeMillis: Long,
    val formattedTime: String,
    val isAlarm: Boolean = false,
    val isCompleted: Boolean = false
)
