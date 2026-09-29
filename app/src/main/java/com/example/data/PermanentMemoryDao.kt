package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PermanentMemoryDao {
    @Query("SELECT * FROM permanent_memory ORDER BY updatedAt DESC")
    fun getAllMemoriesFlow(): Flow<List<PermanentMemory>>

    @Query("SELECT * FROM permanent_memory ORDER BY updatedAt DESC")
    suspend fun getAllMemories(): List<PermanentMemory>

    @Query("SELECT * FROM permanent_memory WHERE memoryKey = :key LIMIT 1")
    suspend fun getMemoryByKey(key: String): PermanentMemory?

    @Query("SELECT * FROM permanent_memory WHERE memoryValue LIKE '%' || :query || '%' OR memoryKey LIKE '%' || :query || '%' OR rawStatement LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    suspend fun searchMemories(query: String): List<PermanentMemory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: PermanentMemory): Long

    @Update
    suspend fun updateMemory(memory: PermanentMemory)

    @Delete
    suspend fun deleteMemory(memory: PermanentMemory)

    @Query("DELETE FROM permanent_memory WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM permanent_memory WHERE memoryKey = :key")
    suspend fun deleteMemoryByKey(key: String)

    @Query("DELETE FROM permanent_memory")
    suspend fun clearAllMemories()
}
