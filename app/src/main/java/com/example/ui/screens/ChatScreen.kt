package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AssistantMode
import com.example.model.ChatMessage
import com.example.model.Citation
import com.example.model.GeminiModelOption
import com.example.ui.components.AnshAiLogo
import com.example.ui.components.AnshConfirmDialog
import com.example.ui.components.AnshMarkdownMessage
import com.example.ui.components.ElectricSurfaceCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightSurfaceElevated
import com.example.ui.theme.NebulaPurple
import com.example.viewmodel.ActiveChatUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
fun ChatScreen(
    chatState: ActiveChatUiState,
    messages: List<ChatMessage>,
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    onClearChat: () -> Unit,
    onModeSelected: (AssistantMode) -> Unit,
    onModelSelected: (GeminiModelOption) -> Unit,
    onSendMessage: (String, Bitmap?) -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenVoiceChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showModelMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var copiedMessageId by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                attachedBitmap = loadBitmapFromUri(context, uri)
            }
        }
    }

    LaunchedEffect(messages.size, chatState.streamingText) {
        val total = messages.size + if (chatState.isGenerating) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    if (showClearDialog) {
        AnshConfirmDialog(
            title = "Clear Conversation",
            message = "Delete all messages in this chat thread? This cannot be undone.",
            confirmLabel = "Clear",
            isDestructive = true,
            onConfirm = {
                showClearDialog = false
                onClearChat()
            },
            onDismiss = { showClearDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Chat Toolbar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MidnightBorder)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("chat_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                        AnshAiLogo(size = 32.dp, glowing = false)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "ANSH AI",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Box {
                                Text(
                                    text = "${chatState.modelOption.displayName} ▾",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricBlueBright,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showModelMenu = true }
                                        .padding(vertical = 2.dp)
                                        .testTag("chat_model_selector")
                                )
                                DropdownMenu(
                                    expanded = showModelMenu,
                                    onDismissRequest = { showModelMenu = false }
                                ) {
                                    GeminiModelOption.entries.forEach { opt ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(opt.displayName, style = MaterialTheme.typography.titleSmall)
                                                    Text(
                                                        opt.subtitle,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                showModelMenu = false
                                                onModelSelected(opt)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onNewChat,
                            modifier = Modifier.testTag("chat_new_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddComment,
                                contentDescription = "New Chat",
                                tint = ElectricBlueBright
                            )
                        }
                        IconButton(
                            onClick = { showClearDialog = true },
                            enabled = messages.isNotEmpty(),
                            modifier = Modifier.testTag("chat_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "Clear Conversation"
                            )
                        }
                    }
                }

                // Mode Pills Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        AssistantMode.CHAT,
                        AssistantMode.SEARCH,
                        AssistantMode.MATH,
                        AssistantMode.CODE
                    ).forEach { mode ->
                        val selected = chatState.mode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onModeSelected(mode) },
                            label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue.copy(alpha = 0.25f),
                                selectedLabelColor = ElectricBlueBright
                            ),
                            modifier = Modifier.testTag("chat_mode_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty() && !chatState.isGenerating) {
                item {
                    EmptyChatPromptCard(
                        mode = chatState.mode,
                        onSuggestionClick = { suggestion ->
                            onSendMessage(suggestion, null)
                        }
                    )
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isUser = msg.role == "user"
                val isLastModel = !isUser && messages.lastOrNull()?.id == msg.id
                ChatBubble(
                    message = msg,
                    isCopied = copiedMessageId == msg.id,
                    showRegenerate = isLastModel && !chatState.isGenerating,
                    onCopy = {
                        clipboard.setText(AnnotatedString(msg.text))
                        copiedMessageId = msg.id
                    },
                    onRegenerate = onRegenerate,
                    onCitationClick = { url ->
                        runCatching { uriHandler.openUri(url) }
                    }
                )
            }

            if (chatState.isGenerating) {
                item(key = "streaming_bubble") {
                    StreamingAssistantBubble(
                        streamingText = chatState.streamingText,
                        citations = chatState.streamingCitations,
                        onStop = onStopGeneration,
                        onCitationClick = { url ->
                            runCatching { uriHandler.openUri(url) }
                        }
                    )
                }
            }
        }

        // Error Banner
        AnimatedVisibility(visible = !chatState.errorMessage.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = chatState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Attached Image Preview
        AnimatedVisibility(visible = attachedBitmap != null) {
            attachedBitmap?.let { bmp ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Attached image preview",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.small),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { attachedBitmap = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(22.dp)
                                .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove attached image",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = "Image attached for Gemini Vision analysis",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricCyan
                    )
                }
            }
        }

        // Composer Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MidnightBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_attach_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = "Upload image",
                        tint = ElectricBlueBright
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            when (chatState.mode) {
                                AssistantMode.SEARCH -> "Search the live web..."
                                AssistantMode.MATH -> "Enter equation or math problem..."
                                AssistantMode.CODE -> "Describe code to write or debug..."
                                else -> "Message ANSH AI..."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MidnightSurfaceElevated,
                        unfocusedContainerColor = MidnightSurfaceElevated,
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = MidnightBorder
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!chatState.isGenerating && (inputText.isNotBlank() || attachedBitmap != null)) {
                                val text = inputText
                                val bmp = attachedBitmap
                                inputText = ""
                                attachedBitmap = null
                                onSendMessage(text, bmp)
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )

                IconButton(
                    onClick = onOpenVoiceChat,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_voice_shortcut")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Voice Chat",
                        tint = ElectricCyan
                    )
                }

                if (chatState.isGenerating) {
                    FilledIconButton(
                        onClick = onStopGeneration,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Stop generation"
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            val text = inputText
                            val bmp = attachedBitmap
                            inputText = ""
                            attachedBitmap = null
                            onSendMessage(text, bmp)
                        },
                        enabled = inputText.isNotBlank() || attachedBitmap != null,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = ElectricBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send message"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatPromptCard(
    mode: AssistantMode,
    onSuggestionClick: (String) -> Unit
) {
    val suggestions = when (mode) {
        AssistantMode.SEARCH -> listOf(
            "What are the latest breakthroughs in fusion energy?",
            "Compare Android 16 developer highlights",
            "Current top AI open-weights models"
        )
        AssistantMode.MATH -> listOf(
            "Solve step-by-step: integral of x * e^(2x) dx",
            "Find eigenvalues of [[4, 2], [1, 3]]",
            "Explain Bayes' theorem with a medical test example"
        )
        AssistantMode.CODE -> listOf(
            "Write a thread-safe Kotlin LRU cache with coroutines",
            "Explain Jetpack Compose derivedStateOf vs remember",
            "Build a Python FastAPI endpoint with Pydantic validation"
        )
        else -> listOf(
            "Explain quantum computing simply with analogies",
            "Help me plan a 7-day focus & productivity routine",
            "Summarize key differences between TCP and QUIC"
        )
    }

    ElectricSurfaceCard(
        highlightBorder = true,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusPill(text = "${mode.label.uppercase()} READY")
            Text(
                text = "Start a conversation with ANSH AI",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Upload an image for vision analysis or try one of these prompts:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            suggestions.forEach { s ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MidnightSurfaceElevated,
                    border = BorderStroke(1.dp, MidnightBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSuggestionClick(s) }
                ) {
                    Text(
                        text = s,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricBlueBright,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isCopied: Boolean,
    showRegenerate: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onCitationClick: (String) -> Unit
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) ElectricBlue else MaterialTheme.colorScheme.surface,
            border = if (isUser) null else BorderStroke(1.dp, MidnightBorder),
            modifier = Modifier.widthIn(max = 540.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isUser && message.hasImage) {
                    StatusPill(text = "IMAGE ATTACHED", accentColor = Color.White)
                }
                if (isUser) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                } else {
                    AnshMarkdownMessage(markdown = message.text)
                }

                val citations = message.citations()
                if (citations.isNotEmpty()) {
                    CitationsRow(citations = citations, onCitationClick = onCitationClick)
                }
            }
        }

        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                TextButton(
                    onClick = onCopy,
                    modifier = Modifier.testTag("copy_response_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy response",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isCopied) "Copied" else "Copy", style = MaterialTheme.typography.labelSmall)
                }
                if (showRegenerate) {
                    TextButton(
                        onClick = onRegenerate,
                        modifier = Modifier.testTag("regenerate_response_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Regenerate response",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Regenerate", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantBubble(
    streamingText: String,
    citations: List<Citation>,
    onStop: () -> Unit,
    onCitationClick: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = ElectricCyan,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "ANSH AI is thinking...",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricBlueBright
                    )
                }
                TextButton(onClick = onStop) {
                    Text("Stop", color = MaterialTheme.colorScheme.error)
                }
            }

            if (streamingText.isNotBlank()) {
                AnshMarkdownMessage(markdown = streamingText)
            }
            if (citations.isNotEmpty()) {
                CitationsRow(citations = citations, onCitationClick = onCitationClick)
            }
        }
    }
}

@Composable
private fun CitationsRow(
    citations: List<Citation>,
    onCitationClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Sources",
            style = MaterialTheme.typography.labelSmall,
            color = ElectricCyan
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            citations.forEach { citation ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MidnightSurfaceElevated,
                    border = BorderStroke(1.dp, NebulaPurple.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onCitationClick(citation.url) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = citation.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 180.dp)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Open source",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

suspend fun loadBitmapFromUri(
    context: android.content.Context,
    uri: Uri,
    maxDimension: Int = 1600
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val maxSide = max(info.size.width, info.size.height)
                if (maxSide > maxDimension) {
                    val scale = maxDimension.toFloat() / maxSide.toFloat()
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                }
                decoder.isMutableRequired = true
            }
        } else {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            var sampleSize = 1
            val maxSide = max(boundsOpts.outWidth, boundsOpts.outHeight)
            while (maxSide / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        }
    } catch (_: Exception) {
        null
    }
}
