package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertFalse

/** Pins that the classes holding passwords, PINs and tokens never print them: a logged object or an exception message must not leak one. */
class SecretRedactionTest {
    private val secrets = listOf("hunter2", "4321", "access-abc", "refresh-def", "cci-ghi", "person@example.com")

    private fun assertNoSecrets(text: String) {
        for (s in secrets) assertFalse(s in text, "\"$s\" leaked in: $text")
    }

    @Test
    fun credentials() = assertNoSecrets(Credentials("person@example.com", "hunter2", "4321").toString())

    @Test
    fun storedSession() = assertNoSecrets(
        SessionStore.Session(
            accessToken = "access-abc", refreshToken = "refresh-def", username = "person@example.com", pin = "4321",
            cci = CciTokens(cciAccessToken = "cci-ghi"),
        ).toString(),
    )

    @Test
    fun controlSession() = assertNoSecrets(
        ControlSession(token = "access-abc", refresh = "refresh-def", deviceId = "D", pin = "4321", cci = CciTokens(idToken = "cci-ghi")).toString(),
    )
}
