package com.example.data

import android.graphics.Bitmap
import com.example.model.AssistantMode
import com.example.model.ChatMessage
import com.example.model.Citation
import com.example.model.GeminiModelOption
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

data class StreamChunkResult(
    val accumulatedText: String,
    val citations: List<Citation>
)

class GeminiAiRepository {

    private fun systemInstructionFor(mode: AssistantMode, preferredLanguage: String): Content {
        val base = "You are ANSH AI, an intelligent, futuristic, and helpful AI assistant. " +
            "Always respond accurately, clearly, and concisely in $preferredLanguage unless the user asks for another language."
        val modeAddon = when (mode) {
            AssistantMode.CHAT -> " Adapt smoothly across general Q&A, brainstorming, summaries, translation, science, and education."
            AssistantMode.SEARCH -> " Ground your answers in live web search results when available, summarize clearly, and never fabricate citations or URLs."
            AssistantMode.MATH -> " Show clear step-by-step mathematical derivations, formulas, intermediate calculations, and verify the final numeric or symbolic answer."
            AssistantMode.CODE -> " Provide clean, production-grade code inside fenced markdown blocks with language tags, brief architectural explanations, and edge-case notes."
            AssistantMode.VOICE -> " Keep responses natural, conversational, and easy to follow when spoken aloud, avoiding excessive markdown symbols."
        }
        return content { text(base + modeAddon) }
    }

    fun streamChatMessage(
        mode: AssistantMode,
        modelOption: GeminiModelOption,
        history: List<ChatMessage>,
        userMessage: String,
        attachedBitmap: Bitmap? = null,
        preferredLanguage: String = "English"
    ): Flow<StreamChunkResult> = flow {
        val tools = if (mode == AssistantMode.SEARCH) {
            listOf(Tool.googleSearch())
        } else {
            emptyList()
        }

        val model = Firebase.ai.generativeModel(
            modelName = modelOption.modelId,
            systemInstruction = systemInstructionFor(mode, preferredLanguage),
            tools = tools.ifEmpty { null }
        )

        val sdkHistory = history
            .filter { it.text.isNotBlank() }
            .takeLast(20)
            .map { msg ->
                content(role = if (msg.role == "user") "user" else "model") {
                    text(msg.text)
                }
            }

        val chat = model.startChat(history = sdkHistory)
        val promptContent = content("user") {
            if (attachedBitmap != null) {
                image(scaleBitmapForPayload(attachedBitmap))
            }
            text(userMessage)
        }

        val accumulated = StringBuilder()
        val citationsMap = linkedMapOf<String, Citation>()

        withTimeout(60_000L) {
            chat.sendMessageStream(promptContent).collect { chunk ->
                val delta = chunk.text.orEmpty()
                accumulated.append(delta)

                chunk.candidates.firstOrNull()?.groundingMetadata?.groundingChunks?.forEach { gChunk ->
                    val web = gChunk.web
                    val uri = web?.uri
                    if (!uri.isNullOrBlank()) {
                        val title = web.title?.takeIf { it.isNotBlank() } ?: uri
                        citationsMap[uri] = Citation(title = title, url = uri)
                    }
                }

                emit(
                    StreamChunkResult(
                        accumulatedText = accumulated.toString(),
                        citations = citationsMap.values.toList()
                    )
                )
            }
        }
    }

    suspend fun generateImage(
        prompt: String,
        aspectRatioLabel: String,
        referenceBitmap: Bitmap? = null
    ): Bitmap {
        val ratio = when (aspectRatioLabel) {
            "16:9" -> AspectRatio.LANDSCAPE_16x9
            "9:16" -> AspectRatio.PORTRAIT_9x16
            "4:3" -> AspectRatio.LANDSCAPE_4x3
            "3:4" -> AspectRatio.PORTRAIT_3x4
            else -> null
        }

        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            if (ratio != null) {
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(ratio)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
        }

        val model = Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = config
        )

        return withTimeout(60_000L) {
            val response = if (referenceBitmap != null) {
                model.generateContent(
                    content {
                        image(scaleBitmapForPayload(referenceBitmap))
                        text(prompt)
                    }
                )
            } else {
                model.generateContent(prompt)
            }

            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()

            imagePart?.image
                ?: throw IllegalStateException("The image model returned no image output.")
        }
    }

    private fun scaleBitmapForPayload(bitmap: Bitmap, maxDim: Int = 1024): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val newW = (width * ratio).toInt().coerceAtLeast(1)
        val newH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
