package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.BuildConfig
import com.bloo.bluelink.data.Brand
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * DEBUG-ONLY hooks for the real-account test on an emulator (.github/realtest.sh, driven through DebugFixturesReceiver). The captures
 * cannot read the screen (the moving background keeps uiautomator from ever seeing it idle), so the test signs in, sends one command
 * and runs the restart sign-in through these, and reads the outcome from the "BlooTest" log lines. Those lines are written to be
 * public: the CI log is readable by anyone, so they carry states and counts, never a car's name, VIN or place.
 */
internal object DebugProbe {
    fun mark(message: String) {
        if (BuildConfig.DEBUG) android.util.Log.i("BlooTest", message)
    }
}

internal sealed interface DebugAccountCommand {
    class Login(val user: String, val password: String, val pin: String, val brand: Brand) : DebugAccountCommand
    class Reauth(val user: String, val password: String, val pin: String, val brand: Brand) : DebugAccountCommand
    object AppLockOn : DebugAccountCommand
    object LockCar : DebugAccountCommand
    object FinishOnboarding : DebugAccountCommand
}

internal object DebugAccountTest {
    val commands = MutableSharedFlow<DebugAccountCommand>(extraBufferCapacity = 4)
}

/** Runs the test's commands against the app and logs each change of the app's state, with every car's name and VIN taken out. */
@Composable
internal fun DebugAccountTestHost(vm: AppViewModel) {
    if (!BuildConfig.DEBUG) return
    val context = LocalContext.current
    val state = vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        DebugAccountTest.commands.collect { c ->
            when (c) {
                is DebugAccountCommand.Login -> {
                    DebugProbe.mark("command: sign in")
                    vm.login(c.user, c.password, c.pin, c.brand)
                }
                is DebugAccountCommand.Reauth -> {
                    DebugProbe.mark("command: restart sign-in (bootReauth=${state.value.bootReauth})")
                    vm.reauthAfterRestart(c.user, c.password, c.pin, c.brand, context.findFragmentActivity())
                }
                DebugAccountCommand.AppLockOn -> {
                    DebugProbe.mark("command: app lock on (device lock usable=${vm.canUseBiometrics()})")
                    vm.setBiometricLock(true)
                }
                DebugAccountCommand.FinishOnboarding -> {
                    DebugProbe.mark("command: finish the first-run pages")
                    vm.finishOnboarding()
                }
                DebugAccountCommand.LockCar -> {
                    val car = state.value.vehicles.firstOrNull()
                    DebugProbe.mark("command: lock the first car (${if (car == null) "no car" else "car found"})")
                    car?.let { vm.lock(it) }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow {
            val s = state.value
            var message = s.message.orEmpty()
            for (v in s.vehicles) {
                if (v.name.isNotBlank()) message = message.replace(v.name, "<car>")
                message = message.replace(v.vin, "<vin>")
            }
            for (place in s.placeNames.values) if (place.toString().isNotBlank()) message = message.replace(place.toString(), "<place>")
            // An email address, and any run of digits long enough to be a PIN, a coordinate or a phone number.
            message = message.replace(Regex("[^\\s@]+@[^\\s@]+"), "<email>").replace(Regex("\\d{4,}"), "<n>")
            val pending = s.pending.map { it.substringAfterLast(':') }.sorted()
            val awaiting = s.awaitingCar.map { it.substringAfterLast(':') }.sorted()
            "state: screen=${s.screen::class.simpleName} locked=${s.locked} bootReauth=${s.bootReauth} cars=${s.vehicles.size} " +
                "pending=$pending awaitingCar=$awaiting otp=${s.kiaOtp != null || s.canadaOtp != null} message=\"$message\""
        }.distinctUntilChanged().collect { DebugProbe.mark(it.replace(Regex("[A-HJ-NPR-Z0-9]{17}"), "<vin>")) }
    }
}
