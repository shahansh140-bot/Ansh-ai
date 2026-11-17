package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightSurfaceElevated
import com.example.ui.theme.NebulaPurple
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun AnshWordmark(
    fontSize: TextUnit = 24.sp,
    italicAi: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = SpaceGroteskFontFamily
                )
            ) {
                append("ANSH ")
            }
            withStyle(
                SpanStyle(
                    color = ElectricBlueBright,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = if (italicAi) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = SpaceGroteskFontFamily
                )
            ) {
                append("AI")
            }
        },
        fontSize = fontSize,
        letterSpacing = 0.6.sp,
        modifier = modifier
    )
}

@Composable
fun CosmicStarryBackground(
    showHorizonArc: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val stars = rememberStaticStars()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF050A1B),
                        Color(0xFF070B17),
                        Color(0xFF080910)
                    )
                )
            )
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val topGlowCenter = Offset(size.width * 0.5f, size.height * 0.28f)
                    val topGlowRadius = size.maxDimension * 0.45f
                    val topGlowBrush = Brush.radialGradient(
                        colors = listOf(
                            ElectricBlue.copy(alpha = 0.22f),
                            NebulaPurple.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = topGlowCenter,
                        radius = topGlowRadius
                    )
                    val starPoints = stars.map { star ->
                        Triple(
                            Offset(size.width * star.x, size.height * star.y),
                            star.radius,
                            Color.White.copy(alpha = star.alpha)
                        )
                    }
                    val horizonGlowCenter = Offset(size.width * 0.5f, size.height * 0.94f)
                    val horizonGlowRadius = size.width * 0.9f
                    val horizonGlowBrush = Brush.radialGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.35f),
                            ElectricBlue.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        center = horizonGlowCenter,
                        radius = horizonGlowRadius
                    )
                    val horizonCenter = Offset(size.width * 0.5f, size.height * 1.42f)
                    val horizonRadius = size.width * 1.15f
                    val horizonFillBrush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0B2E7A), Color(0xFF030712))
                    )
                    val horizonStrokeBrush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            ElectricBlue,
                            ElectricCyan,
                            ElectricBlue,
                            Color.Transparent
                        )
                    )
                    val horizonStroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)

                    onDrawBehind {
                        drawCircle(
                            brush = topGlowBrush,
                            radius = topGlowRadius,
                            center = topGlowCenter
                        )
                        starPoints.forEach { (pt, r, color) ->
                            drawCircle(color = color, radius = r, center = pt)
                        }
                        if (showHorizonArc) {
                            drawCircle(
                                brush = horizonGlowBrush,
                                radius = horizonGlowRadius,
                                center = horizonGlowCenter
                            )
                            drawCircle(
                                brush = horizonFillBrush,
                                radius = horizonRadius,
                                center = horizonCenter
                            )
                            drawCircle(
                                brush = horizonStrokeBrush,
                                radius = horizonRadius,
                                center = horizonCenter,
                                style = horizonStroke
                            )
                        }
                    }
                }
        )
        content()
    }
}

private fun rememberStaticStars(): List<QuadStar> = listOf(
    QuadStar(0.12f, 0.09f, 2.0f, 0.55f),
    QuadStar(0.24f, 0.18f, 1.5f, 0.35f),
    QuadStar(0.78f, 0.11f, 2.2f, 0.6f),
    QuadStar(0.88f, 0.22f, 1.4f, 0.4f),
    QuadStar(0.16f, 0.34f, 1.6f, 0.35f),
    QuadStar(0.83f, 0.38f, 1.8f, 0.45f),
    QuadStar(0.08f, 0.54f, 1.5f, 0.3f),
    QuadStar(0.92f, 0.58f, 2.0f, 0.45f),
    QuadStar(0.28f, 0.68f, 1.4f, 0.3f),
    QuadStar(0.71f, 0.66f, 1.7f, 0.35f),
    QuadStar(0.45f, 0.08f, 1.3f, 0.35f),
    QuadStar(0.62f, 0.15f, 1.6f, 0.4f)
)

private data class QuadStar(val x: Float, val y: Float, val radius: Float, val alpha: Float)

@Composable
fun AnshAiLogo(
    size: Dp = 44.dp,
    glowing: Boolean = true,
    showBorder: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val glowRadius = this.size.minDimension * 0.85f
                val glowBrush = if (glowing) {
                    Brush.radialGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.40f),
                            ElectricBlue.copy(alpha = 0.48f),
                            NebulaPurple.copy(alpha = 0.24f),
                            Color.Transparent
                        )
                    )
                } else null
                onDrawBehind {
                    if (glowBrush != null) {
                        drawCircle(brush = glowBrush, radius = glowRadius)
                    }
                }
            }
            .clip(CircleShape)
            .then(
                if (showBorder) {
                    Modifier.border(
                        width = 1.6.dp,
                        brush = Brush.linearGradient(listOf(ElectricCyan, ElectricBlue, NebulaPurple)),
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ansh_ai_icon_1794901879984),
            contentDescription = "ANSH AI Logo",
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun ElectricSurfaceCard(
    modifier: Modifier = Modifier,
    highlightBorder: Boolean = false,
    containerColor: Color = Color(0xFF0E1428),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = if (highlightBorder) {
        Brush.linearGradient(
            listOf(
                ElectricCyan.copy(alpha = 0.85f),
                ElectricBlue,
                NebulaPurple.copy(alpha = 0.75f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFF21315C),
                Color(0xFF1A2242)
            )
        )
    }

    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        border = BorderStroke(if (highlightBorder) 1.25.dp else 1.dp, borderBrush),
        tonalElevation = 2.dp
    ) {
        Column(content = content)
    }
}

@Composable
fun GlowingPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = ElectricBlue,
            contentColor = Color.White,
            disabledContainerColor = MidnightSurfaceElevated,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
            .height(52.dp)
            .drawWithCache {
                val glowBrush = Brush.radialGradient(
                    colors = listOf(
                        ElectricBlue.copy(alpha = 0.55f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.55f),
                    radius = (size.width * 0.65f).coerceAtLeast(1f)
                )
                val corner = CornerRadius(24.dp.toPx())
                onDrawBehind {
                    if (enabled) {
                        drawRoundRect(brush = glowBrush, cornerRadius = corner)
                    }
                }
            },
        content = content
    )
}

@Composable
fun StatusPill(
    text: String,
    accentColor: Color = ElectricBlue,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accentColor.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (accentColor == ElectricBlue) ElectricBlueBright else accentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun AnshMarkdownMessage(
    markdown: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val segments = splitCodeSegments(markdown)
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        segments.forEach { segment ->
            if (segment.isCode) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MidnightSurfaceElevated,
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ElectricBlue.copy(alpha = 0.14f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = segment.language.ifBlank { "code" }.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(segment.content)) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy code block",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = segment.content,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            } else if (segment.content.isNotBlank()) {
                Text(
                    text = formatInlineMarkdown(segment.content.trim()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
            }
        }
    }
}

private data class MarkdownSegment(
    val content: String,
    val isCode: Boolean,
    val language: String = ""
)

private fun splitCodeSegments(raw: String): List<MarkdownSegment> {
    val result = mutableListOf<MarkdownSegment>()
    val regex = Regex("```([a-zA-Z0-9_+-]*)\\n([\\s\\S]*?)```")
    var lastIndex = 0
    for (match in regex.findAll(raw)) {
        if (match.range.first > lastIndex) {
            result.add(MarkdownSegment(raw.substring(lastIndex, match.range.first), false))
        }
        val lang = match.groupValues[1]
        val code = match.groupValues[2].trimEnd()
        result.add(MarkdownSegment(code, true, lang))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < raw.length) {
        result.add(MarkdownSegment(raw.substring(lastIndex), false))
    }
    if (result.isEmpty()) {
        result.add(MarkdownSegment(raw, false))
    }
    return result
}

private fun formatInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { lineIdx, rawLine ->
            val trimmed = rawLine.trimStart()
            val isHeading = trimmed.startsWith("### ") || trimmed.startsWith("## ") || trimmed.startsWith("# ")
            val cleanLine = when {
                trimmed.startsWith("### ") -> trimmed.removePrefix("### ")
                trimmed.startsWith("## ") -> trimmed.removePrefix("## ")
                trimmed.startsWith("# ") -> trimmed.removePrefix("# ")
                trimmed.startsWith("- ") -> "• " + trimmed.removePrefix("- ")
                trimmed.startsWith("* ") -> "• " + trimmed.removePrefix("* ")
                else -> rawLine
            }

            if (isHeading) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ElectricBlueBright)) {
                    append(cleanLine.replace("**", ""))
                }
            } else {
                val tokenRegex = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`)")
                var idx = 0
                for (m in tokenRegex.findAll(cleanLine)) {
                    if (m.range.first > idx) {
                        append(cleanLine.substring(idx, m.range.first))
                    }
                    val token = m.value
                    if (token.startsWith("**") && token.endsWith("**")) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(token.substring(2, token.length - 2))
                        }
                    } else if (token.startsWith("`") && token.endsWith("`")) {
                        withStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = ElectricCyan,
                                background = MidnightSurfaceElevated
                            )
                        ) {
                            append(" ${token.substring(1, token.length - 1)} ")
                        }
                    }
                    idx = m.range.last + 1
                }
                if (idx < cleanLine.length) {
                    append(cleanLine.substring(idx))
                }
            }
            if (lineIdx < lines.lastIndex) append("\n")
        }
    }
}

@Composable
fun AnshConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isDestructive) MaterialTheme.colorScheme.error else ElectricBlue
                ),
                modifier = Modifier.testTag("dialog_confirm_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_button")
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
