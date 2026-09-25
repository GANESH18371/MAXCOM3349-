package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.manager.WeatherManager
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.TtsManager

@Composable
fun WeatherCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val weather by WeatherManager.currentWeather.collectAsStateWithLifecycle()
    val isLoading by WeatherManager.isLoading.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasPermission = granted
        if (granted) {
            WeatherManager.fetchAndAnnounceWeather(context)
        } else {
            Toast.makeText(context, "Location permission required for local weather", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-fetch weather on first load if permission already present and weather not loaded
    LaunchedEffect(hasPermission) {
        if (hasPermission && weather == null) {
            WeatherManager.fetchAndAnnounceWeather(context)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("weather_card")
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyberCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "Weather Icon",
                            tint = NeonYellow,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "WEATHER (मौसम)",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Open-Meteo GPS Live Report",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = CyberCyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                if (hasPermission) {
                                    WeatherManager.fetchAndAnnounceWeather(context)
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.testTag("refresh_weather_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Weather",
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!hasPermission) {
                // Permission Request Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface)
                        .border(1.dp, NeonYellow.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Location Permission Required",
                                color = NeonYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Bol kar mausam janne ke liye GPS location ki permission allow karein.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("allow_location_button")
                        ) {
                            Text("Allow", color = DarkCard, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (weather != null) {
                val info = weather!!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Temperature & Condition
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${Math.round(info.temperature)}°C",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = info.description,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Air,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${info.windSpeed} km/h hawa",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    // Speak aloud button
                    IconButton(
                        onClick = {
                            val rounded = Math.round(info.temperature).toInt()
                            TtsManager.speak("आज तापमान $rounded डिग्री सेल्सियस है और मौसम ${info.description} है.")
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ElectricBlue.copy(alpha = 0.2f))
                            .testTag("speak_weather_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Speak Weather",
                            tint = ElectricBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // GPS Coordinates info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GPS: %.4f, %.4f • Free Open-Meteo API".format(info.latitude, info.longitude),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                // Empty state before fetch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Voice command bole: \"aaj ka mausam kaisa hai\"",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Button(
                        onClick = { WeatherManager.fetchAndAnnounceWeather(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.2f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("check_weather_btn")
                    ) {
                        Text("Check Now", color = CyberCyan, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
