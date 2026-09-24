package com.example.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import com.example.util.DebugLogger
import java.util.Locale

data class InstalledApp(
    val name: String,
    val packageName: String,
    val icon: Drawable? = null,
    val normalizedName: String = name.lowercase(Locale.getDefault()).trim()
)

object AppOpenManager {
    private const val TAG = "AppOpenManager"

    // Greeting, trigger, and command filler words in English & Hindi to strip anywhere
    private val STRIP_WORDS = listOf(
        "max", "hello", "hey", "suno", "hi", "namaste", "bhai", "yaar",
        "open", "kholo", "khol", "kholiye", "khol do", "khol de",
        "start", "launch", "chalao", "chala", "chala do", "chala de",
        "please", "plz", "zarur", "jaldi", "run", "execute",
        "app", "application", "ko", "se", "pe", "par", "the", "a", "an"
    )

    /**
     * Fresh query of all launchable installed apps on the device
     */
    fun getFreshInstalledApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.ResolveInfoFlags.of(0)
        } else {
            0
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, flags as PackageManager.ResolveInfoFlags)
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        val apps = mutableListOf<InstalledApp>()
        for (resolveInfo in resolveInfos) {
            try {
                val packageName = resolveInfo.activityInfo.packageName
                // Skip our own app from open matches if needed, but include other launcher apps
                val label = resolveInfo.loadLabel(pm).toString().trim()
                val icon = resolveInfo.loadIcon(pm)
                if (label.isNotBlank()) {
                    apps.add(InstalledApp(name = label, packageName = packageName, icon = icon))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error resolving app info", e)
            }
        }

        return apps.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    /**
     * Strips "Max", "hello", "hey", "suno", "open", "kholo", etc. from anywhere in command
     */
    fun sanitizeCommand(rawText: String): String {
        var text = rawText.lowercase(Locale.getDefault())
            .replace(Regex("[^a-zA-Z0-9\\s\\u0900-\\u097F]"), " ") // keep alphanumeric and Devanagari

        // Remove multi-word phrases first
        val multiWordPhrases = listOf("khol do", "chala do", "khol de", "chala de", "khol dena", "chala dena")
        for (phrase in multiWordPhrases) {
            text = text.replace(Regex("\\b$phrase\\b"), " ")
        }

        // Split words and filter out any stripped words
        val tokens = text.split("\\s+".toRegex()).filter { token ->
            token.isNotBlank() && token !in STRIP_WORDS
        }

        val cleaned = tokens.joinToString(" ").trim()
        return if (cleaned.isNotBlank()) cleaned else rawText.trim()
    }

    /**
     * Fuzzy matches the cleaned query against installed apps
     */
    fun fuzzyMatchApp(query: String, apps: List<InstalledApp>): InstalledApp? {
        val cleanQuery = sanitizeCommand(query).lowercase(Locale.getDefault()).trim()
        if (cleanQuery.isBlank()) return null

        // 1. Exact match with app name
        val exactMatch = apps.find { it.normalizedName.equals(cleanQuery, ignoreCase = true) }
        if (exactMatch != null) return exactMatch

        // 2. Exact match with package name ending (e.g. "youtube" matches "com.google.android.youtube")
        val packageExact = apps.find { app ->
            val pkgLastPart = app.packageName.substringAfterLast(".").lowercase(Locale.getDefault())
            pkgLastPart == cleanQuery || app.packageName.lowercase(Locale.getDefault()).endsWith(".$cleanQuery")
        }
        if (packageExact != null) return packageExact

        // 3. App name starts with query
        val startsWithMatch = apps.find { it.normalizedName.startsWith(cleanQuery) }
        if (startsWithMatch != null) return startsWithMatch

        // 4. App name contains query as a distinct word or substring
        val containsMatch = apps.find { app ->
            app.normalizedName.contains(cleanQuery) ||
                    cleanQuery.contains(app.normalizedName)
        }
        if (containsMatch != null) return containsMatch

        // 5. Package name contains query
        val pkgContains = apps.find { it.packageName.lowercase(Locale.getDefault()).contains(cleanQuery) }
        if (pkgContains != null) return pkgContains

        // 6. Word-level token match (e.g. "Google Maps" vs "Maps")
        val queryTokens = cleanQuery.split(" ").filter { it.isNotBlank() }
        val tokenMatch = apps.maxByOrNull { app ->
            val appTokens = app.normalizedName.split(" ")
            var score = 0
            for (qt in queryTokens) {
                if (appTokens.any { it.equals(qt, ignoreCase = true) || it.startsWith(qt) }) {
                    score += 2
                } else if (appTokens.any { it.contains(qt) }) {
                    score += 1
                }
            }
            score
        }

        if (tokenMatch != null) {
            val appTokens = tokenMatch.normalizedName.split(" ")
            val hasMatch = queryTokens.any { qt ->
                appTokens.any { it.contains(qt) || qt.contains(it) }
            }
            if (hasMatch) return tokenMatch
        }

        // 7. Levenshtein Distance for typos (e.g. "yotube" -> "YouTube", "whatapp" -> "WhatsApp")
        var bestApp: InstalledApp? = null
        var minDistance = Int.MAX_VALUE
        for (app in apps) {
            val dist = levenshteinDistance(cleanQuery, app.normalizedName)
            val maxAllowed = when {
                cleanQuery.length <= 3 -> 1
                cleanQuery.length <= 6 -> 2
                else -> 3
            }
            if (dist <= maxAllowed && dist < minDistance) {
                minDistance = dist
                bestApp = app
            }
        }

        return bestApp
    }

    /**
     * Opens matched app and outputs required logs:
     * "APP_OPEN_MATCH: <found/not-found>"
     * "APP_OPEN_LAUNCH: success/fail"
     */
    fun processAndLaunch(context: Context, rawQuery: String): Boolean {
        val cleanQuery = sanitizeCommand(rawQuery)
        val apps = getFreshInstalledApps(context)
        val matchedApp = fuzzyMatchApp(rawQuery, apps)

        if (matchedApp != null) {
            DebugLogger.logMatch(true, "${matchedApp.name} [${matchedApp.packageName}]")
            val launched = launchApp(context, matchedApp)
            if (launched) {
                DebugLogger.logLaunch(true, matchedApp.name)
            } else {
                DebugLogger.logLaunch(false, "Could not start activity for ${matchedApp.packageName}")
            }
            return launched
        } else {
            DebugLogger.logMatch(false, cleanQuery)
            DebugLogger.logLaunch(false, "No match found for '$rawQuery'")
            return false
        }
    }

    fun launchApp(context: Context, app: InstalledApp): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                Log.e(TAG, "No launch intent for package: ${app.packageName}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app: ${app.packageName}", e)
            false
        }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
