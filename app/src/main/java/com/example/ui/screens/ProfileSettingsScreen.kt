package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.data.signOut
import com.example.model.AppConfig
import com.example.model.ThemeMode
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.ui.components.AnshAiLogo
import com.example.ui.components.AnshConfirmDialog
import com.example.ui.components.ElectricSurfaceCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightSurfaceElevated
import com.example.ui.theme.NebulaPurple

@Composable
fun ProfileSettingsScreen(
    userProfile: UserProfile?,
    appConfig: AppConfig,
    onUpdatePreferences: (
        displayName: String?,
        language: String?,
        themeMode: ThemeMode?,
        speechRate: Double?,
        voiceAutoSpeak: Boolean?,
        autoSaveHistory: Boolean?
    ) -> Unit,
    onClearAllHistory: () -> Unit,
    onOpenSuperAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val profile = userProfile ?: UserProfile()
    val isSuperAdmin = profile.roleEnum() == UserRole.SUPER_ADMIN
    val languages = listOf("English", "Hindi", "Spanish", "French", "German", "Japanese")
    var sliderRate by remember(profile.voiceSpeechRate) {
        mutableFloatStateOf(profile.voiceSpeechRate.toFloat().coerceIn(0.5f, 2.0f))
    }

    if (showEditNameDialog) {
        var newName by remember(profile.displayName) { mutableStateOf(profile.displayName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Edit Display Name") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Display Name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEditNameDialog = false
                        if (newName.isNotBlank()) {
                            onUpdatePreferences(newName.trim(), null, null, null, null, null)
                        }
                    }
                ) { Text("Save", color = ElectricBlueBright) }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearHistoryDialog) {
        AnshConfirmDialog(
            title = "Clear All Chat History",
            message = "This will permanently delete all your saved ANSH AI conversations from cloud storage.",
            confirmLabel = "Delete All",
            isDestructive = true,
            onConfirm = {
                showClearHistoryDialog = false
                onClearAllHistory()
            },
            onDismiss = { showClearHistoryDialog = false }
        )
    }

    if (showLogoutDialog) {
        AnshConfirmDialog(
            title = "Log Out",
            message = "Sign out of ANSH AI on this device?",
            confirmLabel = "Log Out",
            isDestructive = true,
            onConfirm = {
                showLogoutDialog = false
                signOut(credentialManager, scope)
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Privacy & Security") },
            text = {
                Text(
                    text = "ANSH AI stores your chats under your private authenticated UID (/users/{uid}/chats) protected by zero-trust Firestore security rules. No other user can read your conversations. AI traffic is attested via Firebase App Check, and no secret API keys are stored in client code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Close", color = ElectricBlueBright)
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Profile Header Card
        item {
            ElectricSurfaceCard(
                highlightBorder = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (!profile.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = profile.photoUrl,
                                contentDescription = "User avatar",
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            AnshAiLogo(size = 62.dp, glowing = true)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(profile.displayName, style = MaterialTheme.typography.titleLarge)
                                IconButton(
                                    onClick = { showEditNameDialog = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit display name",
                                        tint = ElectricBlueBright,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = profile.email.ifBlank { "Google Authenticated Account" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            StatusPill(
                                text = if (isSuperAdmin) "SUPER ADMIN • OWNER" else "ACTIVE MEMBER",
                                accentColor = if (isSuperAdmin) NebulaPurple else ElectricBlue
                            )
                        }
                    }

                    val progress = (profile.dailyRequestCount.toFloat() /
                        appConfig.dailyRequestLimit.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Daily AI Usage", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${profile.dailyRequestCount} / ${appConfig.dailyRequestLimit} requests (${profile.totalRequests} lifetime)",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            color = ElectricBlue,
                            trackColor = MidnightSurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }

        // Super Admin Console Card (STRICTLY gated by server-backed SUPER_ADMIN role)
        if (isSuperAdmin) {
            item {
                ElectricSurfaceCard(
                    highlightBorder = true,
                    onClick = onOpenSuperAdmin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_super_admin_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
                            contentDescription = "Super Admin Dashboard",
                            tint = NebulaPurple,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Super Admin Console", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Users, AI telemetry, feature flags, limits & error logs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(text = "OWNER", accentColor = NebulaPurple)
                    }
                }
            }
        }

        // Theme Settings
        item {
            ElectricSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = ElectricBlueBright)
                        Text("Theme Settings", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = profile.themeModeEnum() == mode,
                                onClick = {
                                    onUpdatePreferences(null, null, mode, null, null, null)
                                },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue.copy(alpha = 0.25f),
                                    selectedLabelColor = ElectricBlueBright
                                ),
                                modifier = Modifier.testTag("theme_option_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // Language Settings
        item {
            ElectricSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Language, contentDescription = null, tint = ElectricCyan)
                        Text("AI Response Language", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        languages.forEach { lang ->
                            FilterChip(
                                selected = profile.preferredLanguage == lang,
                                onClick = {
                                    onUpdatePreferences(null, lang, null, null, null, null)
                                },
                                label = { Text(lang) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue.copy(alpha = 0.25f),
                                    selectedLabelColor = ElectricBlueBright
                                )
                            )
                        }
                    }
                }
            }
        }

        // Voice Settings
        item {
            ElectricSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, tint = ElectricBlueBright)
                        Text("Voice Settings", style = MaterialTheme.typography.titleMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Speak AI Replies", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Read voice chat responses aloud via Text-to-Speech",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.voiceAutoSpeak,
                            onCheckedChange = { checked ->
                                onUpdatePreferences(null, null, null, null, checked, null)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = ElectricBlue)
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Speech Rate", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                String.format("%.1fx", sliderRate),
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }
                        Slider(
                            value = sliderRate,
                            onValueChange = { sliderRate = it },
                            onValueChangeFinished = {
                                onUpdatePreferences(null, null, null, sliderRate.toDouble(), null, null)
                            },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricBlue,
                                activeTrackColor = ElectricBlue
                            )
                        )
                    }
                }
            }
        }

        // Chat History & Account Security
        item {
            ElectricSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Save Chat History", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Sync conversations privately to Cloud Firestore",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.autoSaveHistory,
                            onCheckedChange = { checked ->
                                onUpdatePreferences(null, null, null, null, null, checked)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = ElectricBlue)
                        )
                    }

                    OutlinedButton(
                        onClick = { showClearHistoryDialog = true },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_clear_history_button")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All Saved Chats", color = MaterialTheme.colorScheme.error)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { showPrivacyDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = ElectricCyan)
                        Column {
                            Text("Privacy & Data Isolation", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Zero-trust Firestore rules & App Check details",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = ElectricBlueBright)
                        Column {
                            Text("Password & Security", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Managed securely by Google Sign-In & Credential Manager.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // About ANSH AI
        item {
            ElectricSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnshAiLogo(size = 44.dp, glowing = false)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("About ANSH AI", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Version 1.0 • Intelligence Without Limits.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ElectricBlueBright
                        )
                    }
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Logout Button
        item {
            Button(
                onClick = { showLogoutDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("settings_logout_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out")
            }
        }
    }
}
