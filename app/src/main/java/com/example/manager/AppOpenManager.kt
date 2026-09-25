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

    // Multi-word phrases to strip first (English + Devanagari + Hinglish)
    private val MULTI_WORD_STRIP_PHRASES = listOf(
        // Hindi / Devanagari multi-word phrases
        "खोल दो", "खोल दे", "खोल देना", "खोल दीजिए", "खोलिए",
        "चला दो", "चला दे", "चला देना", "चला दीजिए", "चलाइए",
        "ओपन करो", "ओपन कर", "ओपन कर दो", "ओपन कर दे", "ओपन कीजिए",
        "स्टार्ट करो", "स्टार्ट कर", "स्टार्ट कर दो", "स्टार्ट कर दे",
        "लॉन्च करो", "लॉन्च कर", "लॉन्च कर दो", "लॉन्च कर दे",
        "चालू करो", "चालू कर", "चालू कर दो", "चालू कर दे",
        // English / Roman Hindi multi-word phrases
        "khol do", "khol de", "khol dena", "khol dijiye",
        "chala do", "chala de", "chala dena", "chala dijiye",
        "open karo", "open kar do", "open kar de",
        "start karo", "start kar do", "start kar de",
        "launch karo", "launch kar do", "launch kar de",
        "chalu karo", "chalu kar do", "chalu kar de"
    )

    // Single greeting, trigger, and command filler words in English & Devanagari
    private val STRIP_WORDS = setOf(
        // English / Hinglish wake & filler words
        "max", "hello", "hey", "suno", "hi", "namaste", "bhai", "yaar", "are", "oye",
        "open", "kholo", "khol", "kholiye", "start", "launch", "chalao", "chala",
        "please", "plz", "zarur", "jaldi", "run", "execute", "karo", "kar", "do", "de",
        "app", "application", "ko", "se", "pe", "par", "the", "a", "an",
        // Devanagari wake words & greetings
        "मैक्स", "हेलो", "हे", "सुनो", "सुन", "हाय", "नमस्ते", "भाई", "यार", "अरे", "ओए",
        // Devanagari action / filler words
        "खोलो", "खोल", "खोलिए", "ओपन", "स्टार्ट", "लॉन्च", "चलाओ", "चला", "चालू",
        "करो", "कर", "दो", "दे", "दीजिए", "प्लीज", "कृपया", "जल्दी", "जरूर",
        "ऐप", "एप्प", "एप्लिकेशन", "को", "से", "पे", "पर", "का", "की", "के", "वाला", "वाली", "वाले"
    )

    /**
     * PHONETIC MAPPING TABLE:
     * Maps common Devanagari (Hindi) app names and spelling variations directly
     * to English app name keywords and package name tokens.
     */
    private val DEVANAGARI_PHONETIC_MAP = mapOf<String, List<String>>(
        // YouTube
        "यूट्यूब" to listOf("youtube", "com.google.android.youtube"),
        "युटुब" to listOf("youtube", "com.google.android.youtube"),
        "यूटुब" to listOf("youtube", "com.google.android.youtube"),
        "युट्यूब" to listOf("youtube", "com.google.android.youtube"),
        "यूटुबे" to listOf("youtube", "com.google.android.youtube"),
        "यू ट्यूब" to listOf("youtube", "com.google.android.youtube"),
        "यु ट्यूब" to listOf("youtube", "com.google.android.youtube"),
        "यूटियूब" to listOf("youtube", "com.google.android.youtube"),
        "युट्युब" to listOf("youtube", "com.google.android.youtube"),
        "यू-ट्यूब" to listOf("youtube", "com.google.android.youtube"),

        // WhatsApp
        "व्हाट्सएप" to listOf("whatsapp", "com.whatsapp"),
        "वॉट्सएप" to listOf("whatsapp", "com.whatsapp"),
        "वाट्सएप" to listOf("whatsapp", "com.whatsapp"),
        "व्हाट्सऐप" to listOf("whatsapp", "com.whatsapp"),
        "वॉट्सऐप" to listOf("whatsapp", "com.whatsapp"),
        "वाट्सऐप" to listOf("whatsapp", "com.whatsapp"),
        "व्हाट्स अप" to listOf("whatsapp", "com.whatsapp"),
        "व्हाट्सअप" to listOf("whatsapp", "com.whatsapp"),
        "व्हाट्सप" to listOf("whatsapp", "com.whatsapp"),
        "व्हाटसप" to listOf("whatsapp", "com.whatsapp"),
        "वाट्सअप" to listOf("whatsapp", "com.whatsapp"),
        "व्हाटसएप" to listOf("whatsapp", "com.whatsapp"),
        "व्हाट्स एप्प" to listOf("whatsapp", "com.whatsapp"),

        // Google Chrome / Browser
        "क्रोम" to listOf("chrome", "com.android.chrome"),
        "गूगल क्रोम" to listOf("chrome", "com.android.chrome"),
        "गूगलक्रोम" to listOf("chrome", "com.android.chrome"),
        "ब्राउज़र" to listOf("chrome", "browser", "com.android.chrome"),
        "ब्राउजर" to listOf("chrome", "browser", "com.android.chrome"),

        // Instagram
        "इंस्टाग्राम" to listOf("instagram", "com.instagram.android"),
        "इन्स्टाग्राम" to listOf("instagram", "com.instagram.android"),
        "इंस्टा" to listOf("instagram", "com.instagram.android"),
        "इन्स्टा" to listOf("instagram", "com.instagram.android"),
        "इन्सटाग्राम" to listOf("instagram", "com.instagram.android"),

        // Facebook
        "फेसबुक" to listOf("facebook", "com.facebook.katana", "com.facebook.lite"),
        "फ़ेसबुक" to listOf("facebook", "com.facebook.katana", "com.facebook.lite"),
        "फेस बुक" to listOf("facebook", "com.facebook.katana", "com.facebook.lite"),
        "एफबी" to listOf("facebook", "com.facebook.katana"),

        // Camera
        "कैमरा" to listOf("camera", "com.android.camera", "com.google.android.GoogleCamera"),
        "कैमरे" to listOf("camera", "com.android.camera"),
        "कैम" to listOf("camera"),
        "फोटो खींचने वाला" to listOf("camera"),
        "फ़ोटो खींचने वाला" to listOf("camera"),

        // Settings
        "सेटिंग्स" to listOf("settings", "com.android.settings"),
        "सेटिंग" to listOf("settings", "com.android.settings"),
        "सैटिंग्स" to listOf("settings", "com.android.settings"),
        "सैटिंग" to listOf("settings", "com.android.settings"),

        // Google Maps
        "मैप्स" to listOf("maps", "com.google.android.apps.maps"),
        "मैप" to listOf("maps", "com.google.android.apps.maps"),
        "गूगल मैप्स" to listOf("maps", "com.google.android.apps.maps"),
        "गूगल मैप" to listOf("maps", "com.google.android.apps.maps"),
        "नक्शा" to listOf("maps", "com.google.android.apps.maps"),
        "नक्शे" to listOf("maps", "com.google.android.apps.maps"),

        // Gmail & Email
        "जीमेल" to listOf("gmail", "com.google.android.gm"),
        "जी मेल" to listOf("gmail", "com.google.android.gm"),
        "ईमेल" to listOf("gmail", "email", "com.google.android.gm"),
        "इमेल" to listOf("gmail", "email", "com.google.android.gm"),
        "ई मेल" to listOf("gmail", "email"),
        "मेल" to listOf("gmail", "email"),

        // Photos & Gallery
        "गैलरी" to listOf("gallery", "photos", "com.google.android.apps.photos"),
        "गेलरी" to listOf("gallery", "photos"),
        "फोटो" to listOf("photos", "gallery", "com.google.android.apps.photos"),
        "फ़ोटो" to listOf("photos", "gallery", "com.google.android.apps.photos"),
        "गूगल फोटोज" to listOf("photos", "com.google.android.apps.photos"),
        "गूगल फोटो" to listOf("photos", "com.google.android.apps.photos"),
        "तस्वीरें" to listOf("photos", "gallery"),
        "तस्वीर" to listOf("photos", "gallery"),

        // Phone & Dialer
        "फोन" to listOf("dialer", "phone", "com.google.android.dialer", "com.android.dialer"),
        "फ़ोन" to listOf("dialer", "phone", "com.google.android.dialer", "com.android.dialer"),
        "डायलर" to listOf("dialer", "phone", "com.google.android.dialer"),
        "कॉल" to listOf("dialer", "phone"),

        // Messages & SMS
        "मैसेज" to listOf("messaging", "message", "com.google.android.apps.messaging", "sms"),
        "मेसेज" to listOf("messaging", "message", "com.google.android.apps.messaging"),
        "मैसेजिंग" to listOf("messaging", "message"),
        "संदेश" to listOf("messaging", "message"),
        "एसएमएस" to listOf("messaging", "message", "sms"),

        // Contacts
        "कॉन्टैक्ट्स" to listOf("contacts", "com.google.android.contacts"),
        "कॉन्टैक्ट" to listOf("contacts", "com.google.android.contacts"),
        "कांटेक्ट" to listOf("contacts", "com.google.android.contacts"),
        "कांटेक्ट्स" to listOf("contacts", "com.google.android.contacts"),
        "संपर्क" to listOf("contacts", "com.google.android.contacts"),

        // Calculator
        "कैलकुलेटर" to listOf("calculator", "com.google.android.calculator", "calc"),
        "केलकुलेटर" to listOf("calculator", "com.google.android.calculator"),
        "कैलकुलेशन" to listOf("calculator"),
        "हिसाब" to listOf("calculator"),

        // Clock & Alarm
        "क्लॉक" to listOf("clock", "deskclock", "alarm", "com.google.android.deskclock"),
        "घड़ी" to listOf("clock", "deskclock", "alarm", "com.google.android.deskclock"),
        "घडी" to listOf("clock", "deskclock", "alarm"),
        "अलार्म" to listOf("clock", "alarm", "deskclock"),

        // Calendar
        "कैलेंडर" to listOf("calendar", "com.google.android.calendar"),
        "केलेंडर" to listOf("calendar", "com.google.android.calendar"),
        "पंचांग" to listOf("calendar", "com.google.android.calendar"),

        // Files & File Manager
        "फाइल्स" to listOf("files", "filemanager", "com.google.android.apps.nbu.files"),
        "फ़ाइल्स" to listOf("files", "filemanager", "com.google.android.apps.nbu.files"),
        "फाइल" to listOf("files", "filemanager"),
        "फ़ाइल" to listOf("files", "filemanager"),
        "फाइल मैनेजर" to listOf("files", "filemanager"),
        "फ़ाइल मैनेजर" to listOf("files", "filemanager"),

        // Google Play Store
        "प्ले स्टोर" to listOf("vending", "playstore", "play store", "com.android.vending"),
        "प्लेस्टोर" to listOf("vending", "playstore", "play store", "com.android.vending"),
        "गूगल प्ले" to listOf("vending", "playstore", "com.android.vending"),
        "स्टोर" to listOf("vending", "playstore"),

        // Spotify & Music
        "स्पॉटिफ़ाई" to listOf("spotify", "com.spotify.music"),
        "स्पॉटिफाइ" to listOf("spotify", "com.spotify.music"),
        "स्पॉटीफाई" to listOf("spotify", "com.spotify.music"),
        "स्पॉटिफाई" to listOf("spotify", "com.spotify.music"),
        "म्यूजिक" to listOf("music", "spotify", "jiosaavn", "gaana", "wynk"),
        "म्यूज़िक" to listOf("music", "spotify", "jiosaavn", "gaana"),
        "गाना" to listOf("gaana", "music", "spotify"),
        "गाने" to listOf("gaana", "music", "spotify"),
        "जियो सावन" to listOf("jiosaavn", "saavn"),
        "विंक" to listOf("wynk", "music"),

        // Social Media & Messaging
        "ट्विटर" to listOf("twitter", "x", "com.twitter.android"),
        "टविटर" to listOf("twitter", "x", "com.twitter.android"),
        "एक्स" to listOf("twitter", "x", "com.twitter.android"),
        "टेलीग्राम" to listOf("telegram", "org.telegram.messenger"),
        "टेली ग्राम" to listOf("telegram", "org.telegram.messenger"),
        "स्नैपचैट" to listOf("snapchat", "com.snapchat.android"),
        "स्नेपचैट" to listOf("snapchat", "com.snapchat.android"),
        "स्नेप चैट" to listOf("snapchat", "com.snapchat.android"),
        "ट्रूकॉलर" to listOf("truecaller", "com.truecaller"),
        "ट्रू कॉलर" to listOf("truecaller", "com.truecaller"),

        // Shopping
        "फ्लिपकार्ट" to listOf("flipkart", "com.flipkart.android"),
        "फ्लिप कार्ट" to listOf("flipkart", "com.flipkart.android"),
        "अमेज़न" to listOf("amazon", "in.amazon.mShop.android.shopping"),
        "अमेजन" to listOf("amazon", "in.amazon.mShop.android.shopping"),
        "अमेज़ॉन" to listOf("amazon", "in.amazon.mShop.android.shopping"),
        "मीशो" to listOf("meesho", "com.meesho.supply"),

        // Payments & UPI
        "गूगल पे" to listOf("gpay", "google.android.apps.nbu.paisa.user", "paisa"),
        "जीपे" to listOf("gpay", "google.android.apps.nbu.paisa.user", "paisa"),
        "जी पे" to listOf("gpay", "google.android.apps.nbu.paisa.user", "paisa"),
        "फोन पे" to listOf("phonepe", "com.phonepe.app"),
        "फोनपे" to listOf("phonepe", "com.phonepe.app"),
        "फ़ोनपे" to listOf("phonepe", "com.phonepe.app"),
        "फ़ोन पे" to listOf("phonepe", "com.phonepe.app"),
        "पेटीएम" to listOf("paytm", "net.one97.paytm"),
        "पे टीएम" to listOf("paytm", "net.one97.paytm"),

        // Streaming & Entertainment
        "नेटफ्लिक्स" to listOf("netflix", "com.netflix.mediaclient"),
        "नेटफ़्लिक्स" to listOf("netflix", "com.netflix.mediaclient"),
        "हॉटस्टार" to listOf("hotstar", "in.startv.hotstar", "disney"),
        "हॉट स्टार" to listOf("hotstar", "in.startv.hotstar"),
        "जियो सिनेमा" to listOf("jiocinema", "com.jio.media.ondemand"),
        "जियोसिनेमा" to listOf("jiocinema", "com.jio.media.ondemand"),

        // Productivity & Utilities
        "कीप" to listOf("keep", "notes", "com.google.android.keep"),
        "कीप नोट्स" to listOf("keep", "notes", "com.google.android.keep"),
        "नोट्स" to listOf("notes", "keep", "com.google.android.keep"),
        "नोट" to listOf("notes", "keep"),
        "ड्राइव" to listOf("drive", "com.google.android.apps.docs"),
        "गूगल ड्राइव" to listOf("drive", "com.google.android.apps.docs"),
        "रिकॉर्डर" to listOf("soundrecorder", "recorder", "com.google.android.apps.recorder"),
        "वॉइस रिकॉर्डर" to listOf("soundrecorder", "recorder"),
        "मौसम" to listOf("weather", "com.google.android.apps.weather")
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
     * Strips "Max", "hello", "hey", "suno", "open", "kholo", "खोलो", "ओपन करो", etc.
     * from anywhere in the command.
     */
    fun sanitizeCommand(rawText: String): String {
        var text = rawText.lowercase(Locale.getDefault())
            .replace(Regex("[^a-zA-Z0-9\\s\\u0900-\\u097F]"), " ") // keep alphanumeric and Devanagari

        // 1. Remove multi-word phrases first
        for (phrase in MULTI_WORD_STRIP_PHRASES) {
            val escaped = Regex.escape(phrase)
            text = text.replace(Regex("(?i)\\b$escaped\\b"), " ")
            text = text.replace(Regex("(?i)$escaped"), " ")
        }

        // 2. Split into tokens and filter out any stripped single words
        val tokens = text.split("\\s+".toRegex()).filter { token ->
            val cleanToken = token.trim()
            cleanToken.isNotBlank() && cleanToken !in STRIP_WORDS
        }

        val cleaned = tokens.joinToString(" ").trim()
        return if (cleaned.isNotBlank()) cleaned else rawText.trim()
    }

    /**
     * Fuzzy matches the query against installed apps:
     * 1. Wake-words and fillers stripped
     * 2. PHONETIC MAPPING TABLE match first (for Devanagari names like "यूट्यूब", "क्रोम", "व्हाट्सएप")
     * 3. Direct fuzzy-match (exact, startsWith, contains, token match, Levenshtein distance)
     * 4. Devanagari-to-Latin transliteration fallback matching
     */
    fun fuzzyMatchApp(query: String, apps: List<InstalledApp>): InstalledApp? {
        if (apps.isEmpty()) return null

        val cleanQuery = sanitizeCommand(query).lowercase(Locale.getDefault()).trim()
        if (cleanQuery.isBlank()) return null

        // --- STEP 1: Devanagari Phonetic Mapping Table Check ---
        val phoneticMatch = matchViaPhoneticTable(cleanQuery, apps)
        if (phoneticMatch != null) return phoneticMatch

        // Also check if any single token or substring in the cleanQuery matches the phonetic map
        val tokens = cleanQuery.split(" ").filter { it.isNotBlank() }
        for (token in tokens) {
            val tokenMatch = matchViaPhoneticTable(token, apps)
            if (tokenMatch != null) return tokenMatch
        }

        // Check if raw query (before sanitizing) had a phonetic map entry
        val rawLower = query.lowercase(Locale.getDefault()).trim()
        for ((devanagariKey, _) in DEVANAGARI_PHONETIC_MAP) {
            if (rawLower.contains(devanagariKey)) {
                val match = matchViaPhoneticTable(devanagariKey, apps)
                if (match != null) return match
            }
        }

        // --- STEP 2: Normal Local Fuzzy Match against Installed Apps ---
        val directMatch = runLocalFuzzyMatch(cleanQuery, apps)
        if (directMatch != null) return directMatch

        // --- STEP 3: Transliterated Devanagari -> Latin Match ---
        val transliterated = transliterateDevanagariToLatin(cleanQuery)
        if (transliterated.isNotBlank() && transliterated != cleanQuery) {
            val translitMatch = runLocalFuzzyMatch(transliterated, apps)
            if (translitMatch != null) return translitMatch
        }

        // Fallback: Try with rawQuery if cleanQuery didn't match
        if (cleanQuery != rawLower) {
            val rawMatch = runLocalFuzzyMatch(rawLower, apps)
            if (rawMatch != null) return rawMatch
        }

        return null
    }

    /**
     * Checks if the query or keyword exists in DEVANAGARI_PHONETIC_MAP and matches installed apps
     */
    private fun matchViaPhoneticTable(devanagariQuery: String, apps: List<InstalledApp>): InstalledApp? {
        val mappedTargets = DEVANAGARI_PHONETIC_MAP[devanagariQuery]
            ?: DEVANAGARI_PHONETIC_MAP.entries.firstOrNull { (key, _) ->
                devanagariQuery.contains(key) || key.contains(devanagariQuery)
            }?.value
            ?: return null

        // For each mapped target (e.g. "youtube", "com.google.android.youtube")
        for (target in mappedTargets) {
            val normalizedTarget = target.lowercase(Locale.getDefault())

            // 1. Check exact package match
            val exactPkg = apps.find { it.packageName.equals(normalizedTarget, ignoreCase = true) }
            if (exactPkg != null) return exactPkg

            // 2. Check package name contains
            val pkgContains = apps.find { it.packageName.lowercase(Locale.getDefault()).contains(normalizedTarget) }
            if (pkgContains != null) return pkgContains

            // 3. Check app name matches target
            val appMatch = apps.find { app ->
                val appNorm = app.normalizedName
                appNorm == normalizedTarget ||
                        appNorm.startsWith(normalizedTarget) ||
                        appNorm.contains(normalizedTarget)
            }
            if (appMatch != null) return appMatch

            // 4. Check fuzzy match with the English mapped target
            val fuzzyTarget = runLocalFuzzyMatch(normalizedTarget, apps)
            if (fuzzyTarget != null) return fuzzyTarget
        }

        return null
    }

    /**
     * Standard local fuzzy matching algorithm
     */
    private fun runLocalFuzzyMatch(targetQuery: String, apps: List<InstalledApp>): InstalledApp? {
        val query = targetQuery.trim().lowercase(Locale.getDefault())
        if (query.isBlank()) return null

        // 1. Exact match with app name
        val exactMatch = apps.find { it.normalizedName.equals(query, ignoreCase = true) }
        if (exactMatch != null) return exactMatch

        // 2. Exact match with package name ending (e.g. "youtube" matches "com.google.android.youtube")
        val packageExact = apps.find { app ->
            val pkgLastPart = app.packageName.substringAfterLast(".").lowercase(Locale.getDefault())
            pkgLastPart == query || app.packageName.lowercase(Locale.getDefault()).endsWith(".$query")
        }
        if (packageExact != null) return packageExact

        // 3. App name starts with query
        val startsWithMatch = apps.find { it.normalizedName.startsWith(query) }
        if (startsWithMatch != null) return startsWithMatch

        // 4. App name contains query as substring or vice versa
        val containsMatch = apps.find { app ->
            app.normalizedName.contains(query) || (query.length > 3 && query.contains(app.normalizedName))
        }
        if (containsMatch != null) return containsMatch

        // 5. Package name contains query
        val pkgContains = apps.find { it.packageName.lowercase(Locale.getDefault()).contains(query) }
        if (pkgContains != null) return pkgContains

        // 6. Word-level token match (e.g. "Google Maps" vs "Maps")
        val queryTokens = query.split(" ").filter { it.isNotBlank() }
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
            val dist = levenshteinDistance(query, app.normalizedName)
            val maxAllowed = when {
                query.length <= 3 -> 1
                query.length <= 6 -> 2
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
     * Converts Devanagari characters phonetically into Latin script for fallback matching.
     */
    fun transliterateDevanagariToLatin(input: String): String {
        val mapping = mapOf(
            "अ" to "a", "आ" to "aa", "इ" to "i", "ई" to "ee", "उ" to "u", "ऊ" to "oo",
            "ए" to "e", "ऐ" to "ai", "ओ" to "o", "औ" to "au", "ऋ" to "ri",
            "क" to "k", "ख" to "kh", "ग" to "g", "घ" to "gh", "ङ" to "ng",
            "च" to "ch", "छ" to "chh", "ज" to "j", "झ" to "jh", "ञ" to "ny",
            "ट" to "t", "ठ" to "th", "ड" to "d", "ढ" to "dh", "ण" to "n",
            "त" to "t", "थ" to "th", "द" to "d", "ध" to "dh", "न" to "n",
            "प" to "p", "फ" to "ph", "ब" to "b", "भ" to "bh", "म" to "m",
            "य" to "y", "र" to "r", "ल" to "l", "व" to "v", "श" to "sh",
            "ष" to "sh", "स" to "s", "ह" to "h",
            "ा" to "a", "ि" to "i", "ी" to "ee", "ु" to "u", "ू" to "oo",
            "े" to "e", "ै" to "ai", "ो" to "o", "ौ" to "au", "्" to "",
            "ं" to "n", "ः" to "h", "ँ" to "n", "़" to "", "ऑ" to "o", "ॉ" to "o"
        )

        val result = StringBuilder()
        var i = 0
        while (i < input.length) {
            val charStr = input[i].toString()
            if (mapping.containsKey(charStr)) {
                result.append(mapping[charStr])
            } else {
                result.append(input[i])
            }
            i++
        }
        return result.toString().trim()
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
