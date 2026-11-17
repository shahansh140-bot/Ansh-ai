package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.SUPPORTED_VIDEO_MODEL
import com.example.data.VideoDurationSpec
import com.example.data.VideoErrorCategory
import com.example.data.VideoGenerationMode
import com.example.model.AppConfig
import com.example.model.UiState
import com.example.model.VideoCreationLog
import com.example.ui.components.ElectricSurfaceCard
import com.example.ui.components.GlowingPrimaryButton
import com.example.ui.components.StatusPill
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightSurfaceElevated
import com.example.ui.theme.NebulaPurple
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.VideoGenUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private data class VideoStyleOption(
    val label: String,
    val drawableRes: Int?,
    val fallbackGradient: List<Color>
)

@Composable
fun VideosScreen(
    appConfig: AppConfig,
    videoGenState: VideoGenUiState = VideoGenUiState.Idle,
    videoLogsState: UiState<List<VideoCreationLog>> = UiState.Success(emptyList()),
    onGenerateVideo: (
        backendUrl: String,
        prompt: String,
        mode: VideoGenerationMode,
        style: String,
        aspectRatio: String,
        duration: String,
        sourceBitmap: Bitmap?
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onExtendVideo: (
        backendUrl: String,
        sourceLog: VideoCreationLog,
        continuationPrompt: String,
        extensionDuration: String
    ) -> Unit = { _, _, _, _ -> },
    onCancelGeneration: () -> Unit = {},
    onResetResult: () -> Unit = {},
    onSelectHistoryVideo: (VideoCreationLog) -> Unit = {},
    onDeleteVideoLog: (VideoCreationLog) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var modeTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Text to Video, 1 = Image to Video
    var textPrompt by rememberSaveable { mutableStateOf("") }
    var animationPrompt by rememberSaveable { mutableStateOf("") }
    var selectedStyle by rememberSaveable { mutableStateOf("Cinematic") }
    var selectedAspect by rememberSaveable { mutableStateOf("16:9") }
    var selectedDurationSec by rememberSaveable { mutableIntStateOf(VideoDurationSpec.DEFAULT_GENERATE_SECONDS) }
    var extendPrompt by rememberSaveable { mutableStateOf("") }
    var selectedExtendSec by rememberSaveable { mutableIntStateOf(VideoDurationSpec.DEFAULT_EXTEND_SECONDS) }
    var sourceImage by remember { mutableStateOf<Bitmap?>(null) }
    var showPricingSheet by remember { mutableStateOf(false) }
    var downloadFeedback by remember { mutableStateOf<String?>(null) }
    var isSavingMp4 by remember { mutableStateOf(false) }
    var showFullscreenPlayer by remember { mutableStateOf(false) }

    val bundledBackendUrl = stringResource(R.string.video_backend_url).trim()
    val effectiveBackendUrl = remember(appConfig.videoBackendUrl, bundledBackendUrl) {
        appConfig.videoBackendUrl.trim().ifBlank { bundledBackendUrl }.trimEnd('/')
    }
    val isGenerating = videoGenState is VideoGenUiState.Generating
    val isAdminEnabled = appConfig.videoGenEnabled

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                sourceImage = loadBitmapFromUri(context, uri)
            }
        }
    }

    val styleOptions = remember {
        listOf(
            VideoStyleOption("Cinematic", R.drawable.thumb_style_cinematic, listOf(Color(0xFF1D4ED8), Color(0xFFF59E0B))),
            VideoStyleOption("Anime", R.drawable.thumb_style_anime, listOf(Color(0xFF0284C7), Color(0xFF9B4DFF))),
            VideoStyleOption("Realistic", R.drawable.thumb_style_realistic, listOf(Color(0xFF059669), Color(0xFF1677FF))),
            VideoStyleOption("Nature", null, listOf(Color(0xFF047857), Color(0xFF0EA5E9))),
            VideoStyleOption("Creative", null, listOf(Color(0xFF7C3AED), Color(0xFF00D4FF)))
        )
    }
    val aspects = listOf("16:9", "9:16")

    if (showPricingSheet) {
        AlertDialog(
            onDismissRequest = { showPricingSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Video API Setup & Duration Guide", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Model & Supported Durations:",
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricBlueBright
                    )
                    Text(
                        text = "• Uses $SUPPORTED_VIDEO_MODEL via the Gemini Interactions API for Text-to-Video, Image-to-Video, and Extend Video.\n" +
                            "• Supported clip lengths: 3s–15s per generation (default 10 seconds).\n" +
                            "• 1s and 2s are disabled because $SUPPORTED_VIDEO_MODEL requires at least 3 seconds per clip.\n" +
                            "• Extend Video appends 3s–10s per step (up to 30s cumulative) via previous_interaction_id.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Server-Side Key & Billing:",
                        style = MaterialTheme.typography.titleSmall,
                        color = WarningAmber
                    )
                    Text(
                        text = "• Your GEMINI_API_KEY lives exclusively inside Firebase Cloud Secret Manager (`anshVideoApi`) and is never bundled in the Android APK.\n" +
                            "• Requires Google Cloud Blaze / paid Gemini API billing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPricingSheet = false }) {
                    Text("Got it", color = ElectricBlueBright)
                }
            }
        )
    }

    if (showFullscreenPlayer && videoGenState is VideoGenUiState.Success) {
        val fullFile = File(videoGenState.videoLog.localFilePath)
        if (fullFile.exists() && fullFile.length() > 0L) {
            Dialog(
                onDismissRequest = { showFullscreenPlayer = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    VideoPlayerBox(
                        videoFile = fullFile,
                        autoPlay = true,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { showFullscreenPlayer = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close fullscreen video",
                            tint = Color.White
                        )
                    }
                }
            }
        } else {
            showFullscreenPlayer = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("videos_lazy_column")
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060B1D),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header: Video Generation + Setup/Billing Icon
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Video Generation",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Powered by $SUPPORTED_VIDEO_MODEL (Secure Cloud Proxy)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { showPricingSheet = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("video_setup_crown_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WorkspacePremium,
                        contentDescription = "Video API setup and billing",
                        tint = WarningAmber,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Segmented Pill Toggle: Text to Video | Image to Video
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0E152B),
                border = BorderStroke(1.dp, Color(0xFF21315C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isText = modeTab == 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isText) ElectricBlue else Color.Transparent)
                            .clickable(enabled = !isGenerating) { modeTab = 0 }
                            .testTag("video_tab_text"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Text to Video",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isText) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (!isText) ElectricBlue else Color.Transparent)
                            .clickable(enabled = !isGenerating) { modeTab = 1 }
                            .testTag("video_tab_image"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Image to Video",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (!isText) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Prompt Input Card
        item {
            ElectricSurfaceCard(
                containerColor = Color(0xFF0E152B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (modeTab == 0) {
                        OutlinedTextField(
                            value = textPrompt,
                            onValueChange = { if (it.length <= 1000) textPrompt = it },
                            enabled = !isGenerating,
                            placeholder = {
                                Text(
                                    "Describe your video...\ne.g. A beautiful sunset over the ocean",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MidnightSurfaceElevated,
                                unfocusedContainerColor = MidnightSurfaceElevated,
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = MidnightBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("video_text_prompt_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "${textPrompt.length}/1000",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isGenerating,
                            border = BorderStroke(1.dp, MidnightBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("video_upload_image_button")
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (sourceImage == null) "Upload Source Image" else "Change Source Image")
                        }

                        sourceImage?.let { bmp ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Source image for animation",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { if (!isGenerating) sourceImage = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(36.dp)
                                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove source image",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = animationPrompt,
                            onValueChange = { if (it.length <= 1000) animationPrompt = it },
                            enabled = !isGenerating,
                            placeholder = { Text("Describe the camera motion or animation...") },
                            minLines = 2,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MidnightSurfaceElevated,
                                unfocusedContainerColor = MidnightSurfaceElevated,
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = MidnightBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("video_animation_prompt_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "${animationPrompt.length}/1000",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Visual Video Style Thumbnails Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Video Style",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    styleOptions.forEach { option ->
                        val selected = selectedStyle == option.label
                        Box(
                            modifier = Modifier
                                .width(106.dp)
                                .height(74.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) ElectricCyan else MidnightBorder,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable(enabled = !isGenerating) { selectedStyle = option.label }
                                .testTag("video_style_${option.label.lowercase()}")
                        ) {
                            if (option.drawableRes != null) {
                                Image(
                                    painter = painterResource(id = option.drawableRes),
                                    contentDescription = "${option.label} video style",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.linearGradient(option.fallbackGradient))
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                                        )
                                    ),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Electric Blue 1s–15s Video Duration Selector (Compact 5x3 Grid)
        item {
            ElectricSurfaceCard(
                containerColor = Color(0xFF0E152B),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_duration_selector_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = ElectricBlueBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Video Duration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        StatusPill(
                            text = "Selected: ${VideoDurationSpec.formatDurationLabel(selectedDurationSec)}",
                            accentColor = ElectricCyan
                        )
                    }

                    // 3 rows of 5 pills: 1s–15s (every duration has its own tile)
                    val rows = VideoDurationSpec.ALL_UI_SECONDS.chunked(5)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { rowSeconds ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowSeconds.forEach { sec ->
                                    val supported = VideoDurationSpec.isGenerateDurationSupported(sec)
                                    val selected = supported && selectedDurationSec == sec
                                    val labelText = VideoDurationSpec.formatDurationLabel(sec)
                                    val tileBg = when {
                                        !supported -> Color(0xFF10131F)
                                        selected -> ElectricBlue
                                        else -> MidnightSurfaceElevated
                                    }
                                    val tileBorder = when {
                                        !supported -> MidnightBorder.copy(alpha = 0.45f)
                                        selected -> ElectricCyan
                                        else -> Color(0xFF21315C)
                                    }
                                    Surface(
                                        onClick = {
                                            if (supported && !isGenerating) {
                                                selectedDurationSec = sec
                                            }
                                        },
                                        enabled = supported && !isGenerating,
                                        shape = RoundedCornerShape(12.dp),
                                        color = tileBg,
                                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, tileBorder),
                                        shadowElevation = if (selected) 6.dp else 0.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp)
                                            .semantics {
                                                contentDescription = if (supported) {
                                                    labelText
                                                } else {
                                                    "$labelText (Unsupported)"
                                                }
                                            }
                                            .testTag("video_duration_${sec}s")
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 2.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "${sec}s",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = when {
                                                    !supported -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                    selected -> Color.White
                                                    else -> Color.White.copy(alpha = 0.9f)
                                                }
                                            )
                                            Text(
                                                text = when {
                                                    !supported -> "Unsupported"
                                                    sec == VideoDurationSpec.DEFAULT_GENERATE_SECONDS -> "Default"
                                                    else -> if (sec == 1) "second" else "sec"
                                                },
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                color = when {
                                                    !supported -> WarningAmber.copy(alpha = 0.72f)
                                                    selected -> Color.White.copy(alpha = 0.92f)
                                                    else -> ElectricBlueBright.copy(alpha = 0.75f)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Honest explanation of supported vs unsupported durations
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0A1124),
                        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_duration_support_note")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "1s and 2s are disabled (Unsupported): $SUPPORTED_VIDEO_MODEL requires 3s–15s per clip (minimum 3 seconds). Default: 10 seconds.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Aspect Ratio + Selected Duration Summary + Generate Video CTA
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Aspect Ratio", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            aspects.forEach { ratio ->
                                FilterChip(
                                    selected = selectedAspect == ratio,
                                    enabled = !isGenerating,
                                    onClick = { selectedAspect = ratio },
                                    label = { Text(ratio) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricBlue.copy(alpha = 0.25f),
                                        selectedLabelColor = ElectricBlueBright
                                    )
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Ready to Generate", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        StatusPill(
                            text = "${VideoDurationSpec.formatDurationLabel(selectedDurationSec)} • $selectedAspect",
                            accentColor = ElectricBlueBright
                        )
                    }
                }

                GlowingPrimaryButton(
                    onClick = {
                        downloadFeedback = null
                        val activeMode = if (modeTab == 0) VideoGenerationMode.TEXT_TO_VIDEO else VideoGenerationMode.IMAGE_TO_VIDEO
                        val activePrompt = if (modeTab == 0) textPrompt else animationPrompt
                        val activeBitmap = if (modeTab == 1) sourceImage else null
                        onGenerateVideo(
                            effectiveBackendUrl,
                            activePrompt,
                            activeMode,
                            selectedStyle,
                            selectedAspect,
                            "${selectedDurationSec}s",
                            activeBitmap
                        )
                    },
                    enabled = isAdminEnabled && !isGenerating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_generate_button")
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isGenerating -> "Generating Video..."
                            !isAdminEnabled -> "Video Generation Disabled (Admin)"
                            else -> "Generate ${selectedDurationSec}s Video"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Upfront Admin / Backend Configuration Status Banner (when disabled or proxy URL missing)
        if (!isAdminEnabled || effectiveBackendUrl.isBlank()) {
            item {
                ElectricSurfaceCard(
                    highlightBorder = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_config_status_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Backend configuration required",
                                tint = WarningAmber
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (!isAdminEnabled) {
                                        "Video Generation Disabled by Admin"
                                    } else {
                                        "Server-Side Video Backend Not Configured"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = WarningAmber
                                )
                                Text(
                                    text = if (!isAdminEnabled) {
                                        "Turn ON Video Generation and set your Cloud Functions proxy URL in Super Admin Console -> Config."
                                    } else {
                                        "Deploy the `anshVideoApi` Cloud Function with GEMINI_API_KEY in Cloud Secret Manager, then paste its HTTPS URL in Super Admin Console -> Config."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showPricingSheet = true },
                            border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("video_pricing_info_button")
                        ) {
                            Icon(Icons.Rounded.Paid, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View Video API Billing & Backend Setup")
                        }
                    }
                }
            }
        }

        // Active Generation Progress, Error, or Real Video Result + Extend Video Card
        when (videoGenState) {
            is VideoGenUiState.Idle -> Unit

            is VideoGenUiState.Generating -> {
                item {
                    ElectricSurfaceCard(
                        highlightBorder = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_generating_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = ElectricCyan)
                            Text(
                                text = videoGenState.stage,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusPill(text = "${videoGenState.elapsedSeconds}s elapsed", accentColor = ElectricCyan)
                                videoGenState.interactionId?.take(12)?.let { shortId ->
                                    StatusPill(text = "Job $shortId", accentColor = NebulaPurple)
                                }
                            }
                            Text(
                                text = "Asynchronous cloud rendering in progress. Waiting for confirmed MP4 output and duration verification before enabling playback or download.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            OutlinedButton(
                                onClick = onCancelGeneration,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.65f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("video_cancel_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cancel Generation", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            is VideoGenUiState.Error -> {
                item {
                    ElectricSurfaceCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_error_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = videoGenState.category.title,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = videoGenState.category.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    StatusPill(
                                        text = videoGenState.category.name,
                                        accentColor = WarningAmber
                                    )
                                }
                            }

                            Text(
                                text = videoGenState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (videoGenState.category == VideoErrorCategory.MISSING_API_CONFIG ||
                                    videoGenState.category == VideoErrorCategory.BILLING_REQUIRED
                                ) {
                                    OutlinedButton(
                                        onClick = { showPricingSheet = true },
                                        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Text("Setup Guide")
                                    }
                                }
                                OutlinedButton(
                                    onClick = onResetResult,
                                    border = BorderStroke(1.dp, MidnightBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("video_error_dismiss_button")
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }

            is VideoGenUiState.Success -> {
                item {
                    val videoLog = videoGenState.videoLog
                    val verifiedFile = remember(videoLog.localFilePath) {
                        if (videoLog.localFilePath.isBlank()) {
                            null
                        } else {
                            File(videoLog.localFilePath).takeIf { it.exists() && it.length() > 0L }
                        }
                    }

                    if (verifiedFile == null) {
                        ElectricSurfaceCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("video_missing_file_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "MP4 File Unavailable",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "The generated MP4 could not be verified on device storage. Download and playback are disabled.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        val actualDisplayDuration = remember(videoLog.actualDurationMs, videoLog.duration) {
                            if (videoLog.actualDurationMs > 0L) {
                                VideoDurationSpec.formatMeasuredSeconds(videoLog.actualDurationMs, videoLog.duration)
                            } else {
                                videoLog.duration
                            }
                        }
                        val currentSecondsApprox = remember(videoLog.actualDurationMs, videoLog.duration) {
                            if (videoLog.actualDurationMs > 0L) {
                                ((videoLog.actualDurationMs + 500L) / 1000L).toInt()
                            } else {
                                VideoDurationSpec.parseSecondsOrNull(videoLog.duration) ?: 10
                            }
                        }
                        val canExtendMore = videoLog.interactionId.isNotBlank() &&
                            isAdminEnabled &&
                            currentSecondsApprox + VideoDurationSpec.MIN_EXTEND_SECONDS <= VideoDurationSpec.MAX_CUMULATIVE_SECONDS

                        ElectricSurfaceCard(
                            highlightBorder = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("video_result_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Video Result",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = videoLog.prompt,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { showFullscreenPlayer = true },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("video_fullscreen_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Fullscreen,
                                            contentDescription = "Fullscreen video",
                                            tint = ElectricCyan
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StatusPill(
                                        text = "Final Duration: $actualDisplayDuration",
                                        accentColor = ElectricCyan,
                                        modifier = Modifier.testTag("video_final_duration_pill")
                                    )
                                    StatusPill(text = videoLog.aspectRatio, accentColor = ElectricBlueBright)
                                    StatusPill(text = videoLog.style, accentColor = NebulaPurple)
                                    if (videoLog.segmentCount > 1L) {
                                        StatusPill(text = "${videoLog.segmentCount} segments", accentColor = ElectricCyan)
                                    }
                                }

                                // Real Media3 ExoPlayer Preview Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(if (videoLog.aspectRatio == "9:16") 9f / 16f else 16f / 9f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.Black)
                                        .border(1.dp, MidnightBorder, RoundedCornerShape(14.dp))
                                        .testTag("video_player_container")
                                ) {
                                    if (!showFullscreenPlayer) {
                                        VideoPlayerBox(
                                            videoFile = verifiedFile,
                                            autoPlay = false,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                // Download MP4 — enabled ONLY after verified real video completion
                                Button(
                                    onClick = {
                                        if (!isSavingMp4) {
                                            isSavingMp4 = true
                                            scope.launch {
                                                val saved = saveMp4ToGallery(context, verifiedFile)
                                                isSavingMp4 = false
                                                downloadFeedback = if (saved) {
                                                    "Saved MP4 to Movies/ANSH AI."
                                                } else {
                                                    "Unable to save MP4 to device gallery."
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isSavingMp4,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricBlue,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("video_download_button")
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isSavingMp4) "Saving MP4..." else "Download MP4", fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = { shareMp4File(context, verifiedFile) },
                                    border = BorderStroke(1.dp, MidnightBorder),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("video_share_button")
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Share")
                                }

                                // Extend Video Card (continuity-preserving chaining via previous_interaction_id)
                                HorizontalDivider(color = MidnightBorder.copy(alpha = 0.6f))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF0A1124))
                                        .border(1.dp, ElectricBlue.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                        .padding(12.dp)
                                        .testTag("video_extend_section"),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AddCircleOutline,
                                                contentDescription = null,
                                                tint = ElectricCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Extend Video",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        StatusPill(
                                            text = "Max ${VideoDurationSpec.MAX_CUMULATIVE_SECONDS}s",
                                            accentColor = NebulaPurple
                                        )
                                    }

                                    Text(
                                        text = "Append a new segment while preserving scene continuity, characters, audio, and ${videoLog.style} style.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (!canExtendMore) {
                                        Text(
                                            text = if (videoLog.interactionId.isBlank()) {
                                                "Extension unavailable: missing backend interaction ID for this clip."
                                            } else if (!isAdminEnabled) {
                                                "Video extension is disabled while Video Generation is turned off in Admin Config."
                                            } else {
                                                "Maximum cumulative video length (${VideoDurationSpec.MAX_CUMULATIVE_SECONDS}s) reached for this sequence."
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = WarningAmber
                                        )
                                    } else {
                                        OutlinedTextField(
                                            value = extendPrompt,
                                            onValueChange = { if (it.length <= 500) extendPrompt = it },
                                            placeholder = {
                                                Text(
                                                    "What happens next? e.g. Camera pans up into the aurora sky",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            minLines = 2,
                                            maxLines = 3,
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = MidnightSurfaceElevated,
                                                unfocusedContainerColor = MidnightSurfaceElevated,
                                                focusedBorderColor = ElectricCyan,
                                                unfocusedBorderColor = MidnightBorder
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("video_extend_prompt_input")
                                        )

                                        Text(
                                            text = "Extension Segment Length (3s–10s supported per step)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricBlueBright
                                        )

                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            VideoDurationSpec.SUPPORTED_EXTEND_SECONDS.forEach { extSec ->
                                                val fitsCap = currentSecondsApprox + extSec <= VideoDurationSpec.MAX_CUMULATIVE_SECONDS
                                                FilterChip(
                                                    selected = selectedExtendSec == extSec && fitsCap,
                                                    enabled = fitsCap && !isGenerating,
                                                    onClick = { selectedExtendSec = extSec },
                                                    label = { Text("+${extSec}s") },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = ElectricBlue,
                                                        selectedLabelColor = Color.White
                                                    ),
                                                    modifier = Modifier.testTag("video_extend_dur_${extSec}s")
                                                )
                                            }
                                        }

                                        val extendTargetAllowed = currentSecondsApprox + selectedExtendSec <= VideoDurationSpec.MAX_CUMULATIVE_SECONDS
                                        Button(
                                            onClick = {
                                                downloadFeedback = null
                                                onExtendVideo(
                                                    effectiveBackendUrl,
                                                    videoLog,
                                                    extendPrompt,
                                                    "${selectedExtendSec}s"
                                                )
                                            },
                                            enabled = extendTargetAllowed && !isGenerating,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = ElectricBlue,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("video_extend_button")
                                        ) {
                                            Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Extend Video (+${selectedExtendSec}s)",
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        downloadFeedback = null
                                        extendPrompt = ""
                                        onResetResult()
                                    },
                                    border = BorderStroke(1.dp, NebulaPurple.copy(alpha = 0.75f)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("video_generate_another_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generate Another Video", color = Color.White)
                                }

                                downloadFeedback?.let { feedback ->
                                    Text(
                                        text = feedback,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ElectricCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Your Videos History Section (Real Firestore logs — zero simulated videos)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Videos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusPill(text = "GEMINI OMNI 1.2", accentColor = NebulaPurple)
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = ElectricBlueBright,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        when (videoLogsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(24.dp))
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Text(
                        text = videoLogsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            is UiState.Success -> {
                if (videoLogsState.data.isEmpty()) {
                    item {
                        ElectricSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Movie,
                                    contentDescription = null,
                                    tint = ElectricBlueBright,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text("No Generated Videos Yet", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Completed videos rendered by $SUPPORTED_VIDEO_MODEL appear here with MP4 playback, Extend Video, download, and share controls.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Real cloud output only: no simulated videos or downloads.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(videoLogsState.data, key = { it.id.ifBlank { "${it.interactionId}_${it.prompt}" } }) { log ->
                        val hasPlayableFile = remember(log.localFilePath, log.status) {
                            log.status == "SUCCESS" &&
                                log.localFilePath.isNotBlank() &&
                                File(log.localFilePath).let { it.exists() && it.length() > 0L }
                        }
                        val historyDuration = remember(log.actualDurationMs, log.duration) {
                            if (log.actualDurationMs > 0L) {
                                VideoDurationSpec.formatMeasuredSeconds(log.actualDurationMs, log.duration)
                            } else {
                                log.duration
                            }
                        }
                        ElectricSurfaceCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("video_history_card_${log.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MidnightSurfaceElevated,
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (hasPlayableFile) Icons.Rounded.PlayArrow else Icons.Rounded.Movie,
                                            contentDescription = null,
                                            tint = if (hasPlayableFile) ElectricCyan else WarningAmber
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = log.prompt,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        StatusPill(text = historyDuration, accentColor = ElectricBlueBright)
                                        StatusPill(text = log.aspectRatio, accentColor = NebulaPurple)
                                        StatusPill(
                                            text = log.status,
                                            accentColor = if (log.status == "SUCCESS") ElectricCyan else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    if (!hasPlayableFile) {
                                        Text(
                                            text = log.errorMessage.ifBlank { "MP4 not cached on this device." },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (hasPlayableFile) {
                                    IconButton(
                                        onClick = { onSelectHistoryVideo(log) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("video_history_play_${log.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Play video",
                                            tint = ElectricCyan
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onDeleteVideoLog(log) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("video_history_delete_${log.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete video log",
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

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerBox(
    videoFile: File,
    autoPlay: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playbackError by remember(videoFile.absolutePath) { mutableStateOf<String?>(null) }

    val exoPlayer = remember(videoFile.absolutePath) {
        runCatching {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = autoPlay
                prepare()
            }
        }.onFailure { err ->
            playbackError = err.localizedMessage ?: "Video player unavailable on this device."
        }.getOrNull()
    }

    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) {
            return@DisposableEffect onDispose {}
        }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "Failed to decode MP4 video stream."
            }
        }
        exoPlayer.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    if (playbackError != null || exoPlayer == null) {
        Box(
            modifier = modifier
                .background(Color(0xFF0A0F20))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = playbackError ?: "Initializing video player...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    } else {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = modifier
        )
    }
}

private suspend fun saveMp4ToGallery(context: Context, sourceFile: File): Boolean =
    withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return@withContext false
        try {
            val filename = "ANSH_AI_Video_${System.currentTimeMillis()}.mp4"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ANSH AI")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return@withContext false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                } ?: return@withContext false
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                true
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    "ANSH AI"
                ).apply { mkdirs() }
                val dest = File(dir, filename)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(dest).use { out ->
                        input.copyTo(out)
                    }
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

private fun shareMp4File(context: Context, file: File) {
    if (!file.exists() || file.length() == 0L) return
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share ANSH AI Video"))
}
