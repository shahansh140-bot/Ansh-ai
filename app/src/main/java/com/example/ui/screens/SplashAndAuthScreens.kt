package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.data.onGoogleSignInClicked
import com.example.data.signOut
import com.example.ui.components.AnshAiLogo
import com.example.ui.components.AnshWordmark
import com.example.ui.components.CosmicStarryBackground
import com.example.ui.components.ElectricSurfaceCard
import com.example.ui.components.GlowingPrimaryButton
import com.example.ui.components.StatusPill
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NebulaPurple

@Composable
fun SplashScreen(
    subtitle: String = "Intelligence Without Limits",
    modifier: Modifier = Modifier
) {
    val spinTransition = rememberInfiniteTransition(label = "splashSpin")
    val angle by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringAngle"
    )

    CosmicStarryBackground(
        showHorizonArc = true,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnshAiLogo(size = 120.dp, glowing = true)

            Spacer(modifier = Modifier.height(4.dp))

            AnshWordmark(fontSize = 38.sp, italicAi = true)

            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Glowing sweep loader ring matching reference
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .drawWithCache {
                        val stroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        val trackStroke = Stroke(width = 3.5.dp.toPx())
                        val sweepBrush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                ElectricBlue,
                                ElectricCyan,
                                Color.White
                            )
                        )
                        onDrawBehind {
                            drawCircle(
                                color = Color(0xFF1A2952),
                                style = trackStroke
                            )
                            rotate(angle) {
                                drawArc(
                                    brush = sweepBrush,
                                    startAngle = 0f,
                                    sweepAngle = 240f,
                                    useCenter = false,
                                    style = stroke
                                )
                            }
                        }
                    }
            )

            Text(
                text = if (subtitle.contains("Admin", ignoreCase = true)) {
                    subtitle
                } else {
                    "Loading your AI assistant..."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    CosmicStarryBackground(
        showHorizonArc = true,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                AnshAiLogo(size = 98.dp, glowing = true)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill(text = "POWERED BY GEMINI AI & FIREBASE")
                    AnshWordmark(fontSize = 34.sp, italicAi = true)
                    Text(
                        text = "Intelligence Without Limits.",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricBlueBright,
                        textAlign = TextAlign.Center
                    )
                }

                ElectricSurfaceCard(
                    highlightBorder = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        FeatureRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "Multimodal AI & Voice Studio",
                            subtitle = "Streaming Gemini chat, live voice, image studio, math & code."
                        )
                        FeatureRow(
                            icon = Icons.Rounded.Shield,
                            title = "Private Cloud Sync",
                            subtitle = "Your conversations are isolated to your verified Google account."
                        )
                        FeatureRow(
                            icon = Icons.Rounded.Lock,
                            title = "Zero-Trust App Check",
                            subtitle = "Protected by Google Sign-In & Firebase App Check attestation."
                        )
                    }
                }

                AnimatedVisibility(visible = !errorMessage.isNullOrBlank(), enter = fadeIn()) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                GlowingPrimaryButton(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                                errorMessage = "Sign-in was cancelled. Select a Google account to continue."
                            }
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Connecting securely...", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
                    }
                }

                Text(
                    text = "Protected by Google Sign-In. One tap creates or restores your private ANSH AI workspace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = ElectricBlue.copy(alpha = 0.16f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AccountSuspendedScreen(
    email: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ElectricSurfaceCard(
            highlightBorder = true,
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    contentDescription = "Account Suspended",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(52.dp)
                )
                Text(
                    text = "Account Disabled",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Your ANSH AI account ($email) has been disabled by the Super Admin. Please contact the app owner for reactivation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    onClick = { signOut(credentialManager, scope) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("suspended_logout_button")
                ) {
                    Text("Sign Out")
                }
            }
        }
    }
}
