package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.FirestoreRepository
import com.example.data.GeminiAiRepository
import com.example.data.attemptAutoSignInSuspend
import com.example.data.authStateFlow
import com.example.data.signOut
import com.example.model.AccountStatus
import com.example.model.AssistantMode
import com.example.model.ThemeMode
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.ui.components.AnshAiLogo
import com.example.ui.components.AnshConfirmDialog
import com.example.ui.components.AnshWordmark
import com.example.ui.components.StatusPill
import com.example.ui.screens.AccountSuspendedScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CreateImageScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SuperAdminScreen
import com.example.ui.screens.VideosScreen
import com.example.ui.screens.VoiceChatScreen
import com.example.ui.theme.AnshAiTheme
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.NebulaPurple
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.SuperAdminViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
    HISTORY("History", Icons.Rounded.History, Icons.Outlined.History, "nav_history"),
    CREATE("Create", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_create"),
    VIDEOS("Videos", Icons.Rounded.Videocam, Icons.Outlined.Videocam, "nav_videos"),
    PROFILE("Profile", Icons.Rounded.Person, Icons.Outlined.Person, "nav_profile")
}

private enum class ActiveWorkspace {
    TABS,
    CHAT,
    VOICE,
    SUPER_ADMIN
}

@Composable
fun AnshAiApp() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    var splashDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (auth.currentUser != null) {
            delay(450)
        } else {
            val credentialManager = CredentialManager.create(context)
            val startMs = System.currentTimeMillis()
            withTimeoutOrNull(1800L) {
                attemptAutoSignInSuspend(
                    context = context,
                    credentialManager = credentialManager
                )
            }
            val elapsed = System.currentTimeMillis() - startMs
            if (elapsed < 650L) {
                delay(650L - elapsed)
            }
        }
        splashDone = true
    }

    if (!splashDone) {
        AnshAiTheme {
            SplashScreen()
        }
        return
    }

    val user = currentUser
    if (user == null) {
        AnshAiTheme {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        key(user.uid) {
            AuthenticatedWorkspace(
                currentUserId = user.uid,
                userEmail = user.email.orEmpty()
            )
        }
    }
}

@Composable
private fun AuthenticatedWorkspace(
    currentUserId: String,
    userEmail: String
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val firestoreRepo = remember(appContext) { FirestoreRepository(appContext) }
    val geminiRepo = remember { GeminiAiRepository() }

    val mainViewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = MainViewModel.provideFactory(
            firestoreRepo = firestoreRepo,
            geminiRepo = geminiRepo,
            currentUserId = currentUserId,
            filesDir = appContext.filesDir
        )
    )

    val userProfileState by mainViewModel.userProfile.collectAsStateWithLifecycle()
    val appConfig by mainViewModel.appConfig.collectAsStateWithLifecycle()
    val chatsState by mainViewModel.chatsState.collectAsStateWithLifecycle()
    val activeChatState by mainViewModel.activeChatState.collectAsStateWithLifecycle()
    val activeMessages by mainViewModel.activeMessages.collectAsStateWithLifecycle()
    val imageGenState by mainViewModel.imageGenState.collectAsStateWithLifecycle()
    val imageLogsState by mainViewModel.imageLogsState.collectAsStateWithLifecycle()
    val videoGenState by mainViewModel.videoGenState.collectAsStateWithLifecycle()
    val videoLogsState by mainViewModel.videoLogsState.collectAsStateWithLifecycle()
    val voiceState by mainViewModel.voiceState.collectAsStateWithLifecycle()
    val statusBanner by mainViewModel.statusBanner.collectAsStateWithLifecycle()

    val userProfile = (userProfileState as? UiState.Success)?.data
    val themeMode = userProfile?.themeModeEnum() ?: ThemeMode.ELECTRIC_DARK

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var activeWorkspace by rememberSaveable { mutableStateOf(ActiveWorkspace.TABS) }
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var showDrawerLanguageDialog by remember { mutableStateOf(false) }
    var showDrawerAboutDialog by remember { mutableStateOf(false) }
    var showDrawerLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            mainViewModel.clearBanner()
        }
    }

    AnshAiTheme(themeMode = themeMode) {
        if (userProfile?.accountStatusEnum() == AccountStatus.DISABLED) {
            AccountSuspendedScreen(email = userEmail)
            return@AnshAiTheme
        }

        if (showDrawerLanguageDialog) {
            val languages = listOf("English", "Hindi", "Spanish", "French", "German", "Japanese")
            AlertDialog(
                onDismissRequest = { showDrawerLanguageDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Select AI Language") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        languages.forEach { lang ->
                            val selected = userProfile?.preferredLanguage == lang
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) ElectricBlue.copy(alpha = 0.2f) else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) ElectricBlue else MidnightBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        mainViewModel.updatePreferences(null, lang, null, null, null, null)
                                        showDrawerLanguageDialog = false
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = lang,
                                        color = if (selected) ElectricCyan else Color.White
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDrawerLanguageDialog = false }) {
                        Text("Close", color = ElectricBlueBright)
                    }
                }
            )
        }

        if (showDrawerAboutDialog) {
            AlertDialog(
                onDismissRequest = { showDrawerAboutDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("About ANSH AI") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "ANSH AI — Intelligence Without Limits.",
                            style = MaterialTheme.typography.titleSmall,
                            color = ElectricCyan
                        )
                        Text(
                            text = "Powered by Google Gemini AI, live Voice Speech-to-Speech, Nano Banana Image Studio, private Cloud Firestore sync, and Firebase App Check.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDrawerAboutDialog = false }) {
                        Text("Close", color = ElectricBlueBright)
                    }
                }
            )
        }

        if (showDrawerLogoutDialog) {
            AnshConfirmDialog(
                title = "Log Out",
                message = "Sign out of ANSH AI on this device?",
                confirmLabel = "Logout",
                isDestructive = true,
                onConfirm = {
                    showDrawerLogoutDialog = false
                    signOut(credentialManager, scope)
                },
                onDismiss = { showDrawerLogoutDialog = false }
            )
        }

        when (activeWorkspace) {
            ActiveWorkspace.VOICE -> {
                VoiceChatScreen(
                    voiceState = voiceState,
                    speechRate = (userProfile?.voiceSpeechRate ?: 1.0).toFloat(),
                    onUpdateAvailability = mainViewModel::updateVoiceAvailability,
                    onListeningChange = mainViewModel::setVoiceListening,
                    onRmsChange = mainViewModel::updateVoiceRms,
                    onSpeakingChange = mainViewModel::setVoiceSpeaking,
                    onToggleMute = mainViewModel::toggleVoiceMute,
                    onVoiceError = mainViewModel::setVoiceError,
                    onSubmitQuery = mainViewModel::submitVoiceQuery,
                    onReturnToChat = { activeWorkspace = ActiveWorkspace.CHAT }
                )
            }

            ActiveWorkspace.CHAT -> {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    ChatScreen(
                        chatState = activeChatState,
                        messages = activeMessages,
                        onBack = { activeWorkspace = ActiveWorkspace.TABS },
                        onNewChat = { mainViewModel.startNewChat(activeChatState.mode) },
                        onClearChat = mainViewModel::clearCurrentChat,
                        onModeSelected = mainViewModel::setAssistantMode,
                        onModelSelected = mainViewModel::setModelOption,
                        onSendMessage = mainViewModel::sendChatMessage,
                        onStopGeneration = mainViewModel::stopGeneration,
                        onRegenerate = mainViewModel::regenerateLastResponse,
                        onOpenVoiceChat = { activeWorkspace = ActiveWorkspace.VOICE },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }

            ActiveWorkspace.SUPER_ADMIN -> {
                if (userProfile?.roleEnum() != UserRole.SUPER_ADMIN) {
                    activeWorkspace = ActiveWorkspace.TABS
                } else {
                    val adminVm: SuperAdminViewModel = viewModel(
                        key = "admin_$currentUserId",
                        factory = SuperAdminViewModel.provideFactory(firestoreRepo)
                    )
                    val usersState by adminVm.usersState.collectAsStateWithLifecycle()
                    val telemetryState by adminVm.telemetryState.collectAsStateWithLifecycle()
                    val errorsState by adminVm.errorsState.collectAsStateWithLifecycle()
                    val adminConfig by adminVm.appConfig.collectAsStateWithLifecycle()
                    val adminToast by adminVm.adminToast.collectAsStateWithLifecycle()

                    Scaffold(
                        contentWindowInsets = WindowInsets.safeDrawing,
                        containerColor = MaterialTheme.colorScheme.background
                    ) { innerPadding ->
                        SuperAdminScreen(
                            usersState = usersState,
                            telemetryState = telemetryState,
                            errorsState = errorsState,
                            appConfig = adminConfig,
                            adminToast = adminToast,
                            onClearToast = adminVm::clearToast,
                            onToggleUserStatus = adminVm::toggleUserStatus,
                            onSaveGlobalConfig = adminVm::updateGlobalConfig,
                            onDismissError = adminVm::dismissError,
                            onBack = { activeWorkspace = ActiveWorkspace.TABS },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }

            ActiveWorkspace.TABS -> {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = true,
                    drawerContent = {
                        AnshDrawerContent(
                            userProfile = userProfile,
                            userEmail = userEmail,
                            themeMode = themeMode,
                            onNewChat = {
                                scope.launch { drawerState.close() }
                                mainViewModel.startNewChat(AssistantMode.CHAT)
                                activeWorkspace = ActiveWorkspace.CHAT
                            },
                            onChatHistory = {
                                scope.launch { drawerState.close() }
                                selectedTab = MainTab.HISTORY
                            },
                            onVideoHistory = {
                                scope.launch { drawerState.close() }
                                selectedTab = MainTab.VIDEOS
                            },
                            onSettings = {
                                scope.launch { drawerState.close() }
                                selectedTab = MainTab.PROFILE
                            },
                            onLanguage = {
                                scope.launch { drawerState.close() }
                                showDrawerLanguageDialog = true
                            },
                            onToggleOledDark = { useOled ->
                                val nextTheme = if (useOled) ThemeMode.OLED_BLACK else ThemeMode.ELECTRIC_DARK
                                mainViewModel.updatePreferences(null, null, nextTheme, null, null, null)
                            },
                            onAbout = {
                                scope.launch { drawerState.close() }
                                showDrawerAboutDialog = true
                            },
                            onOpenAdmin = {
                                scope.launch { drawerState.close() }
                                if (userProfile?.roleEnum() == UserRole.SUPER_ADMIN) {
                                    activeWorkspace = ActiveWorkspace.SUPER_ADMIN
                                }
                            },
                            onLogout = {
                                scope.launch { drawerState.close() }
                                showDrawerLogoutDialog = true
                            }
                        )
                    }
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        val useRail = maxWidth >= 600.dp
                        val recentChats = (chatsState as? UiState.Success)?.data ?: emptyList()

                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            contentWindowInsets = WindowInsets.safeDrawing,
                            containerColor = MaterialTheme.colorScheme.background,
                            bottomBar = {
                                if (!useRail) {
                                    AnshBottomNavigationBar(
                                        selectedTab = selectedTab,
                                        onSelectTab = { selectedTab = it }
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                if (useRail) {
                                    NavigationRail(
                                        containerColor = MidnightSurface,
                                        modifier = Modifier.fillMaxHeight()
                                    ) {
                                        MainTab.entries.forEach { tab ->
                                            val selected = selectedTab == tab
                                            NavigationRailItem(
                                                selected = selected,
                                                onClick = { selectedTab = tab },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = tab.label
                                                    )
                                                },
                                                label = { Text(tab.label) },
                                                colors = NavigationRailItemDefaults.colors(
                                                    selectedIconColor = Color.White,
                                                    selectedTextColor = ElectricBlueBright,
                                                    indicatorColor = ElectricBlue.copy(alpha = 0.28f)
                                                ),
                                                modifier = Modifier.testTag(tab.testTag)
                                            )
                                        }
                                    }
                                }

                                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                    when (selectedTab) {
                                        MainTab.HOME -> {
                                            HomeScreen(
                                                userProfile = userProfile,
                                                appConfig = appConfig,
                                                recentChats = recentChats,
                                                onNewChat = { mode, initialPrompt ->
                                                    mainViewModel.startNewChat(mode)
                                                    activeWorkspace = ActiveWorkspace.CHAT
                                                    if (!initialPrompt.isNullOrBlank()) {
                                                        mainViewModel.sendChatMessage(initialPrompt, null)
                                                    }
                                                },
                                                onOpenVoiceChat = { activeWorkspace = ActiveWorkspace.VOICE },
                                                onOpenCreateImages = { selectedTab = MainTab.CREATE },
                                                onOpenVideos = { selectedTab = MainTab.VIDEOS },
                                                onOpenHistory = { selectedTab = MainTab.HISTORY },
                                                onOpenProfile = { selectedTab = MainTab.PROFILE },
                                                onOpenSettings = { selectedTab = MainTab.PROFILE },
                                                onOpenAdmin = {
                                                    if (userProfile?.roleEnum() == UserRole.SUPER_ADMIN) {
                                                        activeWorkspace = ActiveWorkspace.SUPER_ADMIN
                                                    }
                                                },
                                                onOpenExistingChat = { thread ->
                                                    mainViewModel.openExistingChat(thread)
                                                    activeWorkspace = ActiveWorkspace.CHAT
                                                },
                                                onOpenDrawer = {
                                                    scope.launch { drawerState.open() }
                                                }
                                            )
                                        }

                                        MainTab.HISTORY -> {
                                            HistoryScreen(
                                                chatsState = chatsState,
                                                onOpenChat = { thread ->
                                                    mainViewModel.openExistingChat(thread)
                                                    activeWorkspace = ActiveWorkspace.CHAT
                                                },
                                                onDeleteChat = mainViewModel::deleteChat,
                                                onClearAll = mainViewModel::clearAllHistory
                                            )
                                        }

                                        MainTab.CREATE -> {
                                            CreateImageScreen(
                                                imageGenState = imageGenState,
                                                imageLogsState = imageLogsState,
                                                onGenerateImage = mainViewModel::generateOrEditImage
                                            )
                                        }

                                        MainTab.VIDEOS -> {
                                            VideosScreen(
                                                appConfig = appConfig,
                                                videoGenState = videoGenState,
                                                videoLogsState = videoLogsState,
                                                onGenerateVideo = mainViewModel::generateVideo,
                                                onExtendVideo = mainViewModel::extendVideo,
                                                onCancelGeneration = mainViewModel::cancelVideoGeneration,
                                                onResetResult = mainViewModel::resetVideoResult,
                                                onSelectHistoryVideo = mainViewModel::selectHistoryVideo,
                                                onDeleteVideoLog = mainViewModel::deleteVideoLog
                                            )
                                        }

                                        MainTab.PROFILE -> {
                                            ProfileSettingsScreen(
                                                userProfile = userProfile,
                                                appConfig = appConfig,
                                                onUpdatePreferences = mainViewModel::updatePreferences,
                                                onClearAllHistory = mainViewModel::clearAllHistory,
                                                onOpenSuperAdmin = {
                                                    if (userProfile?.roleEnum() == UserRole.SUPER_ADMIN) {
                                                        activeWorkspace = ActiveWorkspace.SUPER_ADMIN
                                                    }
                                                }
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
}

@Composable
private fun AnshBottomNavigationBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit
) {
    Surface(
        color = Color(0xFF0A0F20),
        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MainTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                if (tab == MainTab.CREATE) {
                    // Glowing Center '+' Orb matching reference bottom navigation bar
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .drawWithCache {
                                val radius = size.minDimension * 0.72f
                                val alpha = if (selected) 0.78f else 0.45f
                                val brush = Brush.radialGradient(
                                    colors = listOf(
                                        ElectricBlue.copy(alpha = alpha),
                                        Color.Transparent
                                    )
                                )
                                onDrawBehind {
                                    drawCircle(brush = brush, radius = radius)
                                }
                            }
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ElectricCyan, ElectricBlue)
                                )
                            )
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = Color.White.copy(alpha = 0.7f),
                                shape = CircleShape
                            )
                            .clickable { onSelectTab(tab) }
                            .testTag(tab.testTag),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Create Images",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .height(52.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectTab(tab) }
                            .testTag(tab.testTag)
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            tint = if (selected) ElectricBlueBright else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(23.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) ElectricBlueBright else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnshDrawerContent(
    userProfile: UserProfile?,
    userEmail: String,
    themeMode: ThemeMode,
    onNewChat: () -> Unit,
    onChatHistory: () -> Unit,
    onVideoHistory: () -> Unit,
    onSettings: () -> Unit,
    onLanguage: () -> Unit,
    onToggleOledDark: (Boolean) -> Unit,
    onAbout: () -> Unit,
    onOpenAdmin: () -> Unit,
    onLogout: () -> Unit
) {
    val isSuperAdmin = userProfile?.roleEnum() == UserRole.SUPER_ADMIN
    val displayName = userProfile?.displayName?.ifBlank { "ANSH User" } ?: "ANSH User"
    val email = userProfile?.email?.ifBlank { userEmail } ?: userEmail

    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF0A1024),
        drawerContentColor = Color.White,
        modifier = Modifier.width(304.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Centered Brand Header matching Bottom-Right Mockup
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnshAiLogo(size = 56.dp, glowing = true)
                AnshWordmark(fontSize = 22.sp, italicAi = true)
                Text(
                    text = "Intelligence Without Limits",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // User Profile Row matching Bottom-Right Mockup
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!userProfile?.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = userProfile?.photoUrl,
                        contentDescription = "User avatar",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, ElectricCyan, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    AnshAiLogo(size = 54.dp, glowing = false)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusPill(
                        text = if (isSuperAdmin) "Super Admin" else "Active User",
                        accentColor = if (isSuperAdmin) NebulaPurple else ElectricBlue
                    )
                }
            }

            HorizontalDivider(color = MidnightBorder)

            DrawerMenuItem(
                icon = Icons.Rounded.AddComment,
                label = "New Chat",
                tag = "drawer_new_chat",
                onClick = onNewChat
            )
            DrawerMenuItem(
                icon = Icons.Rounded.History,
                label = "Chat History",
                tag = "drawer_chat_history",
                onClick = onChatHistory
            )
            DrawerMenuItem(
                icon = Icons.Rounded.Videocam,
                label = "Video History",
                tag = "drawer_video_history",
                onClick = onVideoHistory
            )
            DrawerMenuItem(
                icon = Icons.Outlined.Settings,
                label = "Settings",
                tag = "drawer_settings",
                onClick = onSettings
            )
            DrawerMenuItem(
                icon = Icons.Outlined.Language,
                label = "Language",
                trailingText = userProfile?.preferredLanguage ?: "English",
                tag = "drawer_language",
                onClick = onLanguage
            )

            // OLED / Dark Mode Toggle matching Mockup
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DarkMode,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("OLED Pitch Dark", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = themeMode == ThemeMode.OLED_BLACK,
                    onCheckedChange = onToggleOledDark,
                    colors = SwitchDefaults.colors(checkedTrackColor = ElectricBlue)
                )
            }

            DrawerMenuItem(
                icon = Icons.Outlined.Info,
                label = "About ANSH AI",
                tag = "drawer_about",
                onClick = onAbout
            )

            if (isSuperAdmin) {
                DrawerMenuItem(
                    icon = Icons.Rounded.AdminPanelSettings,
                    label = "Super Admin Console",
                    tint = NebulaPurple,
                    tag = "drawer_super_admin",
                    onClick = onOpenAdmin
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            HorizontalDivider(color = MidnightBorder)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onLogout)
                    .padding(horizontal = 8.dp)
                    .testTag("drawer_logout"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = "Logout",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Logout",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    tag: String,
    trailingText: String? = null,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = tint
            )
        }
        if (trailingText != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
