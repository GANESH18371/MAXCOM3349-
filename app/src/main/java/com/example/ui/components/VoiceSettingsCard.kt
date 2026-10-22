package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonLime
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.PresetVoice
import com.example.util.TtsManager

@Composable
fun VoiceSettingsCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val pitch by TtsManager.pitch.collectAsState()
    val speechRate by TtsManager.speechRate.collectAsState()
    val selectedPreset by TtsManager.selectedPreset.collectAsState()
    val languageMode by TtsManager.languageMode.collectAsState()
    val isSpeaking by TtsManager.isSpeaking.collectAsState()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkOutline, RoundedCornerShape(16.dp))
            .testTag("voice_settings_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .background(CyberCyan.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, CyberCyan.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Voice Settings",
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Unified Voice & TTS Studio",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "2 Natural Voices (Male & Female) • Unified Everywhere",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                IconButton(
                    onClick = {
                        TtsManager.resetToDefaults(context)
                        Toast.makeText(context, "Voice reset to Male (Default)", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Defaults",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Active Preset Banner
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "CURRENT ACTIVE VOICE: ${selectedPreset.gender.uppercase()} VOICE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "App-open, hardware toggles, weather, WhatsApp auto-reply, camera vision, and conversations all speak in this chosen voice.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 1: 2 NATURAL VOICES SELECTION
            Text(
                text = "CHOOSE YOUR MAX VOICE (MALE / FEMALE)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TtsManager.PRESET_VOICES.forEach { preset ->
                    val isSelected = selectedPreset.id == preset.id
                    PresetVoiceCard(
                        preset = preset,
                        isSelected = isSelected,
                        onSelect = {
                            TtsManager.selectPresetVoice(context, preset.id)
                            Toast.makeText(context, "${preset.gender} voice selected", Toast.LENGTH_SHORT).show()
                        },
                        onSamplePlay = {
                            TtsManager.speakSampleForPreset(preset)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 2: AUDIO TUNING SLIDERS (APPLIED TO PRESET VOICES)
            Text(
                text = "AUDIO TUNING (APPLIES TO SELECTED VOICE)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Pitch Slider
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Voice Pitch (आवाज़ का सुर)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "%.2fx".format(pitch),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )
                }
                Slider(
                    value = pitch,
                    onValueChange = { TtsManager.setPitch(context, it) },
                    valueRange = 0.75f..1.4f,
                    steps = 12,
                    colors = SliderDefaults.colors(
                        thumbColor = CyberCyan,
                        activeTrackColor = CyberCyan,
                        inactiveTrackColor = DarkOutline
                    ),
                    modifier = Modifier.testTag("pitch_slider")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speech Rate / Speed Slider
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = NeonLime,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Speech Rate (बोलने की गति)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "%.2fx".format(speechRate),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonLime
                    )
                }
                Slider(
                    value = speechRate,
                    onValueChange = { TtsManager.setSpeechRate(context, it) },
                    valueRange = 0.75f..1.4f,
                    steps = 12,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonLime,
                        activeTrackColor = NeonLime,
                        inactiveTrackColor = DarkOutline
                    ),
                    modifier = Modifier.testTag("speed_slider")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Language & Script Optimization Selector
            Text(
                text = "LANGUAGE SCRIPT MODE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val modes = listOf(
                    "auto" to "Auto-Hybrid",
                    "hi_IN" to "Hindi Priority",
                    "en_IN" to "English (IN)"
                )
                modes.forEach { (modeKey, label) ->
                    val isSelected = languageMode == modeKey
                    Button(
                        onClick = { TtsManager.setLanguageMode(context, modeKey) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) CyberCyan else DarkSurfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.Black else TextSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 3: TEST SELECTED VOICE ACROSS PHRASES
            Text(
                text = "TEST SELECTED VOICE WITH SYSTEM PHRASES",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { TtsManager.testPhrases(context, 0) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Text("▶ YouTube khul gaya", fontSize = 9.5.sp, color = Color.Black, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }

                    Button(
                        onClick = { TtsManager.testPhrases(context, 1) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Text("▶ WiFi on kar diya", fontSize = 9.5.sp, color = Color.Black, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { TtsManager.testPhrases(context, 2) },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Text("▶ Weather report", fontSize = 9.5.sp, color = TextPrimary, maxLines = 1, softWrap = false)
                    }

                    Button(
                        onClick = { TtsManager.testPhrases(context, 3) },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Text("▶ Auto-reply on ho gaya", fontSize = 9.5.sp, color = TextPrimary, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetVoiceCard(
    preset: PresetVoice,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onSamplePlay: () -> Unit
) {
    val borderColor = if (isSelected) CyberCyan else DarkOutline
    val containerColor = if (isSelected) CyberCyan.copy(alpha = 0.08f) else DarkSurfaceVariant
    val accentColor = if (preset.gender == "Male") CyberCyan else NeonAmber

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onSelect() }
            .testTag("preset_voice_${preset.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Main Top Row: Left-to-right on a single line with generous width
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = onSelect,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = CyberCyan,
                            unselectedColor = TextMuted
                        ),
                        modifier = Modifier.size(20.dp)
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .background(accentColor.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Text(
                            text = preset.name.first().toString(),
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Single line, left-to-right, no vertical wrapping
                    Text(
                        text = "${preset.gender} Voice",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        softWrap = false
                    )

                    Box(
                        modifier = Modifier
                            .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = preset.gender.uppercase(),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Compact, sleek play sample button
                Button(
                    onClick = onSamplePlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) CyberCyan else DarkOutline
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("play_sample_${preset.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Sample",
                            tint = if (isSelected) Color.Black else TextPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Sample",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else TextPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle on its own full line below, indented past the RadioButton
            Text(
                text = preset.subtitle,
                fontSize = 10.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 28.dp),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
