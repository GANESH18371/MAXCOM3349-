package com.example.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.util.DebugLogger
import com.example.util.TtsManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class WeatherInfo(
    val temperature: Double,
    val weatherCode: Int,
    val windSpeed: Double,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val cityName: String = ""
)

object WeatherManager {
    private const val TAG = "WeatherManager"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _currentWeather = MutableStateFlow<WeatherInfo?>(null)
    val currentWeather: StateFlow<WeatherInfo?> = _currentWeather.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Offline coordinate presets for major cities in India and abroad
    private val PRESET_CITIES = mapOf(
        "delhi" to Pair(28.6139, 77.2090),
        "new delhi" to Pair(28.6139, 77.2090),
        "mumbai" to Pair(19.0760, 72.8777),
        "bangalore" to Pair(12.9716, 77.5946),
        "bengaluru" to Pair(12.9716, 77.5946),
        "kolkata" to Pair(22.5726, 88.3639),
        "chennai" to Pair(13.0827, 80.2707),
        "hyderabad" to Pair(17.3850, 78.4867),
        "pune" to Pair(18.5204, 73.8567),
        "ahmedabad" to Pair(23.0225, 72.5714),
        "jaipur" to Pair(26.9124, 75.7873),
        "lucknow" to Pair(26.8467, 80.9462),
        "kanpur" to Pair(26.4499, 80.3319),
        "indore" to Pair(22.7196, 75.8577),
        "bhopal" to Pair(23.2599, 77.4126),
        "patna" to Pair(25.6127, 85.1588),
        "chandigarh" to Pair(30.7333, 76.7794),
        "noida" to Pair(28.5355, 77.3910),
        "gurgaon" to Pair(28.4595, 77.0266),
        "gurugram" to Pair(28.4595, 77.0266),
        "agra" to Pair(27.1767, 78.0081),
        "varanasi" to Pair(25.3176, 82.9739),
        "surat" to Pair(21.1702, 72.8311),
        "goa" to Pair(15.2993, 74.1240),
        "shimla" to Pair(31.1048, 77.1734),
        "dehradun" to Pair(30.3165, 78.0322),
        "srinagar" to Pair(34.0837, 74.7973),
        "ranchi" to Pair(23.3441, 85.3096),
        "guwahati" to Pair(26.1445, 91.7362),
        "kochi" to Pair(9.9312, 76.2673),
        "nagpur" to Pair(21.1458, 79.0882),
        "jodhpur" to Pair(26.2389, 73.0243),
        "udaipur" to Pair(24.5854, 73.7125),
        "gwalior" to Pair(26.2183, 78.1828),
        "ayodhya" to Pair(26.7922, 82.1998),
        "prayagraj" to Pair(25.4358, 81.8463),
        "allahabad" to Pair(25.4358, 81.8463)
    )

    private val HINDI_CITY_MAP = mapOf(
        "दिल्ली" to "Delhi",
        "नई दिल्ली" to "New Delhi",
        "मुंबई" to "Mumbai",
        "बेंगलुरु" to "Bengaluru",
        "बैंगलोर" to "Bangalore",
        "कोलकाता" to "Kolkata",
        "चेन्नई" to "Chennai",
        "हैदराबाद" to "Hyderabad",
        "पुणे" to "Pune",
        "अहमदाबाद" to "Ahmedabad",
        "जयपुर" to "Jaipur",
        "लखनऊ" to "Lucknow",
        "कानपुर" to "Kanpur",
        "इंदौर" to "Indore",
        "भोपाल" to "Bhopal",
        "पटना" to "Patna",
        "चंडीगढ़" to "Chandigarh",
        "नोएडा" to "Noida",
        "गुड़गांव" to "Gurgaon",
        "गुरुग्राम" to "Gurugram",
        "आगरा" to "Agra",
        "वाराणसी" to "Varanasi",
        "सूरत" to "Surat",
        "गोवा" to "Goa",
        "शिमला" to "Shimla",
        "देहरादून" to "Dehradun",
        "श्रीनगर" to "Srinagar",
        "रांची" to "Ranchi",
        "गुवाहाटी" to "Guwahati",
        "कोच्चि" to "Kochi",
        "नागपुर" to "Nagpur",
        "जोधपुर" to "Jodhpur",
        "उदयपुर" to "Udaipur",
        "अयोध्या" to "Ayodhya",
        "प्रयागराज" to "Prayagraj"
    )

    /**
     * Checks if the voice command text is asking for weather.
     */
    fun isWeatherCommand(lower: String): Boolean {
        val keywords = listOf(
            "mausam", "weather", "tapman", "temprature", "temperature",
            "baarish", "mosam", "मौसम", "तापमान", "बारिश", "धूप"
        )
        val queryPhrases = listOf(
            "kaisa hai", "kaisa rahega", "kitna hai", "kya hai", "today", "aaj",
            "batao", "what", "how", "forecast", "hogi kya"
        )
        val hasKeyword = keywords.any { lower.contains(it) }
        val hasQuery = queryPhrases.any { lower.contains(it) }
        return hasKeyword && (hasQuery || lower.contains("weather") || lower.contains("mausam") || lower.contains("मौसम"))
    }

    /**
     * Extracts explicit city or place name from the user's command if mentioned.
     */
    fun extractCityFromCommand(lower: String): String? {
        // 1. Check Hindi city map
        for ((hindiName, englishName) in HINDI_CITY_MAP) {
            if (lower.contains(hindiName)) {
                return englishName
            }
        }

        // 2. Check preset English cities
        for (city in PRESET_CITIES.keys) {
            if (lower.contains(Regex("\\b$city\\b"))) {
                return city.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }

        // 3. Pattern matching like "[city] ka mausam", "[city] me mausam", "weather in [city]"
        val patterns = listOf(
            Regex("([a-zA-Z\\u0900-\\u097F]+)\\s+(?:ka|ki|ke|me|mein|par|pe)\\s+(?:mausam|weather|tapman|mosam)"),
            Regex("(?:mausam|weather|tapman)\\s+([a-zA-Z\\u0900-\\u097F]+)\\s+(?:ka|ki|ke|me|mein|par|pe)"),
            Regex("(?:weather|mausam)\\s+(?:in|of|at)\\s+([a-zA-Z\\u0900-\\u097F]+)"),
            Regex("([a-zA-Z\\u0900-\\u097F]+)\\s+(?:ka|ki|ke)\\s+(?:batao|bataiye)")
        )

        for (pattern in patterns) {
            val match = pattern.find(lower)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                val stopWords = setOf("aaj", "kal", "abhi", "today", "mera", "mere", "yaha", "yahan", "waha", "wahan")
                if (candidate.length >= 3 && !stopWords.contains(candidate.lowercase(Locale.getDefault()))) {
                    return candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }
        }

        return null
    }

    /**
     * Checks if location permission is granted.
     */
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Resolves city coordinates via presets or Open-Meteo Geocoding API.
     */
    suspend fun resolveCityCoordinates(cityName: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val cleanName = cityName.trim().lowercase(Locale.getDefault())
        PRESET_CITIES[cleanName]?.let { return@withContext it }

        // Geocoding API call
        try {
            val encoded = URLEncoder.encode(cityName.trim(), "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
            val req = Request.Builder().url(url).get().build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val first = results.getJSONObject(0)
                    val lat = first.getDouble("latitude")
                    val lon = first.getDouble("longitude")
                    return@withContext Pair(lat, lon)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoding failed for $cityName", e)
        }
        return@withContext null
    }

    /**
     * Fetches and announces weather for a specific named city.
     */
    fun fetchAndAnnounceWeatherForCity(
        context: Context,
        cityName: String,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        _isLoading.value = true
        scope.launch {
            val coords = resolveCityCoordinates(cityName) ?: Pair(28.6139, 77.2090)
            processWeatherForLocation(
                lat = coords.first,
                lon = coords.second,
                onComplete = onComplete,
                cityName = cityName,
                isDefault = false
            )
        }
    }

    /**
     * Fetches current location and calls Open-Meteo API.
     * Speaks the weather report in natural Hindi via TTS.
     */
    fun fetchAndAnnounceWeather(
        context: Context,
        onPermissionNeeded: (() -> Unit)? = null,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        if (!hasLocationPermission(context)) {
            DebugLogger.logInfo("Location permission required for weather")
            TtsManager.speakIfVoiceReady("मौसम जानने के लिए लोकेशन परमिशन ज़रूरी है. कृपया स्क्रीन पर परमिशन दें.", caller = "Weather")
            onPermissionNeeded?.invoke()
            onComplete?.invoke(false, "Location permission missing")
            return
        }

        _isLoading.value = true
        val fusedClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        scope.launch {
            try {
                // Try to get current high-accuracy / balanced location
                val cts = CancellationTokenSource()
                fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc: Location? ->
                        scope.launch {
                            if (loc != null) {
                                processWeatherForLocation(loc.latitude, loc.longitude, onComplete)
                            } else {
                                // Fallback to last known location
                                fusedClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                                    scope.launch {
                                        if (lastLoc != null) {
                                            processWeatherForLocation(lastLoc.latitude, lastLoc.longitude, onComplete)
                                        } else {
                                            Log.w(TAG, "No GPS location available, using default coordinates")
                                            processWeatherForLocation(28.6139, 77.2090, onComplete, isDefault = true)
                                        }
                                    }
                                }.addOnFailureListener {
                                    scope.launch {
                                        processWeatherForLocation(28.6139, 77.2090, onComplete, isDefault = true)
                                    }
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        scope.launch {
                            Log.e(TAG, "Failed to get current location", e)
                            processWeatherForLocation(28.6139, 77.2090, onComplete, isDefault = true)
                        }
                    }
            } catch (e: SecurityException) {
                _isLoading.value = false
                DebugLogger.logWeatherApiCall(false, "SecurityException: Location permission denied")
                val msg = "Location permission ki zaroorat hai."
                TtsManager.speakIfVoiceReady(msg, caller = "Weather")
                onComplete?.invoke(false, msg)
            } catch (e: Exception) {
                _isLoading.value = false
                Log.e(TAG, "Unexpected error getting location", e)
                DebugLogger.logWeatherApiCall(false, e.message ?: "Unknown error")
                val msg = "Mausam janne me samasya aayi."
                TtsManager.speakIfVoiceReady(msg, caller = "Weather")
                onComplete?.invoke(false, msg)
            }
        }
    }

    private suspend fun processWeatherForLocation(
        lat: Double,
        lon: Double,
        onComplete: ((Boolean, String) -> Unit)?,
        cityName: String? = null,
        isDefault: Boolean = false
    ) {
        // Required exact log: "WEATHER_LOCATION: lat=<>, lon=<>"
        DebugLogger.logWeatherLocation(lat, lon)

        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                _isLoading.value = false
                // Required exact log: "WEATHER_API_CALL: success/fail"
                DebugLogger.logWeatherApiCall(false, "HTTP ${response.code}")
                val errorMsg = "Mausam server se jankari nahi mil paayi."
                TtsManager.speakIfVoiceReady(errorMsg, caller = "Weather")
                onComplete?.invoke(false, errorMsg)
                return
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val currentWeatherJson = json.getJSONObject("current_weather")

            val temperature = currentWeatherJson.getDouble("temperature")
            val weatherCode = currentWeatherJson.getInt("weathercode")
            val windSpeed = currentWeatherJson.getDouble("windspeed")

            val weatherDescHindi = getWeatherDescriptionHindi(weatherCode)

            val info = WeatherInfo(
                temperature = temperature,
                weatherCode = weatherCode,
                windSpeed = windSpeed,
                description = weatherDescHindi,
                latitude = lat,
                longitude = lon,
                cityName = cityName ?: if (isDefault) "Delhi" else ""
            )
            _currentWeather.value = info
            _isLoading.value = false

            // Required exact log: "WEATHER_API_CALL: success/fail"
            DebugLogger.logWeatherApiCall(true, "Temp: ${temperature}°C, Code: $weatherCode")

            // Natural Hindi TTS
            val roundedTemp = Math.round(temperature).toInt()
            val speechText = if (!cityName.isNullOrBlank()) {
                "आज $cityName में तापमान $roundedTemp डिग्री सेल्सियस है और मौसम $weatherDescHindi है."
            } else if (isDefault) {
                "आज तापमान $roundedTemp डिग्री सेल्सियस है और मौसम $weatherDescHindi है."
            } else {
                "आज आपके यहाँ तापमान $roundedTemp डिग्री सेल्सियस है और मौसम $weatherDescHindi है."
            }

            TtsManager.speakIfVoiceReady(speechText, caller = "Weather")
            onComplete?.invoke(true, speechText)

        } catch (e: Exception) {
            _isLoading.value = false
            Log.e(TAG, "Error fetching weather API", e)
            // Required exact log: "WEATHER_API_CALL: success/fail"
            DebugLogger.logWeatherApiCall(false, e.message ?: "Network error")
            val errorMsg = "Mausam prapt karne me samasya aayi. Kripya internet check karein."
            TtsManager.speakIfVoiceReady(errorMsg, caller = "Weather")
            onComplete?.invoke(false, errorMsg)
        }
    }

    fun getWeatherDescriptionHindi(code: Int): String {
        return when (code) {
            0 -> "साफ़"
            1 -> "लगभग साफ़"
            2 -> "हल्के बादल"
            3 -> "बादल छाए हुए"
            45, 48 -> "कोहरा"
            51, 53, 55 -> "हल्की बूँदा-बाँदी"
            61, 63 -> "बारिश"
            65 -> "तेज़ बारिश"
            71, 73, 75 -> "बर्फ़बारी"
            80, 81, 82 -> "बारिश के झोंके"
            95, 96, 99 -> "तूफानी बारिश और गरज"
            else -> "सामान्य"
        }
    }
}
