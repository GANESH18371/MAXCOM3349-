package com.example.manager

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.receiver.MaxDeviceAdminReceiver
import com.example.util.DebugLogger
import com.example.util.TtsManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TrustedContact(
    val name: String = "",
    val phoneNumber: String = "",
    val email: String = ""
)

data class TheftIncident(
    val id: Long = System.currentTimeMillis(),
    val timestamp: String,
    val triggerType: String, // "WRONG_PASSWORD" or "SIM_CHANGE" or "TEST"
    val photoPath: String?,
    val locationUrl: String?,
    val alertSent: Boolean
)

object AntiTheftManager {
    private const val TAG = "AntiTheftManager"
    private const val PREFS_NAME = "max_anti_theft_prefs"
    private const val KEY_CONTACT_NAME = "trusted_contact_name"
    private const val KEY_CONTACT_PHONE = "trusted_contact_phone"
    private const val KEY_CONTACT_EMAIL = "trusted_contact_email"
    private const val KEY_LAST_SIM_SERIAL = "last_sim_serial"
    private const val KEY_GUARD_ENABLED = "guard_enabled"

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _trustedContact = MutableStateFlow(TrustedContact())
    val trustedContact: StateFlow<TrustedContact> = _trustedContact.asStateFlow()

    private val _isGuardEnabled = MutableStateFlow(true)
    val isGuardEnabled: StateFlow<Boolean> = _isGuardEnabled.asStateFlow()

    private val _incidents = MutableStateFlow<List<TheftIncident>>(emptyList())
    val incidents: StateFlow<List<TheftIncident>> = _incidents.asStateFlow()

    private val _lastWrongPasswordTime = MutableStateFlow<String?>(null)
    val lastWrongPasswordTime: StateFlow<String?> = _lastWrongPasswordTime.asStateFlow()

    private var callbackCountThisAttempt = 0
    private var lastAttemptTimestamp = 0L
    private const val DEBOUNCE_WINDOW_MS = 2500L

    fun init(context: Context) {
        val prefs = getPrefs(context)
        val name = prefs.getString(KEY_CONTACT_NAME, "") ?: ""
        val phone = prefs.getString(KEY_CONTACT_PHONE, "") ?: ""
        val email = prefs.getString(KEY_CONTACT_EMAIL, "") ?: ""
        val guardEnabled = prefs.getBoolean(KEY_GUARD_ENABLED, true)

        _trustedContact.value = TrustedContact(name, phone, email)
        _isGuardEnabled.value = guardEnabled

        val adminActive = isDeviceAdminActive(context)
        DebugLogger.logDeviceAdminStatus(adminActive)

        // Save initial SIM identifier if not saved yet
        checkSimState(context, isInitialCheck = true)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAdminComponent(context: Context): ComponentName {
        return ComponentName(context, MaxDeviceAdminReceiver::class.java)
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        return dpm?.isAdminActive(getAdminComponent(context)) == true
    }

    fun createDeviceAdminIntent(context: Context): Intent {
        val component = getAdminComponent(context)
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Max Anti-Theft Guard requires Device Admin permission to detect wrong PIN/password attempts and silently protect your phone."
            )
        }
    }

    fun setTrustedContact(context: Context, name: String, phoneNumber: String, email: String = "") {
        val cleanPhone = phoneNumber.trim().replace(" ", "").replace("-", "")
        _trustedContact.value = TrustedContact(name.trim(), cleanPhone, email.trim())

        getPrefs(context).edit()
            .putString(KEY_CONTACT_NAME, name.trim())
            .putString(KEY_CONTACT_PHONE, cleanPhone)
            .putString(KEY_CONTACT_EMAIL, email.trim())
            .apply()

        DebugLogger.logInfo("Anti-Theft Trusted Contact updated: ${name.ifBlank { "User" }} ($cleanPhone)")
    }

    fun setGuardEnabled(context: Context, enabled: Boolean) {
        _isGuardEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_GUARD_ENABLED, enabled).apply()
        DebugLogger.logInfo("Anti-Theft Guard enabled: $enabled")
    }

    /**
     * Called immediately when wrong password/pattern/PIN is entered.
     * MUST BE COMPLETELY SILENT to the intruder.
     */
    @Synchronized
    fun onWrongPasswordAttempt(context: Context) {
        val now = System.currentTimeMillis()
        val isNewAttempt = (now - lastAttemptTimestamp) > DEBOUNCE_WINDOW_MS

        if (isNewAttempt) {
            callbackCountThisAttempt = 1
            lastAttemptTimestamp = now
        } else {
            callbackCountThisAttempt++
        }

        // Required Debug Log: "PASSWORD_FAIL_CALLBACK_COUNT: <count>"
        DebugLogger.logPasswordFailCallbackCount(callbackCountThisAttempt)

        if (!isNewAttempt) {
            Log.d(TAG, "Debouncing duplicate callback #$callbackCountThisAttempt within $DEBOUNCE_WINDOW_MS ms")
            return
        }

        // Log required exact log: "WRONG_PASSWORD_DETECTED: true"
        DebugLogger.logWrongPasswordDetected()

        val timeString = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        _lastWrongPasswordTime.value = timeString

        if (!_isGuardEnabled.value) {
            DebugLogger.logInfo("Anti-Theft Guard is disabled; skipping silent alert trigger")
            return
        }

        // Run full silent theft capture & alert pipeline
        scope.launch {
            triggerTheftAlertPipeline(context, triggerType = "WRONG_PASSWORD")
        }
    }

    /**
     * SIM Change Detection: Checks if the current SIM serial / operator differs from last known.
     */
    fun checkSimState(context: Context, isInitialCheck: Boolean = false) {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return

            val hasPhonePermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            val currentSimId = if (hasPhonePermission) {
                try {
                    telephonyManager.simSerialNumber ?: telephonyManager.simOperator ?: "SIM_UNKNOWN"
                } catch (e: SecurityException) {
                    telephonyManager.simOperator ?: "SIM_OP_UNKNOWN"
                }
            } else {
                telephonyManager.simOperator ?: "SIM_OP_UNKNOWN"
            }

            val prefs = getPrefs(context)
            val storedSimId = prefs.getString(KEY_LAST_SIM_SERIAL, null)

            if (storedSimId == null) {
                // First time setup - save current SIM
                prefs.edit().putString(KEY_LAST_SIM_SERIAL, currentSimId).apply()
                DebugLogger.logInfo("Initial SIM registered: $currentSimId")
            } else if (storedSimId != currentSimId && currentSimId.isNotBlank() && currentSimId != "SIM_UNKNOWN") {
                // SIM has changed!
                DebugLogger.logInfo("SIM change detected! Old: $storedSimId, New: $currentSimId")
                prefs.edit().putString(KEY_LAST_SIM_SERIAL, currentSimId).apply()

                if (!isInitialCheck && _isGuardEnabled.value) {
                    scope.launch {
                        triggerTheftAlertPipeline(context, triggerType = "SIM_CHANGE")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking SIM state", e)
        }
    }

    /**
     * Silent Alert Pipeline:
     * 1. Silent Photo from Front Camera
     * 2. GPS Location
     * 3. SMS to Trusted Contact
     * 4. Record Incident
     */
    suspend fun triggerTheftAlertPipeline(
        context: Context,
        triggerType: String
    ): TheftIncident = withContext(Dispatchers.IO) {
        BatteryOptimizationManager.runWithSafeWakeLock(context, "TheftAlertPipeline", 15000L) {
            BatteryOptimizationManager.updateSubsystemState(antiTheftState = "PROCESSING ($triggerType Alert)")
            val timeStamp = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())

            // 1. Silent Photo Capture (Reusing MaxCameraManager.captureSilentBackgroundPhoto)
            var photoFile: File? = null
            try {
                photoFile = kotlinx.coroutines.runBlocking {
                    MaxCameraManager.captureSilentBackgroundPhoto(context, useFrontCamera = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed capturing silent photo", e)
            }

            val photoSuccess = photoFile != null && photoFile.exists() && photoFile.length() > 0
            DebugLogger.logTheftPhotoCaptured(
                photoSuccess,
                details = if (photoSuccess) photoFile!!.absolutePath else "Camera capture failed"
            )

            // 2. Fetch Current GPS Location
            val location = kotlinx.coroutines.runBlocking { fetchCurrentLocation(context) }
            val locationUrl = if (location != null) {
                "https://maps.google.com/?q=${location.latitude},${location.longitude}"
            } else {
                null
            }

            // 3. Dispatch SMS Alert to Trusted Contact
            val contact = _trustedContact.value
            var smsSuccess = false

            if (contact.phoneNumber.isNotBlank()) {
                val alertMessage = buildAlertMessage(triggerType, timeStamp, locationUrl, photoSuccess)
                smsSuccess = sendSilentSms(context, contact.phoneNumber, alertMessage)
            } else {
                DebugLogger.logInfo("No trusted contact phone set; SMS not sent")
            }

            DebugLogger.logTheftAlertSent(
                smsSuccess,
                details = if (smsSuccess) "Sent to ${contact.phoneNumber}" else "Trusted phone missing or SMS failed"
            )

            // 4. Save incident locally for user viewing
            val incident = TheftIncident(
                timestamp = timeStamp,
                triggerType = triggerType,
                photoPath = photoFile?.absolutePath,
                locationUrl = locationUrl,
                alertSent = smsSuccess
            )

            _incidents.value = listOf(incident) + _incidents.value.take(49)
            BatteryOptimizationManager.updateSubsystemState(antiTheftState = "STANDBY (Broadcast-Driven)")
            incident
        }
    }

    private fun buildAlertMessage(
        triggerType: String,
        timestamp: String,
        locationUrl: String?,
        hasPhoto: Boolean
    ): String {
        val reason = when (triggerType) {
            "SIM_CHANGE" -> "SIM Card change ho gaya hai!"
            "TEST" -> "[TEST] Anti-Theft Guard test alert."
            else -> "Kisi ne aapke phone par galat PIN/Password try kiya hai!"
        }

        val locPart = if (locationUrl != null) "Location: $locationUrl" else "Location: unavailable"
        val photoPart = if (hasPhoto) " [Photo captured]" else ""

        return "🚨 MAX ANTI-THEFT ALERT: $reason Samay: $timestamp. $locPart$photoPart"
    }

    private suspend fun fetchCurrentLocation(context: Context): Location? {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return null

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val location = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            location ?: fusedClient.lastLocation.await()
        } catch (e: Exception) {
            Log.w(TAG, "FusedLocationProvider error, falling back to LocationManager", e)
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val gpsLoc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val netLoc = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                gpsLoc ?: netLoc
            } catch (ex: Exception) {
                null
            }
        }
    }

    private fun sendSilentSms(context: Context, destinationAddress: String, messageText: String): Boolean {
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSmsPermission) {
            Log.w(TAG, "Cannot send SMS: SEND_SMS permission not granted")
            return false
        }

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(messageText)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(destinationAddress, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(destinationAddress, null, messageText, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $destinationAddress", e)
            false
        }
    }

    // =========================================================================
    // VOICE COMMAND MATCHERS FOR TRUSTED CONTACT SETUP
    // =========================================================================

    fun isTrustedContactCommand(lower: String): Boolean {
        val keywords = listOf(
            "emergency contact", "trusted contact", "इमरजेंसी कॉन्टैक्ट", "ट्रस्टेड कॉन्टैक्ट",
            "mera contact", "emergency number", "इमरजेंसी नंबर", "trusted number"
        )
        return keywords.any { lower.contains(it) }
    }

    fun parseAndSaveContactFromVoice(context: Context, rawText: String): Pair<Boolean, String> {
        // Extract digits (phone number) from speech
        val digits = rawText.filter { it.isDigit() }
        val words = rawText.split(" ", "।", ",", ":")

        var foundName = ""
        for (i in words.indices) {
            val w = words[i].lowercase(Locale.getDefault())
            if ((w == "naam" || w == "name" || w == "hai") && i + 1 < words.size) {
                val candidate = words[i + 1]
                if (!candidate.all { it.isDigit() }) {
                    foundName = candidate
                }
            }
        }

        if (digits.length >= 10) {
            val phoneNumber = digits.takeLast(10)
            val name = if (foundName.isNotBlank()) foundName else "Emergency Contact"
            setTrustedContact(context, name, phoneNumber)
            val msg = "इमरजेंसी कॉन्टैक्ट $phoneNumber सेट कर दिया गया है."
            TtsManager.speak(msg)
            return Pair(true, "Trusted contact saved: $name ($phoneNumber)")
        } else if (digits.length >= 7) {
            val phoneNumber = digits
            setTrustedContact(context, "Emergency Contact", phoneNumber)
            val msg = "इमरजेंसी कॉन्टैक्ट $phoneNumber सेट कर दिया गया है."
            TtsManager.speak(msg)
            return Pair(true, "Trusted contact saved: $phoneNumber")
        } else {
            val msg = "कृपया सही 10 अंकों का मोबाइल नंबर बोलें. जैसे 'मेरा इमरजेंसी कॉन्टैक्ट 9876543210 है'."
            TtsManager.speak(msg)
            return Pair(false, msg)
        }
    }
}
