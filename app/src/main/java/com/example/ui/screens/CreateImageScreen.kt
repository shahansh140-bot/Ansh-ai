package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.model.ImageCreationLog
import com.example.model.UiState
import com.example.ui.components.ElectricSurfaceCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightSurfaceElevated
import com.example.ui.theme.NebulaPurple
import com.example.viewmodel.ImageGenUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

@Composable
fun CreateImageScreen(
    imageGenState: ImageGenUiState,
    imageLogsState: UiState<List<ImageCreationLog>>,
    onGenerateImage: (String, String, Bitmap?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var selectedAspect by remember { mutableStateOf("1:1") }
    var referenceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var saveFeedback by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                referenceBitmap = loadBitmapFromUri(context, uri)
            }
        }
    }

    val aspects = listOf("1:1", "16:9", "9:16", "4:3", "3:4")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Image Studio", style = MaterialTheme.typography.headlineSmall)
                    StatusPill(text = "NANO BANANA 2.1", accentColor = NebulaPurple)
                }
                Text(
                    text = "Generate or edit images with Gemini Image models (requires Firebase Blaze billing).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            ElectricSurfaceCard(
                highlightBorder = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Describe your image") },
                        placeholder = { Text("e.g., Futuristic electric-blue AI core in a glass observatory") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MidnightSurfaceElevated,
                            unfocusedContainerColor = MidnightSurfaceElevated,
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = MidnightBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("image_prompt_input")
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        aspects.forEach { ratio ->
                            FilterChip(
                                selected = selectedAspect == ratio,
                                onClick = { selectedAspect = ratio },
                                label = { Text(ratio) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue.copy(alpha = 0.25f),
                                    selectedLabelColor = ElectricBlueBright
                                ),
                                modifier = Modifier.testTag("image_aspect_${ratio.replace(":", "_")}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            border = BorderStroke(1.dp, MidnightBorder),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("image_reference_picker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddPhotoAlternate,
                                contentDescription = "Pick reference image",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (referenceBitmap == null) "Reference" else "Change Ref")
                        }

                        Button(
                            onClick = {
                                saveFeedback = null
                                onGenerateImage(prompt, selectedAspect, referenceBitmap)
                            },
                            enabled = imageGenState !is ImageGenUiState.Loading && prompt.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("image_generate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (imageGenState is ImageGenUiState.Loading) "Synthesizing..." else "Generate")
                        }
                    }

                    AnimatedVisibility(visible = referenceBitmap != null) {
                        referenceBitmap?.let { refBmp ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Image(
                                    bitmap = refBmp.asImageBitmap(),
                                    contentDescription = "Reference image",
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(MaterialTheme.shapes.small),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    text = "Image-to-Image edit mode active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ElectricCyan,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { referenceBitmap = null }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove reference")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Preview Area
        item {
            ElectricSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (imageGenState) {
                        is ImageGenUiState.Idle -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = ElectricBlueBright,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text("Preview will appear here", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Generated images are high-resolution 1K PNGs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is ImageGenUiState.Loading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(vertical = 36.dp)
                            ) {
                                CircularProgressIndicator(color = ElectricCyan)
                                Text(
                                    "Synthesizing with gemini-nano-banana-2.1 (30–60s)...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ElectricBlueBright
                                )
                            }
                        }
                        is ImageGenUiState.Error -> {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = imageGenState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                        is ImageGenUiState.Success -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Image(
                                    bitmap = imageGenState.bitmap.asImageBitmap(),
                                    contentDescription = "Generated image: ${imageGenState.prompt}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Fit
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val saved = saveBitmapToGallery(context, imageGenState.bitmap)
                                                saveFeedback = if (saved) {
                                                    "Saved to Pictures/ANSH AI."
                                                } else {
                                                    "Failed to save image to gallery."
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricBlue,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("image_download_button")
                                    ) {
                                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            shareImageFile(context, File(imageGenState.filePath))
                                        },
                                        border = BorderStroke(1.dp, MidnightBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("image_share_button")
                                    ) {
                                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share")
                                    }
                                }
                                saveFeedback?.let { msg ->
                                    Text(
                                        text = msg,
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

        // Generation History
        item {
            Text("Generation History", style = MaterialTheme.typography.titleMedium)
        }

        when (imageLogsState) {
            is UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(24.dp))
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Text(
                        text = imageLogsState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            is UiState.Success -> {
                if (imageLogsState.data.isEmpty()) {
                    item {
                        Text(
                            text = "No images generated yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(imageLogsState.data, key = { it.id }) { log ->
                        val thumb by produceState<Bitmap?>(initialValue = null, log.localFilePath) {
                            value = withContext(Dispatchers.IO) {
                                decodeThumbnail(log.localFilePath)
                            }
                        }
                        ElectricSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val currentThumb = thumb
                                if (currentThumb != null) {
                                    Image(
                                        bitmap = currentThumb.asImageBitmap(),
                                        contentDescription = log.prompt,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(MaterialTheme.shapes.small),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MidnightSurfaceElevated,
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Image, contentDescription = null, tint = ElectricBlueBright)
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = log.prompt,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        StatusPill(text = log.aspectRatio)
                                        StatusPill(
                                            text = log.status,
                                            accentColor = if (log.status == "SUCCESS") ElectricCyan else MaterialTheme.colorScheme.error
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

private fun decodeThumbnail(path: String, targetSize: Int = 168): Bitmap? {
    if (path.isBlank()) return null
    val file = File(path)
    if (!file.exists()) return null
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        val maxSide = max(opts.outWidth, opts.outHeight)
        var sample = 1
        while (maxSide / sample > targetSize) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, decodeOpts)
    } catch (_: Exception) {
        null
    }
}

private suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean =
    withContext(Dispatchers.IO) {
        try {
        val filename = "ANSH_AI_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ANSH AI")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ANSH AI").apply {
                mkdirs()
            }
            val file = File(dir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        }
        } catch (_: Exception) {
            false
        }
    }

private fun shareImageFile(context: Context, file: File) {
    if (!file.exists()) return
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share ANSH AI Creation"))
}
