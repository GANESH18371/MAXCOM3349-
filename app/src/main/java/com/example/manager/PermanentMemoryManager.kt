package com.example.manager

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.PermanentMemory
import com.example.util.DebugLogger
import com.example.util.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MemoryCommandResult {
    data class Handled(val message: String) : MemoryCommandResult
    data class LaunchFavoriteApp(val appName: String) : MemoryCommandResult
    object NotMemoryCommand : MemoryCommandResult
}

object PermanentMemoryManager {
    private const val TAG = "PermanentMemoryManager"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var database: AppDatabase? = null

    fun init(context: Context) {
        if (database == null) {
            database = AppDatabase.getInstance(context.applicationContext)
            DebugLogger.logInfo("PermanentMemoryManager initialized (Local Room Persistence)")
        }
    }

    private fun getDb(context: Context): AppDatabase {
        return database ?: AppDatabase.getInstance(context.applicationContext).also { database = it }
    }

    fun getAllMemoriesFlow(context: Context): Flow<List<PermanentMemory>> {
        return getDb(context).permanentMemoryDao().getAllMemoriesFlow()
    }

    suspend fun saveMemory(
        context: Context,
        key: String,
        value: String,
        category: String = "general",
        rawStatement: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val normalizedKey = key.trim().lowercase().replace("\\s+".toRegex(), "_")
            val memory = PermanentMemory(
                memoryKey = normalizedKey,
                memoryValue = value.trim(),
                category = category,
                rawStatement = rawStatement.ifBlank { value.trim() },
                updatedAt = System.currentTimeMillis()
            )
            getDb(context).permanentMemoryDao().insertMemory(memory)
            DebugLogger.logInfo("MEMORY_SAVED: $normalizedKey -> ${value.trim()}")
            true
        } catch (e: Exception) {
            DebugLogger.logInfo("MEMORY_SAVE_FAILED: ${e.message}")
            false
        }
    }

    suspend fun getMemory(context: Context, key: String): String? = withContext(Dispatchers.IO) {
        val normalizedKey = key.trim().lowercase().replace("\\s+".toRegex(), "_")
        val item = getDb(context).permanentMemoryDao().getMemoryByKey(normalizedKey)
        if (item != null) {
            DebugLogger.logInfo("MEMORY_RECALLED: $normalizedKey -> ${item.memoryValue}")
            item.memoryValue
        } else {
            null
        }
    }

    suspend fun forgetMemory(context: Context, query: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val normalized = query.trim().lowercase()
            val dao = getDb(context).permanentMemoryDao()
            val matches = dao.searchMemories(normalized)
            if (matches.isNotEmpty()) {
                val target = matches.first()
                dao.deleteMemory(target)
                DebugLogger.logInfo("MEMORY_FORGOTTEN: ${target.memoryKey} (${target.memoryValue})")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            DebugLogger.logInfo("MEMORY_FORGET_FAILED: ${e.message}")
            false
        }
    }

    suspend fun deleteMemoryById(context: Context, id: Long) = withContext(Dispatchers.IO) {
        getDb(context).permanentMemoryDao().deleteMemoryById(id)
        DebugLogger.logInfo("MEMORY_DELETED_BY_ID: $id")
    }

    suspend fun clearAllMemories(context: Context) = withContext(Dispatchers.IO) {
        getDb(context).permanentMemoryDao().clearAllMemories()
        DebugLogger.logInfo("MEMORY_ALL_CLEARED")
    }

    suspend fun getAllMemoriesSummary(context: Context): String = withContext(Dispatchers.IO) {
        val list = getDb(context).permanentMemoryDao().getAllMemories()
        if (list.isEmpty()) {
            ""
        } else {
            list.joinToString(", ") { "${it.memoryKey}: ${it.memoryValue}" }
        }
    }

    /**
     * Determines whether the given text is a permanent memory operation.
     */
    fun isMemoryCommand(lower: String): Boolean {
        return lower.startsWith("yaad rakhna") ||
                lower.startsWith("yeh yaad rakhna") ||
                lower.startsWith("ye yaad rakhna") ||
                lower.startsWith("remember that") ||
                lower.startsWith("remember:") ||
                lower.startsWith("remember ") ||
                lower.contains("favorite app hai") ||
                lower.contains("favourite app hai") ||
                lower.contains("favorite app is") ||
                lower.contains("pasand hai") ||
                lower.contains("meri pasand") ||
                lower.contains("mujhe pasand") ||
                lower.contains("mera naam hai") ||
                lower.contains("my name is") ||
                lower.contains("roz subah") ||
                lower.contains("roz shaam") ||
                lower.contains("daily routine") ||
                lower.contains("tumhe mere baare me kya pata") ||
                lower.contains("tumhe mere bare me kya pata") ||
                lower.contains("what do you know about me") ||
                lower.contains("meri memories") ||
                lower.contains("meri memory") ||
                lower.contains("meri yaadein") ||
                lower.contains("mujhe kya pasand hai") ||
                lower.contains("meri favorite app kaun") ||
                lower.contains("meri favourite app kaun") ||
                lower.contains("favorite app kholo") ||
                lower.contains("favourite app kholo") ||
                lower.contains("meri favorite app open") ||
                lower.contains("bhool jao") ||
                lower.contains("forget that") ||
                lower.contains("saari memory delete") ||
                lower.contains("clear all memory")
    }

    /**
     * Executes the memory command and provides natural bilingual feedback.
     */
    suspend fun handleMemoryCommand(commandText: String, context: Context): MemoryCommandResult = withContext(Dispatchers.IO) {
        val lower = commandText.trim().lowercase()

        // 1. RECALL: Launch favorite app
        if (lower.contains("favorite app kholo") ||
            lower.contains("favourite app kholo") ||
            lower.contains("meri favorite app open") ||
            lower.contains("open my favorite app") ||
            lower == "favorite app"
        ) {
            val favApp = getMemory(context, "favorite_app")
            return@withContext if (!favApp.isNullOrBlank()) {
                DebugLogger.logInfo("MEMORY_RECALLED: favorite_app -> $favApp (Invoking App Launcher)")
                MemoryCommandResult.LaunchFavoriteApp(favApp)
            } else {
                val msg = "Aapne abhi tak koi favorite app save nahi ki hai. 'Meri favorite app YouTube hai' bolkar save kar sakte hain."
                TtsManager.speakIfVoiceReady(msg, caller = "PermanentMemory")
                MemoryCommandResult.Handled(msg)
            }
        }

        // 2. FORGET ALL: Clear all memories
        if (lower.contains("saari memory delete") || lower.contains("clear all memory") || lower.contains("delete all memories")) {
            clearAllMemories(context)
            val msg = "Maine aapki saari permanent memories delete kar di hain."
            TtsManager.speakIfVoiceReady(msg, caller = "PermanentMemory")
            return@withContext MemoryCommandResult.Handled(msg)
        }

        // 3. FORGET SPECIFIC: "X bhool jao"
        if (lower.contains("bhool jao") || lower.contains("forget")) {
            val cleanQuery = lower
                .replace("bhool jao", "")
                .replace("forget", "")
                .replace("ki", "")
                .replace("yeh", "")
                .replace("ye", "")
                .replace("ko", "")
                .trim()

            val forgotten = forgetMemory(context, cleanQuery)
            val msg = if (forgotten) {
                "Theek hai, maine '$cleanQuery' ko apni memory se hata diya hai."
            } else {
                "Mujhe '$cleanQuery' se judi koi memory nahi mili."
            }
            TtsManager.speakIfVoiceReady(msg, caller = "PermanentMemory")
            return@withContext MemoryCommandResult.Handled(msg)
        }

        // 4. RECALL QUERY: "tumhe mere baare me kya pata hai", "meri memories kya hain", "mujhe kya pasand hai"
        if (lower.contains("tumhe mere baare me kya pata") ||
            lower.contains("tumhe mere bare me kya pata") ||
            lower.contains("what do you know about me") ||
            lower.contains("meri memories") ||
            lower.contains("meri memory") ||
            lower.contains("meri yaadein") ||
            lower.contains("mujhe kya pasand") ||
            lower.contains("meri pasand kya hai")
        ) {
            val allList = getDb(context).permanentMemoryDao().getAllMemories()
            if (allList.isEmpty()) {
                val msg = "Mujhe abhi aapke baare me kuch pata nahi hai. Aap mujhe apni pasand ya routine bata sakte hain, jaise 'Meri favorite app YouTube hai'."
                TtsManager.speakIfVoiceReady(msg, caller = "PermanentMemory")
                return@withContext MemoryCommandResult.Handled(msg)
            } else {
                val facts = allList.joinToString(". ") { it.rawStatement.ifBlank { "${it.memoryKey}: ${it.memoryValue}" } }
                val msg = "Mujhe aapke baare me yeh baatein yaad hain: $facts"
                DebugLogger.logInfo("MEMORY_RECALLED: Multiple facts recalled for user query: ${allList.size} item(s)")
                TtsManager.speakIfVoiceReady(msg, caller = "PermanentMemory")
                return@withContext MemoryCommandResult.Handled(msg)
            }
        }

        // 5. RECALL QUERY: "meri favorite app kaun si hai"
        if (lower.contains("favorite app kaun") || lower.contains("favourite app kaun") || lower.contains("what is my favorite app")) {
            val fav = getMemory(context, "favorite_app")
            val msg = if (!fav.isNullOrBlank()) {
                "Aapki favorite app $fav hai."
            } else {
                "Aapne koi favorite app save nahi ki hai."
            }
            TtsManager.speakIfVoiceReady(msg, caller = "PermanentMemory")
            return@withContext MemoryCommandResult.Handled(msg)
        }

        // 6. SAVE: "meri favorite app [x] hai"
        val favAppRegex = "(?:meri|my)\\s+(?:favorite|favourite)\\s+app\\s+(?:is\\s+)?([a-zA-Z0-9\\s]+?)(?:\\s+hai)?\$".toRegex(RegexOption.IGNORE_CASE)
        val favMatch = favAppRegex.find(lower)
        if (favMatch != null) {
            val appVal = favMatch.groupValues[1].trim()
            if (appVal.isNotBlank()) {
                saveMemory(
                    context,
                    key = "favorite_app",
                    value = appVal,
                    category = "preference",
                    rawStatement = "Aapki favorite app $appVal hai"
                )
                val msg = "Theek hai, maine yaad rakh liya ki aapki favorite app $appVal hai."
                TtsManager.speakIfVoiceReady(msg, caller = "PermanentMemory")
                return@withContext MemoryCommandResult.Handled(msg)
            }
        }

        // 7. SAVE: "mera naam [x] hai" / "my name is [x]"
        val nameRegex = "(?:mera\\s+naam|my\\s+name\\s+is)\\s+([a-zA-Z0-9\\s]+?)(?:\\s+hai)?\$".toRegex(RegexOption.IGNORE_CASE)
        val nameMatch = nameRegex.find(lower)
        if (nameMatch != null) {
            val nameVal = nameMatch.groupValues[1].trim()
            if (nameVal.isNotBlank()) {
                saveMemory(
                    context,
                    key = "user_name",
                    value = nameVal,
                    category = "identity",
                    rawStatement = "Aapka naam $nameVal hai"
                )
                val msg = "Namaste $nameVal ji! Maine aapka naam permanent memory me save kar liya hai."
                TtsManager.speakIfVoiceReady(msg, caller = "PermanentMemory")
                return@withContext MemoryCommandResult.Handled(msg)
            }
        }

        // 8. SAVE: "mujhe [x] pasand hai"
        val likeRegex = "mujhe\\s+([a-zA-Z0-9\\s]+?)\\s+pasand\\s+hai".toRegex(RegexOption.IGNORE_CASE)
        val likeMatch = likeRegex.find(lower)
        if (likeMatch != null) {
            val itemVal = likeMatch.groupValues[1].trim()
            if (itemVal.isNotBlank()) {
                val key = "pasand_" + itemVal.take(15).replace("\\s+".toRegex(), "_")
                saveMemory(
                    context,
                    key = key,
                    value = itemVal,
                    category = "preference",
                    rawStatement = "Aapko $itemVal pasand hai"
                )
                val msg = "Theek hai, maine yaad rakh liya ki aapko $itemVal pasand hai."
                TtsManager.speakIfVoiceReady(msg, caller = "PermanentMemory")
                return@withContext MemoryCommandResult.Handled(msg)
            }
        }

        // 9. SAVE: "main roz [x] karta hoon"
        val routineRegex = "main\\s+roz\\s+([a-zA-Z0-9\\s]+?)(?:\\s+karta\\s+hoon|\\s+karti\\s+hoon)?\$".toRegex(RegexOption.IGNORE_CASE)
        val routineMatch = routineRegex.find(lower)
        if (routineMatch != null) {
            val routineVal = routineMatch.groupValues[1].trim()
            if (routineVal.isNotBlank()) {
                saveMemory(
                    context,
                    key = "daily_routine_" + routineVal.take(15).replace("\\s+".toRegex(), "_"),
                    value = routineVal,
                    category = "habit",
                    rawStatement = "Aap roz $routineVal karte hain"
                )
                val msg = "Samajh gaya! Maine aapka routine yaad rakh liya: roz $routineVal."
                TtsManager.speakIfVoiceReady(msg, caller = "PermanentMemory")
                return@withContext MemoryCommandResult.Handled(msg)
            }
        }

        // 10. SAVE EXPLICIT: "yeh yaad rakhna: [fact]" / "yaad rakhna ki [fact]"
        var explicitFact = lower
        val prefixes = listOf("yeh yaad rakhna:", "ye yaad rakhna:", "yeh yaad rakhna ki", "ye yaad rakhna ki", "yaad rakhna ki", "yaad rakhna:", "yaad rakhna", "remember that", "remember:", "remember")
        for (p in prefixes) {
            if (explicitFact.startsWith(p)) {
                explicitFact = explicitFact.removePrefix(p).trim()
                break
            }
        }

        if (explicitFact.isNotBlank()) {
            val cleanKey = "fact_" + explicitFact.take(20).replace("[^a-zA-Z0-9]".toRegex(), "_")
            saveMemory(
                context,
                key = cleanKey,
                value = explicitFact,
                category = "fact",
                rawStatement = explicitFact
            )
            val msg = "Theek hai, maine memory me save kar liya hai: \"$explicitFact\"."
            TtsManager.speakIfVoiceReady(msg, caller = "PermanentMemory")
            return@withContext MemoryCommandResult.Handled(msg)
        }

        MemoryCommandResult.NotMemoryCommand
    }
}
