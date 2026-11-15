package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.domain.model.IdentityMode
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.BabaTheme
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
}
