package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AssistantMode
import com.example.model.ChatThread
import com.example.model.UiState
import com.example.ui.components.AnshConfirmDialog
import com.example.ui.components.ElectricSurfaceCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightSurfaceElevated

@Composable
fun HistoryScreen(
    chatsState: UiState<List<ChatThread>>,
    onOpenChat: (ChatThread) -> Unit,
    onDeleteChat: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<AssistantMode?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    if (showClearAllDialog) {
        AnshConfirmDialog(
            title = "Clear All Chat History",
            message = "Permanently delete every saved conversation in your ANSH AI cloud account?",
            confirmLabel = "Delete All",
            isDestructive = true,
            onConfirm = {
                showClearAllDialog = false
                onClearAll()
            },
            onDismiss = { showClearAllDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Chat History", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Synced privately to your Google account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = { showClearAllDialog = true },
                modifier = Modifier.testTag("history_clear_all_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = "Clear all history",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    " Clear All",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search conversations...") },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null)
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MidnightSurfaceElevated,
                unfocusedContainerColor = MidnightSurfaceElevated,
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = MidnightBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { selectedFilter = null },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricBlue.copy(alpha = 0.25f),
                    selectedLabelColor = ElectricBlueBright
                )
            )
            AssistantMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedFilter == mode,
                    onClick = { selectedFilter = mode },
                    label = { Text(mode.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricBlue.copy(alpha = 0.25f),
                        selectedLabelColor = ElectricBlueBright
                    )
                )
            }
        }

        when (chatsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricBlue)
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = chatsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            is UiState.Success -> {
                val filtered = remember(chatsState.data, searchQuery, selectedFilter) {
                    chatsState.data.filter { thread ->
                        val matchesMode = selectedFilter == null || thread.modeEnum() == selectedFilter
                        val matchesQuery = searchQuery.isBlank() ||
                            thread.title.contains(searchQuery, ignoreCase = true) ||
                            thread.lastMessagePreview.contains(searchQuery, ignoreCase = true)
                        matchesMode && matchesQuery
                    }
                }

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Forum,
                                contentDescription = null,
                                tint = ElectricBlueBright,
                                modifier = Modifier.size(44.dp)
                            )
                            Text("No saved conversations yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Start a new AI Chat from Home to see it synced here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.id }) { thread ->
                            ElectricSurfaceCard(
                                onClick = { onOpenChat(thread) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("history_item_${thread.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            StatusPill(text = thread.modeEnum().label)
                                            Text(
                                                text = "${thread.messageCount} msgs",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = thread.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = thread.lastMessagePreview.ifBlank { "Open conversation" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteChat(thread.id) },
                                        modifier = Modifier.testTag("delete_chat_${thread.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete conversation",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
