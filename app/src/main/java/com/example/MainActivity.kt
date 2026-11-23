package com.example

import android.content.ComponentCallbacks2
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.sjassistant.core.i18n.LocalizedStrings
import com.example.sjassistant.ui.MainViewModel
import com.example.sjassistant.ui.screens.ChatScreen
import com.example.sjassistant.ui.screens.SettingsAiScreen
import com.example.sjassistant.ui.screens.WorkspaceScreen
import com.example.ui.theme.SjAssistantTheme

object SjRoutes {
    const val ROUTE_CHAT = "chat"
    const val ROUTE_WORKSPACE = "workspace"
    const val ROUTE_SYSTEM = "system"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by mainViewModel.preferences.collectAsStateWithLifecycle()

            SjAssistantTheme(darkTheme = prefs.darkMode) {
                SjAssistantApp(viewModel = mainViewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Stop mic/TTS immediately when app leaves foreground so nothing runs in the background
        mainViewModel.onAppBackgrounded()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            System.gc()
        }
    }
}

@Composable
fun SjAssistantApp(viewModel: MainViewModel) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val isAnalyzingAttachment by viewModel.isAnalyzingAttachment.collectAsStateWithLifecycle()
    val activePhoto by viewModel.activePhoto.collectAsStateWithLifecycle()
    val activeFileName by viewModel.activeFileName.collectAsStateWithLifecycle()
    val memorySnapshot by viewModel.memorySnapshot.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val strings = LocalizedStrings.english
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: SjRoutes.ROUTE_CHAT
    val snackbarHostState = remember { SnackbarHostState() }

    val destinations = remember {
        listOf(
            NavDestination(
                route = SjRoutes.ROUTE_CHAT,
                label = strings.navChat,
                selectedIcon = Icons.AutoMirrored.Filled.Chat,
                unselectedIcon = Icons.AutoMirrored.Outlined.Chat,
                testTag = "nav_chat"
            ),
            NavDestination(
                route = SjRoutes.ROUTE_WORKSPACE,
                label = strings.navWorkspace,
                selectedIcon = Icons.Filled.Checklist,
                unselectedIcon = Icons.Outlined.Checklist,
                testTag = "nav_workspace"
            ),
            NavDestination(
                route = SjRoutes.ROUTE_SYSTEM,
                label = strings.navSystem,
                selectedIcon = Icons.Filled.Tune,
                unselectedIcon = Icons.Outlined.Tune,
                testTag = "nav_system"
            )
        )
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(navigationEvent) {
        val target = navigationEvent ?: return@LaunchedEffect
        if (target == "workspace") {
            navigateTo(SjRoutes.ROUTE_WORKSPACE)
        }
        viewModel.consumeNavigationEvent()
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusBanner()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.weight(1f).fillMaxSize()
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = SjRoutes.ROUTE_CHAT
                    ) {
                        composable(SjRoutes.ROUTE_CHAT) {
                            ChatScreen(
                                messages = messages,
                                isThinking = isThinking,
                                isAnalyzingAttachment = isAnalyzingAttachment,
                                activePhoto = activePhoto,
                                activeFileName = activeFileName,
                                voiceState = voiceState,
                                memorySnapshot = memorySnapshot,
                                onSendMessage = viewModel::sendUserMessage,
                                onAttachPhoto = viewModel::attachPhoto,
                                onClearPhoto = viewModel::clearAttachedPhoto,
                                onAttachFile = viewModel::attachFile,
                                onClearFile = viewModel::clearAttachedFile,
                                onStartVoice = viewModel::startVoiceCapture,
                                onStopVoice = { viewModel.voiceManager.stopListening() },
                                onToggleSpeak = viewModel::toggleSpeakMessage,
                                onClearChat = viewModel::clearChatHistory
                            )
                        }

                        composable(SjRoutes.ROUTE_WORKSPACE) {
                            WorkspaceScreen(
                                notes = notes,
                                todos = todos,
                                reminders = reminders,
                                encryptByDefault = prefs.encryptNotesByDefault,
                                onCreateNote = viewModel::createNote,
                                onTogglePinNote = viewModel::togglePinNote,
                                onDeleteNote = viewModel::deleteNote,
                                onCreateTodo = viewModel::createTodo,
                                onToggleTodo = viewModel::toggleTodo,
                                onDeleteTodo = viewModel::deleteTodo,
                                onCreateReminder = viewModel::createReminder,
                                onDeleteReminder = viewModel::deleteReminder
                            )
                        }

                        composable(SjRoutes.ROUTE_SYSTEM) {
                            SettingsAiScreen(
                                prefs = prefs,
                                memorySnapshot = memorySnapshot,
                                recommendedModels = viewModel.recommendedModels,
                                indexedDocuments = documents,
                                onLoadGgufUri = viewModel::inspectAndLoadGguf,
                                onUnloadGguf = viewModel::unloadGgufModel,
                                onDeleteDocument = viewModel::deleteDocument,
                                onSetDarkMode = viewModel::setDarkMode,
                                onSetAutoSpeak = viewModel::setAutoSpeak,
                                onSetContextCompression = viewModel::setContextCompression,
                                onSetEncryptNotes = viewModel::setEncryptNotesByDefault
                            )
                        }
                    }
                }
            }
        }
    }
}
