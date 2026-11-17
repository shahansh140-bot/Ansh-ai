package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppConfig
import com.example.model.AssistantMode
import com.example.model.ChatThread
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.ui.components.AnshAiLogo
import com.example.ui.components.AnshWordmark
import com.example.ui.components.ElectricSurfaceCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightSurfaceElevated
import com.example.ui.theme.NebulaPurple
import com.example.ui.theme.WarningAmber

private data class HomeFeatureTile(
    val tag: String,
    val title: String,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    userProfile: UserProfile?,
    appConfig: AppConfig,
    recentChats: List<ChatThread>,
    onNewChat: (AssistantMode, String?) -> Unit,
    onOpenVoiceChat: () -> Unit,
    onOpenCreateImages: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenExistingChat: (ChatThread) -> Unit,
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var askInput by remember { mutableStateOf("") }
    var showAllUtilities by remember { mutableStateOf(false) }
    var showPlanDialog by remember { mutableStateOf(false) }
    val isSuperAdmin = userProfile?.roleEnum() == UserRole.SUPER_ADMIN

    if (showPlanDialog) {
        AlertDialog(
            onDismissRequest = { showPlanDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("ANSH AI Usage & Tier", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Account: ${userProfile?.email?.ifBlank { "Signed-in User" } ?: "Signed-in User"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Daily Quota Used: ${userProfile?.dailyRequestCount ?: 0} / ${appConfig.dailyRequestLimit} requests",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricCyan
                    )
                    Text(
                        text = "All core Gemini text, vision, search, voice, math, code, and image tools are active under your verified Google account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlanDialog = false }) {
                    Text("Close", color = ElectricBlueBright)
                }
            }
        )
    }

    val primaryGridTiles = remember(onNewChat, onOpenCreateImages, onOpenVideos) {
        listOf(
            HomeFeatureTile(
                tag = "home_ai_chat_btn",
                title = "Ask Anything",
                icon = Icons.Rounded.Forum,
                accent = ElectricCyan,
                onClick = { onNewChat(AssistantMode.CHAT, null) }
            ),
            HomeFeatureTile(
                tag = "home_generate_images_btn",
                title = "Generate Images",
                icon = Icons.Rounded.Image,
                accent = ElectricBlueBright,
                onClick = onOpenCreateImages
            ),
            HomeFeatureTile(
                tag = "home_create_video_btn",
                title = "Create Video",
                icon = Icons.Rounded.PlayCircleOutline,
                accent = ElectricCyan,
                onClick = onOpenVideos
            ),
            HomeFeatureTile(
                tag = "home_search_internet_btn",
                title = "Search Internet",
                icon = Icons.Rounded.Public,
                accent = ElectricBlue,
                onClick = { onNewChat(AssistantMode.SEARCH, null) }
            )
        )
    }

    val exploreMoreTiles = remember(onNewChat) {
        listOf(
            HomeFeatureTile(
                tag = "home_solve_math_btn",
                title = "Solve Math",
                icon = Icons.Rounded.Calculate,
                accent = ElectricCyan,
                onClick = { onNewChat(AssistantMode.MATH, null) }
            ),
            HomeFeatureTile(
                tag = "home_write_code_btn",
                title = "Write Code",
                icon = Icons.Rounded.Code,
                accent = ElectricBlueBright,
                onClick = { onNewChat(AssistantMode.CODE, null) }
            ),
            HomeFeatureTile(
                tag = "home_get_ideas_btn",
                title = "Get Ideas",
                icon = Icons.Rounded.Lightbulb,
                accent = WarningAmber,
                onClick = { onNewChat(AssistantMode.CHAT, "Brainstorm 5 creative, high-impact ideas for me today.") }
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060B1D),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        // Top Header Matching Mockup: Hamburger | ANSH AI Logo + Wordmark | Gold Crown
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("home_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Open navigation menu",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnshAiLogo(size = 34.dp, glowing = true)
                AnshWordmark(fontSize = 20.sp, italicAi = true)
            }

            if (isSuperAdmin) {
                IconButton(
                    onClick = onOpenAdmin,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home_admin_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WorkspacePremium,
                        contentDescription = "Super Admin Console",
                        tint = WarningAmber,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = { showPlanDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home_plan_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WorkspacePremium,
                        contentDescription = "Usage and tier details",
                        tint = WarningAmber,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Scrollable Home Content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Greeting Banner with Mascot Robot on Right
            item {
                ElectricSurfaceCard(
                    highlightBorder = true,
                    containerColor = Color(0xFF0C142B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF0D1633),
                                        ElectricBlue.copy(alpha = 0.22f),
                                        Color(0xFF0A1128)
                                    )
                                )
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, top = 16.dp, bottom = 16.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = buildAnnotatedString {
                                        append("Hello, I'm ")
                                        withStyle(
                                            SpanStyle(
                                                color = ElectricCyan,
                                                fontStyle = FontStyle.Italic,
                                                fontWeight = FontWeight.Bold
                                            )
                                        ) {
                                            append("ANSH AI.")
                                        }
                                    },
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    lineHeight = 30.sp
                                )

                                Text(
                                    text = "How can I help you today?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                StatusPill(
                                    text = "${userProfile?.dailyRequestCount ?: 0}/${appConfig.dailyRequestLimit} today",
                                    accentColor = ElectricBlue
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(116.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .drawWithCache {
                                        val radius = size.minDimension * 0.6f
                                        val brush = Brush.radialGradient(
                                            colors = listOf(
                                                ElectricBlue.copy(alpha = 0.45f),
                                                Color.Transparent
                                            )
                                        )
                                        onDrawBehind {
                                            drawCircle(brush = brush, radius = radius)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ansh_mascot_hero),
                                    contentDescription = "ANSH AI Assistant Mascot",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(18.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            if (appConfig.maintenanceMessage.isNotBlank()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = appConfig.maintenanceMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // 2x2 Glowing Quick Actions Grid (Ask Anything, Generate Images, Create Video, Search Internet)
            items(primaryGridTiles.chunked(2)) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { tile ->
                        CenteredActionTile(
                            tile = tile,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // "Explore More" + "See all ->" Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Explore More",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showAllUtilities = !showAllUtilities }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("home_explore_see_all")
                    ) {
                        Text(
                            text = if (showAllUtilities) "Less" else "See all",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricBlueBright
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = ElectricBlueBright,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 3-Column Row: Solve Math | Write Code | Get Ideas
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    exploreMoreTiles.forEach { tile ->
                        ExploreSquareTile(
                            tile = tile,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Highlighted New Chat + Voice Chat Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HighlightActionPill(
                        tag = "home_new_chat_btn",
                        title = "New Chat",
                        subtitle = "Start fresh",
                        icon = Icons.Rounded.AddComment,
                        accent = ElectricBlue,
                        onClick = { onNewChat(AssistantMode.CHAT, null) },
                        modifier = Modifier.weight(1f)
                    )
                    HighlightActionPill(
                        tag = "home_voice_chat_btn",
                        title = "Voice Chat",
                        subtitle = "Speak live",
                        icon = Icons.Rounded.Mic,
                        accent = NebulaPurple,
                        onClick = onOpenVoiceChat,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Expandable or Quick Utility Chips (History, User Profile, Settings)
            if (showAllUtilities || recentChats.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UtilityChip(
                            label = "History",
                            icon = Icons.Rounded.History,
                            tag = "home_chip_history",
                            onClick = onOpenHistory,
                            modifier = Modifier.weight(1f)
                        )
                        UtilityChip(
                            label = "Profile",
                            icon = Icons.Rounded.Person,
                            tag = "home_chip_profile",
                            onClick = onOpenProfile,
                            modifier = Modifier.weight(1f)
                        )
                        UtilityChip(
                            label = "Settings",
                            icon = Icons.Rounded.Settings,
                            tag = "home_chip_settings",
                            onClick = onOpenSettings,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Recent Conversations Strip
            if (recentChats.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Conversations",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricBlueBright,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenHistory)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recentChats.take(6), key = { it.id }) { thread ->
                            ElectricSurfaceCard(
                                onClick = { onOpenExistingChat(thread) },
                                modifier = Modifier
                                    .width(205.dp)
                                    .testTag("home_recent_chat_${thread.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StatusPill(text = thread.modeEnum().label)
                                    Text(
                                        text = thread.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = thread.lastMessagePreview.ifBlank { "Tap to resume..." },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }
        }

        // Bottom "Ask me anything..." Composer Pill
        Surface(
            color = Color(0xFF0B1124),
            border = BorderStroke(1.dp, Color(0xFF21315C)),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = askInput,
                    onValueChange = { askInput = it },
                    placeholder = {
                        Text(
                            "Ask me anything...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MidnightSurfaceElevated,
                        unfocusedContainerColor = MidnightSurfaceElevated,
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = MidnightBorder
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (askInput.isNotBlank()) {
                                val prompt = askInput.trim()
                                askInput = ""
                                onNewChat(AssistantMode.CHAT, prompt)
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_ask_input")
                )

                IconButton(
                    onClick = onOpenVoiceChat,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ElectricBlue.copy(alpha = 0.18f))
                        .testTag("home_composer_voice_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Start Voice Chat",
                        tint = ElectricCyan
                    )
                }

                FilledIconButton(
                    onClick = {
                        val prompt = askInput.trim()
                        if (prompt.isNotEmpty()) {
                            askInput = ""
                            onNewChat(AssistantMode.CHAT, prompt)
                        } else {
                            onNewChat(AssistantMode.CHAT, null)
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = ElectricBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Ask ANSH AI"
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredActionTile(
    tile: HomeFeatureTile,
    modifier: Modifier = Modifier
) {
    ElectricSurfaceCard(
        onClick = tile.onClick,
        containerColor = Color(0xFF0E152B),
        modifier = modifier.testTag(tile.tag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .drawWithCache {
                        val radius = size.minDimension * 0.7f
                        val brush = Brush.radialGradient(
                            colors = listOf(
                                tile.accent.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                        onDrawBehind {
                            drawCircle(brush = brush, radius = radius)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = tile.title,
                    tint = tile.accent,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExploreSquareTile(
    tile: HomeFeatureTile,
    modifier: Modifier = Modifier
) {
    ElectricSurfaceCard(
        onClick = tile.onClick,
        containerColor = Color(0xFF0E152B),
        modifier = modifier.testTag(tile.tag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = tile.icon,
                contentDescription = tile.title,
                tint = tile.accent,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = tile.title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HighlightActionPill(
    tag: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (accent == ElectricBlue) ElectricCyan else accent,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun UtilityChip(
    label: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag(tag),
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFF0E152B),
        border = BorderStroke(1.dp, MidnightBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = ElectricBlueBright,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
