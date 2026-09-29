package com.example.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PermanentMemory
import com.example.manager.PermanentMemoryManager
import com.example.ui.theme.JarvisCard
import com.example.ui.theme.JarvisCardBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisNeonAmber
import com.example.ui.theme.JarvisNeonGreen
import com.example.ui.theme.JarvisNeonRed
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisTextDim
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PermanentMemoryCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val memories by PermanentMemoryManager.getAllMemoriesFlow(context)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var memoryToEdit by remember { mutableStateOf<PermanentMemory?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filteredMemories = remember(memories, searchQuery) {
        if (searchQuery.isBlank()) {
            memories
        } else {
            val q = searchQuery.lowercase()
            memories.filter {
                it.memoryKey.lowercase().contains(q) ||
                        it.memoryValue.lowercase().contains(q) ||
                        it.rawStatement.lowercase().contains(q) ||
                        it.category.lowercase().contains(q)
            }
        }
    }

    val timeFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.US) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisCard),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, JarvisCardBorder, RoundedCornerShape(16.dp))
            .testTag("permanent_memory_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .background(JarvisCyan.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, JarvisCyan.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = JarvisCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "PERMANENT MEMORY CORE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextPrimary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${memories.size} PERSISTENT FACT(S) • 100% LOCAL",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = JarvisTextDim
                        )
                    }
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).testTag("add_memory_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ADD FACT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Filter Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search memory facts, habits, apps...",
                        fontSize = 11.sp,
                        color = JarvisTextDim
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = JarvisCyan,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = JarvisTextDim,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisCardBorder,
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary,
                    cursorColor = JarvisCyan
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // List of Memories
            if (filteredMemories.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JarvisSurface, RoundedCornerShape(10.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty())
                            "No memories match \"$searchQuery\"."
                        else
                            "No permanent memories stored yet.\nSay \"Meri favorite app YouTube hai\" or \"Yeh yaad rakhna: [fact]\" to save!",
                        fontSize = 11.sp,
                        color = JarvisTextSecondary,
                        lineHeight = 16.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredMemories.forEach { item ->
                        MemoryItemRow(
                            memory = item,
                            timeFormat = timeFormat,
                            onEdit = { memoryToEdit = item },
                            onDelete = {
                                scope.launch {
                                    PermanentMemoryManager.deleteMemoryById(context, item.id)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Clear All Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showClearConfirm = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = JarvisNeonRed.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "CLEAR ALL MEMORY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = JarvisNeonRed.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }

    // Add Memory Dialog
    if (showAddDialog) {
        var keyInput by remember { mutableStateOf("") }
        var valueInput by remember { mutableStateOf("") }
        var categoryInput by remember { mutableStateOf("preference") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = JarvisCard,
            title = {
                Text(
                    text = "STORE PERMANENT FACT",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Fact Key (e.g. favorite_app, routine)", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisCardBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = valueInput,
                        onValueChange = { valueInput = it },
                        label = { Text("Memory Value (e.g. YouTube, Subah 7 baje chai)", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisCardBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("preference", "habit", "identity", "fact").forEach { cat ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (categoryInput == cat) JarvisCyan.copy(alpha = 0.2f) else JarvisSurface,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (categoryInput == cat) JarvisCyan else JarvisCardBorder,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { categoryInput = cat }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cat.uppercase(),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (categoryInput == cat) JarvisCyan else JarvisTextDim
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (keyInput.isNotBlank() && valueInput.isNotBlank()) {
                            scope.launch {
                                PermanentMemoryManager.saveMemory(
                                    context,
                                    key = keyInput,
                                    value = valueInput,
                                    category = categoryInput,
                                    rawStatement = "$keyInput: $valueInput"
                                )
                            }
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Text("SAVE TO MEMORY", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("CANCEL", color = JarvisTextSecondary, fontSize = 11.sp)
                }
            }
        )
    }

    // Edit Memory Dialog
    if (memoryToEdit != null) {
        val memory = memoryToEdit!!
        var editValue by remember(memory) { mutableStateOf(memory.memoryValue) }

        AlertDialog(
            onDismissRequest = { memoryToEdit = null },
            containerColor = JarvisCard,
            title = {
                Text(
                    text = "EDIT: ${memory.memoryKey.uppercase()}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisCyan
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Update the fact statement stored in Max's permanent memory:",
                        fontSize = 11.sp,
                        color = JarvisTextSecondary
                    )
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        label = { Text("Memory Value", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisCardBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editValue.isNotBlank()) {
                            scope.launch {
                                PermanentMemoryManager.saveMemory(
                                    context,
                                    key = memory.memoryKey,
                                    value = editValue,
                                    category = memory.category,
                                    rawStatement = "${memory.memoryKey}: $editValue"
                                )
                            }
                            memoryToEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Text("UPDATE", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryToEdit = null }) {
                    Text("CANCEL", color = JarvisTextSecondary, fontSize = 11.sp)
                }
            }
        )
    }

    // Clear All Confirmation Dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = JarvisCard,
            title = {
                Text(
                    text = "PURGE ALL MEMORY?",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisNeonRed
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all stored memories, user habits, and preferences? This cannot be undone.",
                    fontSize = 11.sp,
                    color = JarvisTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            PermanentMemoryManager.clearAllMemories(context)
                        }
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisNeonRed)
                ) {
                    Text("PURGE ALL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("CANCEL", color = JarvisTextSecondary, fontSize = 11.sp)
                }
            }
        )
    }
}

@Composable
private fun MemoryItemRow(
    memory: PermanentMemory,
    timeFormat: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryColor = when (memory.category.lowercase()) {
        "preference" -> JarvisCyan
        "habit" -> JarvisNeonGreen
        "identity" -> JarvisNeonAmber
        else -> JarvisTextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(JarvisSurface, RoundedCornerShape(10.dp))
            .border(1.dp, JarvisCardBorder.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(categoryColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, categoryColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = memory.category.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = categoryColor
                    )
                }

                Text(
                    text = memory.memoryKey,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextDim
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = memory.memoryValue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = JarvisTextPrimary
            )

            Text(
                text = "Updated: ${timeFormat.format(Date(memory.updatedAt))}",
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = JarvisTextDim
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Memory",
                    tint = JarvisCyan,
                    modifier = Modifier.size(15.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Memory",
                    tint = JarvisNeonRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
