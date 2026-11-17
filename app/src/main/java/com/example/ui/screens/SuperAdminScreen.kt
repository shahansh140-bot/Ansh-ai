package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.signOut
import com.example.model.AccountStatus
import com.example.model.AiRequestMetric
import com.example.model.AppConfig
import com.example.model.SystemErrorLog
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.ui.components.ElectricSurfaceCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightSurfaceElevated
import com.example.ui.theme.NebulaPurple
import com.example.ui.theme.SuccessGreen

@Composable
fun SuperAdminScreen(
    usersState: UiState<List<UserProfile>>,
    telemetryState: UiState<List<AiRequestMetric>>,
    errorsState: UiState<List<SystemErrorLog>>,
    appConfig: AppConfig,
    adminToast: String?,
    onClearToast: () -> Unit,
    onToggleUserStatus: (UserProfile) -> Unit,
    onSaveGlobalConfig: (AppConfig) -> Unit,
    onDismissError: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Overview, 1=Users, 2=Config, 3=Errors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = NebulaPurple)
            Column(modifier = Modifier.weight(1f)) {
                Text("Super Admin Console", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Owner-verified server-side governance",
                    style = MaterialTheme.typography.labelSmall,
                    color = NebulaPurple
                )
            }
            IconButton(
                onClick = { signOut(credentialManager, scope) },
                modifier = Modifier.testTag("admin_logout_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = "Sign out of Super Admin",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        adminToast?.let { toast ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElectricBlue.copy(alpha = 0.2f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(toast, style = MaterialTheme.typography.bodySmall, color = ElectricCyan)
                TextButton(onClick = onClearToast) { Text("OK") }
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = ElectricBlueBright
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Stats") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Users") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Config") })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Errors") })
        }

        when (selectedTab) {
            0 -> AdminOverviewTab(usersState, telemetryState)
            1 -> AdminUsersTab(usersState, onToggleUserStatus)
            2 -> AdminConfigTab(appConfig, onSaveGlobalConfig)
            3 -> AdminErrorsTab(errorsState, onDismissError)
        }
    }
}

@Composable
private fun AdminOverviewTab(
    usersState: UiState<List<UserProfile>>,
    telemetryState: UiState<List<AiRequestMetric>>
) {
    val users = (usersState as? UiState.Success)?.data ?: emptyList()
    val metrics = (telemetryState as? UiState.Success)?.data ?: emptyList()
    val activeUsers = users.count { it.accountStatusEnum() == AccountStatus.ACTIVE }
    val totalRequests = users.sumOf { it.totalRequests }.coerceAtLeast(metrics.size.toLong())
    val successCount = metrics.count { it.success }
    val successRate = if (metrics.isEmpty()) 100 else (successCount * 100 / metrics.size)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile("Total Users", users.size.toString(), ElectricBlue, Modifier.weight(1f))
                MetricTile("Active Users", activeUsers.toString(), SuccessGreen, Modifier.weight(1f))
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile("Total AI Requests", totalRequests.toString(), ElectricCyan, Modifier.weight(1f))
                MetricTile("Success Rate", "$successRate%", NebulaPurple, Modifier.weight(1f))
            }
        }

        item {
            ElectricSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("AI Feature Breakdown (Recent 200)", style = MaterialTheme.typography.titleMedium)
                    val grouped = metrics.groupBy { it.feature }
                    if (grouped.isEmpty()) {
                        Text(
                            "No telemetry events recorded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        grouped.forEach { (feature, list) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(feature, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${list.size} requests",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricCyan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    ElectricSurfaceCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = accent)
        }
    }
}

@Composable
private fun AdminUsersTab(
    usersState: UiState<List<UserProfile>>,
    onToggleUserStatus: (UserProfile) -> Unit
) {
    when (usersState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ElectricBlue)
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(usersState.message, color = MaterialTheme.colorScheme.error)
        }
        is UiState.Success -> {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(usersState.data, key = { it.userId }) { user ->
                    val isOwner = user.roleEnum() == UserRole.SUPER_ADMIN
                    val isActive = user.accountStatusEnum() == AccountStatus.ACTIVE
                    ElectricSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                                    StatusPill(
                                        text = if (isOwner) "SUPER ADMIN" else user.status,
                                        accentColor = if (isOwner) NebulaPurple else if (isActive) SuccessGreen else MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    user.email.ifBlank { user.userId },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Daily: ${user.dailyRequestCount} • Total: ${user.totalRequests}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }

                            if (!isOwner) {
                                IconButton(
                                    onClick = { onToggleUserStatus(user) },
                                    modifier = Modifier.testTag("admin_toggle_user_${user.userId}")
                                ) {
                                    Icon(
                                        imageVector = if (isActive) Icons.Rounded.Block else Icons.Rounded.CheckCircle,
                                        contentDescription = if (isActive) "Disable account" else "Reactivate account",
                                        tint = if (isActive) MaterialTheme.colorScheme.error else SuccessGreen
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

@Composable
private fun AdminConfigTab(
    appConfig: AppConfig,
    onSaveGlobalConfig: (AppConfig) -> Unit
) {
    var limitInput by remember(appConfig.dailyRequestLimit) {
        mutableStateOf(appConfig.dailyRequestLimit.toString())
    }
    var chatEnabled by remember(appConfig.chatEnabled) { mutableStateOf(appConfig.chatEnabled) }
    var voiceEnabled by remember(appConfig.voiceEnabled) { mutableStateOf(appConfig.voiceEnabled) }
    var imageEnabled by remember(appConfig.imageGenEnabled) { mutableStateOf(appConfig.imageGenEnabled) }
    var searchEnabled by remember(appConfig.webSearchEnabled) { mutableStateOf(appConfig.webSearchEnabled) }
    var videoEnabled by remember(appConfig.videoGenEnabled) { mutableStateOf(appConfig.videoGenEnabled) }
    var videoBackendUrl by remember(appConfig.videoBackendUrl) { mutableStateOf(appConfig.videoBackendUrl) }
    var maintenanceMsg by remember(appConfig.maintenanceMessage) {
        mutableStateOf(appConfig.maintenanceMessage)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElectricSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Usage Limits & Feature Controls", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = limitInput,
                        onValueChange = { limitInput = it.filter(Char::isDigit) },
                        label = { Text("Daily Request Limit per User (1–10000)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MidnightSurfaceElevated,
                            unfocusedContainerColor = MidnightSurfaceElevated,
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = MidnightBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    AdminToggleRow("AI Chat & Math/Code", chatEnabled) { chatEnabled = it }
                    AdminToggleRow("Voice Chat", voiceEnabled) { voiceEnabled = it }
                    AdminToggleRow("Image Generation", imageEnabled) { imageEnabled = it }
                    AdminToggleRow("Internet Search", searchEnabled) { searchEnabled = it }
                    AdminToggleRow("Video Generation (Gemini Omni 1.2 Flash)", videoEnabled) { videoEnabled = it }

                    OutlinedTextField(
                        value = videoBackendUrl,
                        onValueChange = { videoBackendUrl = it },
                        label = { Text("Video Backend Proxy URL (Server-Side Key)") },
                        placeholder = { Text("https://us-central1-<project>.cloudfunctions.net/anshVideoApi") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MidnightSurfaceElevated,
                            unfocusedContainerColor = MidnightSurfaceElevated,
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = MidnightBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_video_backend_url_input")
                    )

                    OutlinedTextField(
                        value = maintenanceMsg,
                        onValueChange = { maintenanceMsg = it },
                        label = { Text("Maintenance Banner (leave blank when normal)") },
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MidnightSurfaceElevated,
                            unfocusedContainerColor = MidnightSurfaceElevated,
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = MidnightBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val parsedLimit = limitInput.toLongOrNull()?.coerceIn(1L, 10000L) ?: 50L
                            onSaveGlobalConfig(
                                appConfig.copy(
                                    dailyRequestLimit = parsedLimit,
                                    chatEnabled = chatEnabled,
                                    voiceEnabled = voiceEnabled,
                                    imageGenEnabled = imageEnabled,
                                    webSearchEnabled = searchEnabled,
                                    videoGenEnabled = videoEnabled,
                                    videoBackendUrl = videoBackendUrl.trim(),
                                    maintenanceMessage = maintenanceMsg.trim()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("admin_save_config_button")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Global Settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = ElectricBlue)
        )
    }
}

@Composable
private fun AdminErrorsTab(
    errorsState: UiState<List<SystemErrorLog>>,
    onDismissError: (String) -> Unit
) {
    when (errorsState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ElectricBlue)
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(errorsState.message, color = MaterialTheme.colorScheme.error)
        }
        is UiState.Success -> {
            if (errorsState.data.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No system errors logged.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(errorsState.data, key = { it.id }) { err ->
                        ElectricSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(err.feature, style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                                    Text(err.message, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { onDismissError(err.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Dismiss error")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
