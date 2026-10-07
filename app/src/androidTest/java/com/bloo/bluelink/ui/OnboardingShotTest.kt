package com.bloo.bluelink.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Not a behaviour test: renders the onboarding deck and writes card screenshots to the app's own
 * external files dir, so the look can be reviewed or diffed without walking the whole flow. Run
 * alone and pull the PNGs from /sdcard/Android/data/com.bloo.bluelink/files/:
 *
 *   ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
 *   adb shell am instrument -w -e class com.bloo.bluelink.ui.OnboardingShotTest \
 *     com.bloo.bluelink.test/androidx.test.runner.AndroidJUnitRunner
 *
 * It uses a raw screen capture rather than Compose's captureToImage because the deck runs infinite
 * animations (the aurora, the hero) and Compose never reports idle.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingShotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val bmp: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun onCard(title: String) =
        rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()

    private fun settle() = Thread.sleep(1600)

    private fun nextTo(title: String) {
        rule.onNodeWithText("Next").performClick()
        rule.waitUntil(10_000) { onCard(title) }
        settle()
    }

    @Test
    fun darkShots() {
        val vm = AppViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.showWelcomeCards()
        rule.setContent { BlooTheme(themeMode = ThemeMode.DARK) { OnboardingScreen(vm, OnboardingMode.Replay) } }
        rule.waitUntil(10_000) { onCard("Welcome to Bloo") }
        settle()
        shot("onb_dark_welcome")

        nextTo("Quick setup")
        shot("onb_dark_setup")

        nextTo("Look and feel")
        shot("onb_dark_look")
    }
}
