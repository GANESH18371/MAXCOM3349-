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
            TtsManager.speak("मौसम जानने के लिए लोकेशन परमिशन ज़रूरी है. कृपया स्क्रीन पर परमिशन दें.")
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
                                            // Fallback default coordinates (e.g. New Delhi) with notice
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
                TtsManager.speak(msg)
                onComplete?.invoke(false, msg)
            } catch (e: Exception) {
                _isLoading.value = false
                Log.e(TAG, "Unexpected error getting location", e)
                DebugLogger.logWeatherApiCall(false, e.message ?: "Unknown error")
                val msg = "Mausam janne me samasya aayi."
                TtsManager.speak(msg)
                onComplete?.invoke(false, msg)
            }
        }
    }

    private suspend fun processWeatherForLocation(
        lat: Double,
        lon: Double,
        onComplete: ((Boolean, String) -> Unit)?,
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
                TtsManager.speak(errorMsg)
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
                longitude = lon
            )
            _currentWeather.value = info
            _isLoading.value = false

            // Required exact log: "WEATHER_API_CALL: success/fail"
            DebugLogger.logWeatherApiCall(true, "Temp: ${temperature}°C, Code: $weatherCode")

            // Natural Hindi TTS
            val roundedTemp = Math.round(temperature).toInt()
            val speechText = if (isDefault) {
                "आज तापमान $roundedTemp डिग्री सेल्सियस है और मौसम $weatherDescHindi है."
            } else {
                "आज आपके यहाँ तापमान $roundedTemp डिग्री सेल्सियस है और मौसम $weatherDescHindi है."
            }

            TtsManager.speak(speechText)
            onComplete?.invoke(true, speechText)

        } catch (e: Exception) {
            _isLoading.value = false
            Log.e(TAG, "Error fetching weather API", e)
            // Required exact log: "WEATHER_API_CALL: success/fail"
            DebugLogger.logWeatherApiCall(false, e.message ?: "Network error")
            val errorMsg = "Mausam prapt karne me samasya aayi. Kripya internet check karein."
            TtsManager.speak(errorMsg)
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
