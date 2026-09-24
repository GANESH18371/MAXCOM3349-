package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manager.AppOpenManager
import com.example.manager.InstalledApp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AppLauncherSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var isSuccessResult by remember { mutableStateOf(false) }

    fun refreshApps() {
        installedApps = AppOpenManager.getFreshInstalledApps(context)
    }

    LaunchedEffect(Unit) {
        refreshApps()
    }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            val clean = AppOpenManager.sanitizeCommand(searchQuery)
            installedApps.filter {
                it.normalizedName.contains(clean) || it.packageName.contains(clean, ignoreCase = true)
            }
        }
    }

    fun executeLaunch(query: String) {
        if (query.isBlank()) return
        val sanitized = AppOpenManager.sanitizeCommand(query)
        val launched = AppOpenManager.processAndLaunch(context, query)
        isSuccessResult = launched
        testResultText = if (launched) {
            "Launched successfully (Sanitized: '$sanitized')"
        } else {
            "No match found for '$query' (Sanitized: '$sanitized')"
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "APP OPEN (FUZZY MATCH & LAUNCH)",
                    style = MaterialTheme.typography.labelLarge,
                    color = CyberCyan
                )
            }

            IconButton(
                onClick = { refreshApps() },
                modifier = Modifier.size(28.dp).testTag("refresh_apps_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Apps List",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Command input bar with strip preview
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                testResultText = null
            },
            placeholder = {
                Text(
                    "Try: 'Max hello suno Chrome kholo' or 'YouTube'",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = CyberCyan
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = TextMuted
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { executeLaunch(searchQuery) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = DarkOutline,
                focusedContainerColor = DarkSurfaceCard,
                unfocusedContainerColor = DarkSurfaceCard,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_search_input")
        )

        // Live Stripped Keyword Preview
        if (searchQuery.isNotBlank()) {
            val sanitized = AppOpenManager.sanitizeCommand(searchQuery)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stripped clean query: ",
                    fontSize = 10.sp,
                    color = TextMuted
                )
                Text(
                    text = "\"$sanitized\"",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonLime
                )
            }
        }

        // Test Launch Button
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { executeLaunch(searchQuery) },
                enabled = searchQuery.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan,
                    contentColor = androidx.compose.ui.graphics.Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("test_launch_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Launch,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Fuzzy Match & Launch App",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // Result Banner
        if (testResultText != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = testResultText ?: "",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSuccessResult) NeonLime else NeonRed
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Fresh Apps List (Total count)
        Text(
            text = "Installed Apps (${filteredApps.size} found via QUERY_ALL_PACKAGES):",
            fontSize = 11.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                AppListItem(
                    app = app,
                    onClick = {
                        AppOpenManager.processAndLaunch(context, app.name)
                    }
                )
            }
        }
    }
}

@Composable
private fun AppListItem(
    app: InstalledApp,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkOutline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("app_item_${app.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bitmap = remember(app.icon) {
                drawableToBitmap(app.icon)
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = app.name,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(CyberCyan.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.name.take(1).uppercase(),
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    fontSize = 9.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.Launch,
                contentDescription = "Launch",
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
    if (drawable == null) return null
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
