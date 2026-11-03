package com.bloo.bluelink.ui

import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.SmartClimateMode
import com.bloo.bluelink.data.SmartComfort
import com.bloo.bluelink.data.comfortLevelsOf
import java.util.concurrent.ConcurrentHashMap

/**
 * Notes the comfort smart climate chose for a car, so a manual start that follows it can teach the model what the person actually wanted.
 * A start that matches the choice teaches nothing: only a manual setting that differs from what smart climate picked counts, so the model
 * never just learns its own output.
 */
internal object SmartComfortLearning {
    private class Noted(val comfort: SmartComfort, val mode: SmartClimateMode, val atMs: Long)

    private val noted = ConcurrentHashMap<String, Noted>()

    /** How long after a smart start a manual setting still counts as a response to it. */
    private const val WINDOW_MS = 30 * 60 * 1000L

    /** Smart climate has just started [vin] with [comfort], on a day of kind [mode]. */
    fun noteSmart(vin: String, comfort: SmartComfort, mode: SmartClimateMode) {
        noted[vin] = Noted(comfort, mode, System.currentTimeMillis())
    }

    /**
     * A start for [vin] is being sent with [req]. Returns the kind of day to learn on when it differs from the smart choice made within the
     * window, or null when there is nothing to learn.
     */
    fun manualSend(vin: String, req: ClimateRequest): SmartClimateMode? {
        val n = noted.remove(vin) ?: return null
        if (System.currentTimeMillis() - n.atMs > WINDOW_MS || n.mode == SmartClimateMode.HOLD) return null
        val auto = ClimateRequest(
            tempF = req.tempF,
            defrost = n.comfort.defrost,
            durationMinutes = req.durationMinutes,
            steeringWheelHeat = n.comfort.wheel,
            seatFrontLeft = n.comfort.seatDriver,
            seatFrontRight = n.comfort.seatPassenger,
            seatRearLeft = n.comfort.seatRearLeft,
            seatRearRight = n.comfort.seatRearRight,
        )
        return if (comfortLevelsOf(auto, n.mode).contentEquals(comfortLevelsOf(req, n.mode))) null else n.mode
    }
}
