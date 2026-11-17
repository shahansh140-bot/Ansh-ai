package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.components.CosmicStarryBackground
import com.example.ui.components.ElectricSurfaceCard
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightSurfaceElevated
import com.example.ui.theme.NebulaPurple
import com.example.viewmodel.VoiceChatUiState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun VoiceChatScreen(
    voiceState: VoiceChatUiState,
    speechRate: Float,
    onUpdateAvailability: (Boolean) -> Unit,
    onListeningChange: (Boolean, String) -> Unit,
    onRmsChange: (Float) -> Unit,
    onSpeakingChange: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onVoiceError: (String?) -> Unit,
    onSubmitQuery: (String, (String) -> Unit) -> Unit,
    onReturnToChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onReturnToChat)

    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var typedFallback by remember { mutableStateOf("") }
    var showTypeBar by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }

    val tts = remember(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
                ttsReady = true
            }
        }
        engine
    }

    LaunchedEffect(ttsReady, speechRate) {
        if (ttsReady) {
            tts.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
        }
    }

    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onSpeakingChange(true)
            }

            override fun onDone(utteranceId: String?) {
                onSpeakingChange(false)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onSpeakingChange(false)
            }
        })
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    val speakReply: (String) -> Unit = remember(tts, ttsReady, voiceState.isMuted) {
        { text ->
            if (ttsReady && !voiceState.isMuted) {
                val clean = text.replace("```", " ").replace("*", "").replace("#", "")
                tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "ansh_voice_${System.currentTimeMillis()}")
            }
        }
    }

    val speechAvailable = remember(context) {
        SpeechRecognizer.isRecognitionAvailable(context)
    }
    LaunchedEffect(speechAvailable) {
        onUpdateAvailability(speechAvailable)
        if (!speechAvailable) {
            showTypeBar = true
        }
    }

    val speechRecognizer = remember(context, speechAvailable) {
        if (speechAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onListeningChange(true, "")
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                onRmsChange(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                onListeningChange(false, voiceState.partialTranscript)
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap the mic and try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Speech recognition network error."
                    else -> "Speech recognizer unavailable (code $error)."
                }
                onVoiceError(msg)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalText = matches?.firstOrNull().orEmpty()
                if (finalText.isNotBlank()) {
                    onSubmitQuery(finalText, speakReply)
                } else {
                    onVoiceError("No speech recognized. Try again or type below.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (partial.isNotBlank()) {
                    onListeningChange(true, partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    val startListeningAction: () -> Unit = {
        if (!speechAvailable || speechRecognizer == null) {
            showTypeBar = true
            onVoiceError("Device speech recognition service is unavailable. Use the text input below.")
        } else {
            tts.stop()
            onSpeakingChange(false)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer.startListening(intent)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            startListeningAction()
        } else {
            onVoiceError("Microphone permission denied. Enable it in Settings or type below.")
        }
    }

    val isVoiceActive = voiceState.isListening || voiceState.isSpeaking || voiceState.isThinking
    val wavePhase: Float
    val haloPulse: Float
    if (isVoiceActive) {
        val waveTransition = rememberInfiniteTransition(label = "waveAnim")
        wavePhase = waveTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhase"
        ).value
        haloPulse = waveTransition.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "haloPulse"
        ).value
    } else {
        wavePhase = 0.4f
        haloPulse = 0.96f
    }

    CosmicStarryBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar matching mockup: Back | Voice Chat | Speaker & Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        tts.stop()
                        speechRecognizer?.cancel()
                        onReturnToChat()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_return_to_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Return to Chat",
                        tint = Color.White
                    )
                }

                Text(
                    text = "Voice Chat",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            onToggleMute()
                            if (!voiceState.isMuted) {
                                tts.stop()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("voice_mute_button")
                    ) {
                        Icon(
                            imageVector = if (voiceState.isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = if (voiceState.isMuted) "Unmute voice output" else "Mute voice output",
                            tint = if (voiceState.isMuted) MaterialTheme.colorScheme.error else Color.White
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Voice options",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (showTypeBar) "Hide keyboard input" else "Type instead of speaking") },
                                leadingIcon = { Icon(Icons.Rounded.Keyboard, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showTypeBar = !showTypeBar
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Return to Chat") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    tts.stop()
                                    speechRecognizer?.cancel()
                                    onReturnToChat()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.25f))

            // Concentric Glowing Blue Microphone Orb (Centerpiece matching Top-Right mockup)
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .drawWithCache {
                        val ringStroke = Stroke(width = 1.5.dp.toPx())
                        val glowAlpha = if (voiceState.isListening) 0.58f else 0.35f
                        val glowBrush = Brush.radialGradient(
                            colors = listOf(
                                ElectricBlue.copy(alpha = glowAlpha),
                                ElectricCyan.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                        val outerColor = ElectricBlue.copy(alpha = 0.24f)
                        val innerColor = ElectricCyan.copy(alpha = 0.38f)
                        onDrawBehind {
                            val pulseRadius = size.minDimension * 0.5f * haloPulse
                            drawCircle(
                                brush = glowBrush,
                                radius = pulseRadius * 1.1f
                            )
                            drawCircle(
                                color = outerColor,
                                radius = size.minDimension * 0.44f * haloPulse,
                                style = ringStroke
                            )
                            drawCircle(
                                color = innerColor,
                                radius = size.minDimension * 0.33f,
                                style = ringStroke
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = if (voiceState.isListening) {
                                    listOf(ElectricCyan, ElectricBlue)
                                } else {
                                    listOf(Color(0xFF47A7FF), ElectricBlue, Color(0xFF0A4FD4))
                                }
                            )
                        )
                        .border(2.dp, ElectricCyan.copy(alpha = 0.75f), CircleShape)
                        .clickable {
                            if (voiceState.isListening) {
                                speechRecognizer?.stopListening()
                                onListeningChange(false, voiceState.partialTranscript)
                            } else if (hasMicPermission) {
                                startListeningAction()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        .testTag("voice_mic_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (voiceState.isListening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                        contentDescription = if (voiceState.isListening) "Stop listening" else "Start listening",
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val primaryHeadline = when {
                voiceState.isListening -> "Listening..."
                voiceState.isThinking -> "Thinking..."
                voiceState.isSpeaking -> "Speaking..."
                !voiceState.speechAvailable -> "Voice unavailable — type below"
                else -> "Tap to speak"
            }
            Text(
                text = primaryHeadline,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (voiceState.isListening) {
                    "Speak naturally to ANSH AI"
                } else {
                    "Tap the mic or switch to typing anytime"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Symmetrical Neon Cyan -> Electric Blue -> Purple Sound Wave Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val activeAmp = when {
                    voiceState.isListening -> ((voiceState.rmsDb + 2f) / 10f).coerceIn(0.3f, 1f)
                    voiceState.isSpeaking -> 0.85f
                    voiceState.isThinking -> 0.45f
                    else -> 0.22f
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bars = 38
                    val spacing = size.width / (bars + 1)
                    val centerY = size.height / 2f
                    val centerIdx = bars / 2f
                    for (i in 0 until bars) {
                        val x = spacing * (i + 1)
                        val distanceFactor = (1f - (abs(i - centerIdx) / centerIdx)).coerceIn(0.12f, 1f)
                        val norm = sin(wavePhase + i * 0.42f)
                        val barHeight = (6.dp.toPx() + (size.height * 0.82f * activeAmp * distanceFactor * abs(norm)))
                            .coerceIn(5.dp.toPx(), size.height * 0.92f)
                        val progress = i.toFloat() / bars.toFloat()
                        val color = when {
                            progress < 0.35f -> ElectricCyan
                            progress < 0.65f -> ElectricBlueBright
                            else -> NebulaPurple
                        }
                        drawLine(
                            color = color.copy(alpha = 0.92f),
                            start = Offset(x, centerY - barHeight / 2f),
                            end = Offset(x, centerY + barHeight / 2f),
                            strokeWidth = 3.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Live Transcript Glass Panel (only prominent when there is conversation/error)
            val hasTranscript = voiceState.partialTranscript.isNotBlank() ||
                voiceState.lastUserUtterance.isNotBlank() ||
                voiceState.lastAiResponse.isNotBlank() ||
                voiceState.isThinking ||
                !voiceState.errorMessage.isNullOrBlank() ||
                !voiceState.speechAvailable

            if (hasTranscript) {
                ElectricSurfaceCard(
                    highlightBorder = voiceState.isListening || voiceState.isSpeaking,
                    containerColor = Color(0xFF0C1327),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!voiceState.speechAvailable) {
                            Text(
                                text = "On-device SpeechRecognizer is unavailable here. Type below to ask ANSH AI and hear replies via Text-to-Speech.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (voiceState.partialTranscript.isNotBlank()) {
                            Text(
                                text = "\"${voiceState.partialTranscript}\"",
                                style = MaterialTheme.typography.titleSmall,
                                color = ElectricCyan
                            )
                        }
                        if (voiceState.lastUserUtterance.isNotBlank()) {
                            Text(
                                text = "You: ${voiceState.lastUserUtterance}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (voiceState.isThinking && voiceState.lastAiResponse.isBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = ElectricCyan,
                                    strokeWidth = 2.dp
                                )
                                Text("Thinking...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (voiceState.lastAiResponse.isNotBlank()) {
                            Text(
                                text = voiceState.lastAiResponse,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                        AnimatedVisibility(visible = !voiceState.errorMessage.isNullOrBlank()) {
                            Text(
                                text = voiceState.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(0.55f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Pill "■ Stop" Button + Toggle Keyboard
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF0E1630),
                    border = BorderStroke(
                        1.2.dp,
                        if (voiceState.isListening || voiceState.isSpeaking) ElectricCyan else ElectricBlue.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            speechRecognizer?.stopListening()
                            tts.stop()
                            onListeningChange(false, "")
                            onSpeakingChange(false)
                        }
                        .testTag("voice_stop_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Stop voice",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Stop",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                    }
                }

                IconButton(
                    onClick = { showTypeBar = !showTypeBar },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF121B36))
                        .border(1.dp, MidnightBorder, CircleShape)
                        .testTag("voice_toggle_keyboard_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Keyboard,
                        contentDescription = "Switch between typing and speaking",
                        tint = ElectricCyan
                    )
                }
            }

            // Typing fallback / hybrid mode
            AnimatedVisibility(visible = showTypeBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = typedFallback,
                        onValueChange = { typedFallback = it },
                        placeholder = { Text("Or type to speak with ANSH AI...") },
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MidnightSurfaceElevated,
                            unfocusedContainerColor = MidnightSurfaceElevated,
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = MidnightBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voice_text_input")
                    )
                    FilledIconButton(
                        onClick = {
                            val q = typedFallback.trim()
                            if (q.isNotEmpty()) {
                                typedFallback = ""
                                onSubmitQuery(q, speakReply)
                            }
                        },
                        enabled = typedFallback.isNotBlank() && !voiceState.isThinking,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = ElectricBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("voice_text_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send voice prompt"
                        )
                    }
                }
            }
        }
    }
}
