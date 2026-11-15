package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.attemptAutoSignIn
import com.example.core.authStateFlow
import com.example.core.configureAppCheck
import com.example.core.onGoogleSignInClicked
import com.example.core.signOut
import com.example.data.repository.FamilyRepository
import com.example.data.repository.GovernanceRepository
import com.example.data.repository.IdentityMemoryRepository
import com.example.data.repository.MessagingHubRepository
import com.example.domain.model.AppSurface
import com.example.domain.model.FamilyRole
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FamilyHubScreen
import com.example.ui.screens.FamilySetupScreen
import com.example.ui.screens.FatherScreen
import com.example.ui.screens.GovernanceAdminScreen
import com.example.ui.screens.KidsScreen
import com.example.ui.screens.WifeScreen
import com.example.ui.theme.BabaTheme
import com.example.ui.viewmodel.BabaWorkspaceData
import com.example.ui.viewmodel.BabaWorkspaceViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            BabaTheme {
                BabaRootApp()
            }
        }
    }
}

@Composable
private fun BabaRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            kotlinx.coroutines.delay(400L)
            if (Firebase.auth.currentUser == null) {
                attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = { authError = null },
                    onUnauthenticated = {},
                    scope = scope
                )
            }
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            isSigningIn = isSigningIn,
            errorMessage = authError,
            onGoogleSignIn = {
                isSigningIn = true
                authError = null
                onGoogleSignInClicked(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = { isSigningIn = false },
                    onAuthError = { err ->
                        isSigningIn = false
                        authError = err
                    },
                    onAuthCancelled = { isSigningIn = false },
                    scope = scope
                )
            }
        )
    } else {
        key(user.uid) {
            AuthenticatedBabaWorkspace(
                user = user,
                onSignOut = {
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedBabaWorkspace(
    user: FirebaseUser,
    onSignOut: () -> Unit
) {
    val appContext = LocalContext.current.applicationContext
    val viewModel: BabaWorkspaceViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                BabaWorkspaceViewModel(
                    currentUserId = user.uid,
                    currentUserName = user.displayName ?: user.email ?: "Family Member",
                    currentUserEmail = user.email.orEmpty(),
                    familyRepository = FamilyRepository(appContext),
                    identityMemoryRepository = IdentityMemoryRepository(appContext),
                    messagingHubRepository = MessagingHubRepository(appContext),
                    governanceRepository = GovernanceRepository(appContext)
                )
            }
        }
    )

    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val selectedSurfaceState by viewModel.selectedSurface.collectAsStateWithLifecycle()
    val supervisedChildId by viewModel.supervisedChildId.collectAsStateWithLifecycle()
    val aiBusy by viewModel.aiBusy.collectAsStateWithLifecycle()
    val aiError by viewModel.aiError.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val previewResponse by viewModel.previewResponse.collectAsStateWithLifecycle()

    when (val state = workspaceState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Loading BABA Family Workspace...")
                }
            }
        }

        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 460.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Unable to connect to BABA workspace",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onSignOut) {
                        Text("Sign Out")
                    }
                }
            }
        }

        is UiState.Success<BabaWorkspaceData> -> {
            val data = state.data
            if (data.activeFamily == null) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        TopAppBar(
                            title = { Text("BABA Family Setup") },
                            actions = {
                                IconButton(
                                    onClick = onSignOut,
                                    modifier = Modifier.testTag("sign_out_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        FamilySetupScreen(
                            displayName = data.userAccount?.displayName ?: user.displayName ?: "Family Member",
                            invitations = data.invitations,
                            statusBanner = statusBanner,
                            onDismissBanner = viewModel::clearBanner,
                            onCreateFamily = viewModel::createFamily,
                            onAcceptInvitation = viewModel::acceptInvitation
                        )
                    }
                }
            } else {
                val role = data.currentMember?.role ?: FamilyRole.FATHER.apiValue
                val allowedSurfaces = if (role == FamilyRole.CHILD.apiValue) {
                    listOf(AppSurface.KIDS, AppSurface.FAMILY_HUB)
                } else {
                    listOf(
                        AppSurface.FATHER,
                        AppSurface.WIFE,
                        AppSurface.KIDS,
                        AppSurface.FAMILY_HUB,
                        AppSurface.GOVERNANCE
                    )
                }
                val defaultSurface = when (role) {
                    FamilyRole.WIFE.apiValue -> AppSurface.WIFE
                    FamilyRole.CHILD.apiValue -> AppSurface.KIDS
                    else -> AppSurface.FATHER
                }
                val currentSurface = selectedSurfaceState?.takeIf { it in allowedSurfaces } ?: defaultSurface
                val quietHours = data.userAccount?.quietHoursEnabled ?: false
                val unreadNotifications = data.notifications.filter { user.uid !in it.readByUids }
                var showNotificationsDialog by remember { mutableStateOf(false) }

                if (showNotificationsDialog) {
                    AlertDialog(
                        onDismissRequest = { showNotificationsDialog = false },
                        title = {
                            Text(
                                if (quietHours) "Family Notifications (Quiet Hours On)" else "Family Notifications (${data.notifications.size})"
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (quietHours) {
                                    Text(
                                        text = "Quiet Hours active — non-urgent chimes are muted.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (data.notifications.isEmpty()) {
                                    Text("No notifications yet.", style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    data.notifications.take(10).forEach { notif ->
                                        val isUnread = user.uid !in notif.readByUids
                                        Surface(
                                            color = if (isUnread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(notif.title, style = MaterialTheme.typography.titleSmall)
                                                    Text(notif.body, style = MaterialTheme.typography.bodySmall)
                                                }
                                                if (isUnread) {
                                                    TextButton(onClick = {
                                                        data.activeFamily.let { fam ->
                                                            viewModel.markNotificationRead(fam.id, notif.id)
                                                        }
                                                    }) {
                                                        Text("Read")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showNotificationsDialog = false }) { Text("Close") }
                        }
                    )
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 600.dp
                    Scaffold(
                        contentWindowInsets = WindowInsets.safeDrawing,
                        topBar = {
                            TopAppBar(
                                title = {
                                    Column {
                                        Text(
                                            text = "${stringResource(R.string.app_name)} • ${surfaceLocalizedLabel(currentSurface)}",
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text(
                                            text = data.activeFamily.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                actions = {
                                    IconButton(
                                        onClick = { showNotificationsDialog = true },
                                        modifier = Modifier.testTag("notifications_inbox_button")
                                    ) {
                                        BadgedBox(
                                            badge = {
                                                if (unreadNotifications.isNotEmpty() && !quietHours) {
                                                    Badge { Text("${unreadNotifications.size}") }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (quietHours) Icons.Default.NotificationsPaused else Icons.Default.Notifications,
                                                contentDescription = "Notifications inbox"
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = onSignOut,
                                        modifier = Modifier.testTag("sign_out_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            if (!useRail) {
                                NavigationBar {
                                    allowedSurfaces.forEach { surface ->
                                        NavigationBarItem(
                                            selected = currentSurface == surface,
                                            onClick = { viewModel.selectSurface(surface) },
                                            icon = { SurfaceIcon(surface) },
                                            label = { Text(surfaceLocalizedLabel(surface), maxLines = 1) },
                                            modifier = Modifier.testTag("nav_${surface.routeKey}")
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
                            if (useRail) {
                                NavigationRail {
                                    allowedSurfaces.forEach { surface ->
                                        NavigationRailItem(
                                            selected = currentSurface == surface,
                                            onClick = { viewModel.selectSurface(surface) },
                                            icon = { SurfaceIcon(surface) },
                                            label = { Text(surfaceLocalizedLabel(surface), maxLines = 1) },
                                            modifier = Modifier.testTag("nav_${surface.routeKey}")
                                        )
                                    }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                when (currentSurface) {
                                    AppSurface.FATHER -> FatherScreen(
                                        data = data,
                                        viewModel = viewModel,
                                        aiBusy = aiBusy,
                                        previewResponse = previewResponse,
                                        statusBanner = statusBanner
                                    )
                                    AppSurface.WIFE -> WifeScreen(
                                        data = data,
                                        viewModel = viewModel,
                                        aiBusy = aiBusy,
                                        aiError = aiError,
                                        statusBanner = statusBanner
                                    )
                                    AppSurface.KIDS -> KidsScreen(
                                        data = data,
                                        viewModel = viewModel,
                                        supervisedChildId = supervisedChildId,
                                        aiBusy = aiBusy,
                                        aiError = aiError,
                                        statusBanner = statusBanner
                                    )
                                    AppSurface.FAMILY_HUB -> FamilyHubScreen(
                                        data = data,
                                        viewModel = viewModel,
                                        statusBanner = statusBanner
                                    )
                                    AppSurface.GOVERNANCE -> GovernanceAdminScreen(
                                        data = data,
                                        viewModel = viewModel,
                                        statusBanner = statusBanner
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

@Composable
private fun SurfaceIcon(surface: AppSurface) {
    val icon = when (surface) {
        AppSurface.FATHER -> Icons.Default.Psychology
        AppSurface.WIFE -> Icons.Default.Favorite
        AppSurface.KIDS -> Icons.Default.AutoStories
        AppSurface.FAMILY_HUB -> Icons.Default.FamilyRestroom
        AppSurface.GOVERNANCE -> Icons.Default.AdminPanelSettings
    }
    Icon(imageVector = icon, contentDescription = surface.title)
}

@Composable
private fun surfaceLocalizedLabel(surface: AppSurface): String = when (surface) {
    AppSurface.FATHER -> stringResource(R.string.nav_father)
    AppSurface.WIFE -> stringResource(R.string.nav_wife)
    AppSurface.KIDS -> stringResource(R.string.nav_kids)
    AppSurface.FAMILY_HUB -> stringResource(R.string.nav_hub)
    AppSurface.GOVERNANCE -> stringResource(R.string.nav_safety)
}
