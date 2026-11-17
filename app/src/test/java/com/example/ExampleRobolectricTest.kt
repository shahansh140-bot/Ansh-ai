package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.model.AccountStatus
import com.example.model.AppConfig
import com.example.model.AssistantMode
import com.example.model.ThemeMode
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.VideosScreen
import com.example.ui.screens.VoiceChatScreen
import com.example.ui.theme.AnshAiTheme
import com.example.viewmodel.VoiceChatUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app name matches ANSH AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ANSH AI", appName)
    }

    @Test
    fun `enum deserialization falls back safely on unknown values`() {
        assertEquals(AccountStatus.ACTIVE, AccountStatus.fromRaw("UNKNOWN"))
        assertEquals(UserRole.USER, UserRole.fromRaw("ADMIN_FAKE"))
        assertEquals(ThemeMode.ELECTRIC_DARK, ThemeMode.fromRaw(null))
        assertEquals(AssistantMode.CHAT, AssistantMode.fromRaw("INVALID"))
    }

    @Test
    fun `home screen renders greeting and quick actions`() {
        composeRule.setContent {
            AnshAiTheme {
                HomeScreen(
                    userProfile = UserProfile(displayName = "Ansh", role = UserRole.USER.name),
                    appConfig = AppConfig(),
                    recentChats = emptyList(),
                    onNewChat = { _, _ -> },
                    onOpenVoiceChat = {},
                    onOpenCreateImages = {},
                    onOpenVideos = {},
                    onOpenHistory = {},
                    onOpenProfile = {},
                    onOpenSettings = {},
                    onOpenAdmin = {},
                    onOpenExistingChat = {}
                )
            }
        }

        composeRule.onNodeWithText("Hello, I'm ANSH AI.").assertIsDisplayed()
        composeRule.onNodeWithTag("home_new_chat_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_voice_chat_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_ask_input").assertIsDisplayed()
        // Regular user must NOT see admin console button
        composeRule.onNodeWithTag("home_admin_button").assertDoesNotExist()
    }

    @Test
    fun `videos screen disables generation when disabled by admin`() {
        composeRule.setContent {
            AnshAiTheme {
                VideosScreen(appConfig = AppConfig(videoGenEnabled = false))
            }
        }

        composeRule.onNodeWithTag("videos_lazy_column").performScrollToNode(hasTestTag("video_generate_button"))
        composeRule.onNodeWithTag("video_generate_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("videos_lazy_column").performScrollToNode(hasTestTag("video_config_status_card"))
        composeRule.onNodeWithText("Video Generation Disabled by Admin").assertIsDisplayed()
        composeRule.onNodeWithTag("video_download_button").assertDoesNotExist()
    }

    @Test
    fun `videos screen shows missing backend notice and keeps MP4 download absent when unconfigured`() {
        composeRule.setContent {
            AnshAiTheme {
                VideosScreen(appConfig = AppConfig(videoGenEnabled = true, videoBackendUrl = ""))
            }
        }

        composeRule.onNodeWithTag("videos_lazy_column").performScrollToNode(hasTestTag("video_config_status_card"))
        composeRule.onNodeWithText("Server-Side Video Backend Not Configured").assertIsDisplayed()
        composeRule.onNodeWithTag("video_download_button").assertDoesNotExist()
    }

    @Test
    fun `videos screen renders typed quota error and never simulates video output`() {
        composeRule.setContent {
            AnshAiTheme {
                VideosScreen(
                    appConfig = AppConfig(videoGenEnabled = true, videoBackendUrl = "https://example.cloudfunctions.net/anshVideoApi"),
                    videoGenState = com.example.viewmodel.VideoGenUiState.Error(
                        category = com.example.data.VideoErrorCategory.QUOTA_EXCEEDED,
                        message = "Quota limit reached for video generation."
                    )
                )
            }
        }

        composeRule.onNodeWithTag("videos_lazy_column").performScrollToNode(hasTestTag("video_error_card"))
        composeRule.onNodeWithTag("video_error_card").assertIsDisplayed()
        composeRule.onNodeWithText("Quota / Rate Limit Exceeded").assertIsDisplayed()
        composeRule.onNodeWithTag("video_download_button").assertDoesNotExist()
        composeRule.onNodeWithTag("video_player_container").assertDoesNotExist()
    }

    @Test
    fun `videos screen renders 1s to 15s options, disables 1s and 2s, defaults to 10s, and passes exact duration`() {
        var submittedDuration = ""
        composeRule.setContent {
            AnshAiTheme {
                VideosScreen(
                    appConfig = AppConfig(
                        videoGenEnabled = true,
                        videoBackendUrl = "https://example.cloudfunctions.net/anshVideoApi"
                    ),
                    onGenerateVideo = { _, _, _, _, _, duration, _ ->
                        submittedDuration = duration
                    }
                )
            }
        }

        composeRule.onNodeWithTag("videos_lazy_column").performScrollToNode(hasTestTag("video_duration_selector_card"))
        composeRule.onNodeWithText("Selected: 10 seconds").assertIsDisplayed()
        composeRule.onNodeWithTag("video_duration_1s").assertIsNotEnabled()
        composeRule.onNodeWithTag("video_duration_2s").assertIsNotEnabled()

        for (sec in 3..15) {
            composeRule.onNodeWithTag("video_duration_${sec}s").assertIsEnabled()
        }

        // Select 7s and verify exact selected duration updates and forwards cleanly
        composeRule.onNodeWithTag("video_duration_7s").performClick()
        composeRule.onNodeWithText("Selected: 7 seconds").assertIsDisplayed()
        composeRule.onNodeWithTag("videos_lazy_column").performScrollToNode(hasTestTag("video_text_prompt_input"))
        composeRule.onNodeWithTag("video_text_prompt_input").performTextInput("Electric blue aurora over mountain lake")
        composeRule.onNodeWithTag("videos_lazy_column").performScrollToNode(hasTestTag("video_generate_button"))
        composeRule.onNodeWithTag("video_generate_button").performClick()
        assertEquals("7s", submittedDuration)
    }

    @Test
    fun `videos screen renders final duration, MP4 download, and Extend Video controls on real success`() {
        val tempMp4 = java.io.File.createTempFile("robolectric_vid", ".mp4").apply {
            writeBytes(byteArrayOf(0, 0, 0, 24))
            deleteOnExit()
        }
        var extendedDuration = ""
        var extendedParentId = ""
        composeRule.setContent {
            AnshAiTheme {
                VideosScreen(
                    appConfig = AppConfig(
                        videoGenEnabled = true,
                        videoBackendUrl = "https://example.cloudfunctions.net/anshVideoApi"
                    ),
                    videoGenState = com.example.viewmodel.VideoGenUiState.Success(
                        com.example.model.VideoCreationLog(
                            id = "log_1",
                            prompt = "Sunset waves",
                            style = "Cinematic",
                            aspectRatio = "16:9",
                            duration = "10s",
                            interactionId = "int_10s",
                            localFilePath = tempMp4.absolutePath,
                            status = "SUCCESS",
                            actualDurationMs = 10_000L,
                            segmentCount = 1L
                        )
                    ),
                    onExtendVideo = { _, src, _, extDur ->
                        extendedParentId = src.interactionId
                        extendedDuration = extDur
                    }
                )
            }
        }

        composeRule.onNodeWithTag("videos_lazy_column").performScrollToNode(hasTestTag("video_result_card"))
        composeRule.onNodeWithTag("video_result_card").assertIsDisplayed()
        composeRule.onNodeWithText("Final Duration: 10s").assertIsDisplayed()
        composeRule.onNodeWithTag("video_download_button").assertIsDisplayed()
        composeRule.onNodeWithTag("video_extend_section").assertIsDisplayed()
        composeRule.onNodeWithTag("video_extend_prompt_input")
            .performScrollTo()
            .performTextInput("Camera rises into starry sky")
        composeRule.onNodeWithTag("video_extend_dur_8s")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("video_extend_button")
            .performScrollTo()
            .performClick()
        assertEquals("int_10s", extendedParentId)
        assertEquals("8s", extendedDuration)
    }

    @Test
    fun `auth screen renders Google sign in without admin login button`() {
        composeRule.setContent {
            AnshAiTheme {
                AuthScreen(onAuthSuccess = {})
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("super_admin_sign_in_button").assertDoesNotExist()
    }

    @Test
    fun `voice chat screen renders mic and stop controls`() {
        composeRule.setContent {
            AnshAiTheme {
                VoiceChatScreen(
                    voiceState = VoiceChatUiState(speechAvailable = true),
                    speechRate = 1.0f,
                    onUpdateAvailability = {},
                    onListeningChange = { _, _ -> },
                    onRmsChange = {},
                    onSpeakingChange = {},
                    onToggleMute = {},
                    onVoiceError = {},
                    onSubmitQuery = { _, _ -> },
                    onReturnToChat = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("voice_mic_button").assertIsDisplayed()
        composeRule.onNodeWithTag("voice_stop_button").assertIsDisplayed()
        composeRule.onNodeWithText("Voice Chat").assertIsDisplayed()
    }
}
