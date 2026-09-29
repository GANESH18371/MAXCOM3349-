package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "permanent_memory",
    indices = [Index(value = ["memoryKey"], unique = true)]
)
data class PermanentMemory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val memoryKey: String,
    val memoryValue: String,
    val category: String = "general", // "preference", "habit", "identity", "general"
    val rawStatement: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
