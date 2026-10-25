package com.bloo.bluelink.wear

import androidx.compose.ui.graphics.Color
import com.bloo.bluelink.data.BlooColors
import kotlin.test.Test
import kotlin.test.assertEquals

class WearFaceTintTest {
    private val fallback = Color.White

    @Test fun noLevelUsesTheFallback() = assertEquals(fallback, faceTint(null, charging = false, fallback))

    @Test fun chargingIsBlueAtAnyLevel() = assertEquals(Color(BlooColors.chargeBlue), faceTint(5, charging = true, fallback))

    @Test fun lowIsRed() = assertEquals(Color(BlooColors.heat), faceTint(BlooColors.LOW_LEVEL - 1, charging = false, fallback))

    @Test fun gettingThereIsAmber() = assertEquals(Color(BlooColors.warn), faceTint(BlooColors.WARN_LEVEL - 1, charging = false, fallback))

    @Test fun plentyIsGreen() = assertEquals(Color(BlooColors.chargeGreen), faceTint(BlooColors.WARN_LEVEL, charging = false, fallback))
}
