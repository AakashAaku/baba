package com.example.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppLanguage
import com.example.core.AppLocaleHelper
import com.example.domain.model.IdentityMode
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.BabaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BabaUiCujTest {

    @get:Rule
    val composeRule = createComposeRule()

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
        var activeLocale by androidx.compose.runtime.mutableStateOf("ne")

        composeRule.setContent {
            val localizedContext = androidx.compose.runtime.remember(baseContext, activeLocale) {
                AppLocaleHelper.localizedContext(baseContext, activeLocale)
            }
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

        expectedByLocale.forEach { (code, expectedSignInText) ->
            composeRule.runOnIdle { activeLocale = code }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(expectedSignInText).assertIsDisplayed()
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
}
