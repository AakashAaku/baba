package com.example.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.BabaWorkspaceScaffold
import com.example.LocalizedBabaEnvironment
import com.example.ai.BabaAiProvider
import com.example.ai.BabaOrchestrator
import com.example.ai.RawModelStructuredOutput
import com.example.core.AppLanguage
import com.example.core.AppLocaleHelper
import com.example.data.repository.FamilyRepository
import com.example.data.repository.GovernanceRepository
import com.example.data.repository.IdentityMemoryRepository
import com.example.data.repository.MessagingHubRepository
import com.example.domain.model.AgeBand
import com.example.domain.model.AppSurface
import com.example.domain.model.FamilyMember
import com.example.domain.model.FamilyNotification
import com.example.domain.model.FamilyRole
import com.example.domain.model.FamilyWorkspace
import com.example.domain.model.IdentityMode
import com.example.domain.model.SafetyStatus
import com.example.domain.model.SourceType
import com.example.domain.model.UserAccount
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.BabaTheme
import com.example.ui.viewmodel.BabaWorkspaceData
import com.example.ui.viewmodel.BabaWorkspaceViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BabaUiCujTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    @Before
    fun ensureFirebaseInitialized() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.baba.kmzqvx")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key")
                    .build()
            )
        } else {
            FirebaseApp.getInstance()
        }
        val dbRes = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
        val dbId = if (dbRes != 0) context.getString(dbRes)
        else "ai-studio-android-baba-268a44d1-1220-4692-86b3-6065f8127c41"
        firestore = FirebaseFirestore.getInstance(app, dbId)
        auth = FirebaseAuth.getInstance(app)
    }

    private fun createViewModel(aiProvider: BabaAiProvider): BabaWorkspaceViewModel {
        return BabaWorkspaceViewModel(
            currentUserId = "father-1",
            currentUserName = "Tariq",
            currentUserEmail = "tariq@example.com",
            familyRepository = FamilyRepository(firestore, auth),
            identityMemoryRepository = IdentityMemoryRepository(firestore, auth),
            messagingHubRepository = MessagingHubRepository(firestore, auth),
            governanceRepository = GovernanceRepository(firestore, auth),
            orchestrator = BabaOrchestrator(aiProvider)
        )
    }

    private fun sampleWorkspaceData(): BabaWorkspaceData {
        val family = FamilyWorkspace(
            id = "fam-1",
            name = "Baba Family",
            createdBy = "father-1",
            adultUids = listOf("father-1", "wife-1"),
            memberUids = listOf("father-1", "wife-1", "child-1"),
            planId = "family_plus"
        )
        val father = FamilyMember(
            id = "father-1",
            familyId = "fam-1",
            uid = "father-1",
            displayName = "Tariq",
            role = FamilyRole.FATHER.apiValue,
            ageBand = AgeBand.ADULT.apiValue,
            consentState = "granted",
            membershipStatus = "active"
        )
        val wife = FamilyMember(
            id = "wife-1",
            familyId = "fam-1",
            uid = "wife-1",
            displayName = "Nadia",
            role = FamilyRole.WIFE.apiValue,
            ageBand = AgeBand.ADULT.apiValue,
            consentState = "granted",
            membershipStatus = "active"
        )
        val child = FamilyMember(
            id = "child-1",
            familyId = "fam-1",
            uid = "child-1",
            displayName = "Zayd",
            role = FamilyRole.CHILD.apiValue,
            ageBand = AgeBand.AGE_9_12.apiValue,
            consentState = "granted",
            membershipStatus = "active"
        )
        return BabaWorkspaceData(
            userAccount = UserAccount(uid = "father-1", displayName = "Tariq", locale = "en"),
            families = listOf(family),
            activeFamily = family,
            members = listOf(father, wife, child),
            currentMember = father,
            notifications = listOf(
                FamilyNotification(
                    id = "notif-1",
                    familyId = "fam-1",
                    title = "Welcome to BABA",
                    body = "Your family workspace is ready.",
                    type = "system",
                    recipientUids = listOf("father-1")
                )
            )
        )
    }

    @Test
    fun authScreenDisplaysThreeIdentityDisclosuresAndGoogleSignIn() {
        composeRule.setContent {
            BabaTheme {
                AuthScreen(
                    isSigningIn = false,
                    errorMessage = null,
                    onGoogleSignIn = {}
                )
            }
        }

        composeRule.onNodeWithText("BABA").assertIsDisplayed()
        composeRule.onNodeWithText(IdentityMode.DIGITAL_BABA.label).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(IdentityMode.BABA_COMPANION.label).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(IdentityMode.REAL_BABA.label).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
    }

    @Test
    fun authScreenAllowsPreLoginLanguageSelectionAcrossAllSixLanguages() {
        val clickedLocales = mutableListOf<String>()
        composeRule.setContent {
            BabaTheme {
                AuthScreen(
                    isSigningIn = false,
                    errorMessage = null,
                    selectedLocale = "en",
                    onSelectLocale = { clickedLocales += it },
                    onGoogleSignIn = {}
                )
            }
        }

        listOf("en", "ne", "hi", "bn", "ur", "ar").forEach { code ->
            composeRule.onNodeWithTag("locale_${code}_chip")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
        }
        assertEquals(listOf("en", "ne", "hi", "bn", "ur", "ar"), clickedLocales)
    }

    @Test
    fun authScreenRendersLocalizedStringsForNepaliHindiBengaliAndUrdu() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val expectedByLocale = listOf(
            "ne" to "Google बाट लग-इन गर्नुहोस्",
            "hi" to "Google से साइन इन करें",
            "bn" to "Google দিয়ে সাইন ইন করুন",
            "ur" to "Google کے ذریعے سائن ان کریں"
        )
        var activeLocale by mutableStateOf("ne")

        composeRule.setContent {
            val localizedContext = AppLocaleHelper.localizedContext(baseContext, activeLocale)
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
                LocalLayoutDirection provides if (AppLanguage.fromCode(activeLocale).isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                BabaTheme {
                    AuthScreen(
                        isSigningIn = false,
                        errorMessage = null,
                        selectedLocale = activeLocale,
                        onSelectLocale = { activeLocale = it },
                        onGoogleSignIn = {}
                    )
                }
            }
        }

        expectedByLocale.forEach { (_, expectedSignInText) ->
            // Switch through locales to verify all translations load cleanly
            composeRule.onNodeWithText(expectedSignInText).assertIsDisplayed()
            val next = expectedByLocale.indexOfFirst { it.second == expectedSignInText } + 1
            if (next < expectedByLocale.size) {
                composeRule.runOnIdle { activeLocale = expectedByLocale[next].first }
                composeRule.waitForIdle()
            }
        }
    }

    @Test
    fun authScreenDisplaysActionableSignInDiagnosticsAndKeepsButtonClickable() {
        var clicked = false
        composeRule.setContent {
            BabaTheme {
                AuthScreen(
                    isSigningIn = false,
                    errorMessage = "Google Sign-In failed.\nDebug SHA-1: cf:92:13:35",
                    onGoogleSignIn = { clicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("auth_error_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed().performClick()
        assertEquals(true, clicked)
    }

    @Test
    fun workspaceNavigationBetweenHubAndAllTopMenusWorksUnderLocalizedContext() {
        val vm = createViewModel(object : BabaAiProvider {
            override suspend fun generateStructuredResponse(
                systemInstruction: String,
                userPrompt: String,
                requestedMode: IdentityMode
            ) = RawModelStructuredOutput(
                responseText = "Stay kind and honest.",
                identityMode = requestedMode,
                sourceType = SourceType.GENERAL_AI,
                sourceReferences = emptyList(),
                requiresHumanInput = false,
                escalationType = null,
                safetyStatus = SafetyStatus.APPROVED
            )
        })
        val data = sampleWorkspaceData()

        composeRule.setContent {
            BabaTheme {
                LocalizedBabaEnvironment(baseContext = composeRule.activity, localeCode = "ur") {
                    val selectedSurface by vm.selectedSurface.collectAsStateWithLifecycle()
                    BabaWorkspaceScaffold(
                        data = data,
                        viewModel = vm,
                        currentUserUid = "father-1",
                        selectedSurfaceState = selectedSurface,
                        supervisedChildId = null,
                        aiBusy = false,
                        aiError = null,
                        statusBanner = null,
                        previewResponse = null,
                        onShowLanguageDialog = {},
                        onSignOut = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // Navigate through all Father top tabs including Memories (PickVisualMedia launcher) and Real Baba (Audio Permission launcher)
        listOf(1, 2, 3, 4, 0).forEach { tabIdx ->
            composeRule.onNodeWithTag("father_tab_$tabIdx").performClick()
            composeRule.waitForIdle()
        }

        // Navigate to Hub (calls rememberLauncherForActivityResult) - previously crashed on device
        composeRule.onNodeWithTag("nav_${AppSurface.FAMILY_HUB.routeKey}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_distance_bridge_card").assertIsDisplayed()

        // Navigate to Wife and open Planning tab (calls rememberLauncherForActivityResult twice)
        composeRule.onNodeWithTag("nav_${AppSurface.WIFE.routeKey}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wife_tab_2").performClick()
        composeRule.waitForIdle()

        // Navigate to Kids and switch tabs
        composeRule.onNodeWithTag("nav_${AppSurface.KIDS.routeKey}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("kids_tab_1").performClick()
        composeRule.waitForIdle()

        // Navigate to Safety
        composeRule.onNodeWithTag("nav_${AppSurface.GOVERNANCE.routeKey}").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun topBarLanguageAndNotificationsButtonsOpenAndCloseDialogsCleanly() {
        val vm = createViewModel(object : BabaAiProvider {
            override suspend fun generateStructuredResponse(
                systemInstruction: String,
                userPrompt: String,
                requestedMode: IdentityMode
            ) = error("unused")
        })
        val data = sampleWorkspaceData()
        var showLanguageDialog by mutableStateOf(false)

        composeRule.setContent {
            BabaTheme {
                LocalizedBabaEnvironment(baseContext = composeRule.activity, localeCode = "ne") {
                    if (showLanguageDialog) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showLanguageDialog = false },
                            title = { androidx.compose.material3.Text("Language") },
                            text = { androidx.compose.material3.Text("Pick") },
                            confirmButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = { showLanguageDialog = false },
                                    modifier = androidx.compose.ui.Modifier.testTag("language_dialog_close")
                                ) { androidx.compose.material3.Text("Close") }
                            }
                        )
                    }
                    BabaWorkspaceScaffold(
                        data = data,
                        viewModel = vm,
                        currentUserUid = "father-1",
                        selectedSurfaceState = AppSurface.FATHER,
                        supervisedChildId = null,
                        aiBusy = false,
                        aiError = null,
                        statusBanner = null,
                        previewResponse = null,
                        onShowLanguageDialog = { showLanguageDialog = true },
                        onSignOut = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("notifications_inbox_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("notifications_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("notifications_dialog_close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("topbar_language_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("language_dialog_close").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun digitalBabaPreviewSurfacesBothAppCheckErrorsAndSuccessResponses() {
        var shouldFail = true
        val vm = createViewModel(object : BabaAiProvider {
            override suspend fun generateStructuredResponse(
                systemInstruction: String,
                userPrompt: String,
                requestedMode: IdentityMode
            ): RawModelStructuredOutput {
                if (shouldFail) {
                    throw IllegalStateException("HTTP 403 App Check token rejected")
                }
                return RawModelStructuredOutput(
                    responseText = "Always tell the truth kindly and learn from every step.",
                    identityMode = requestedMode,
                    sourceType = SourceType.APPROVED_GUIDANCE,
                    sourceReferences = emptyList(),
                    requiresHumanInput = false,
                    escalationType = null,
                    safetyStatus = SafetyStatus.APPROVED
                )
            }
        })
        val data = sampleWorkspaceData()

        composeRule.setContent {
            BabaTheme {
                LocalizedBabaEnvironment(baseContext = composeRule.activity, localeCode = "en") {
                    val aiBusy by vm.aiBusy.collectAsStateWithLifecycle()
                    val aiError by vm.aiError.collectAsStateWithLifecycle()
                    val previewResponse by vm.previewResponse.collectAsStateWithLifecycle()
                    val statusBanner by vm.statusBanner.collectAsStateWithLifecycle()
                    BabaWorkspaceScaffold(
                        data = data,
                        viewModel = vm,
                        currentUserUid = "father-1",
                        selectedSurfaceState = AppSurface.FATHER,
                        supervisedChildId = null,
                        aiBusy = aiBusy,
                        aiError = aiError,
                        statusBanner = statusBanner,
                        previewResponse = previewResponse,
                        onShowLanguageDialog = {},
                        onSignOut = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preview_identity_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            vm.aiError.value != null
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preview_error_banner").performScrollTo().assertIsDisplayed()

        shouldFail = false
        composeRule.onNodeWithTag("preview_identity_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            vm.previewResponse.value != null
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preview_response_card").performScrollTo().assertIsDisplayed()
    }
}
