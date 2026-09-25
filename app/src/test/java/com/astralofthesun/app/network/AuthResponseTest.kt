package com.astralofthesun.app.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class AuthResponseTest {
    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject

    @Test fun readsWrappedLookupProfile() {
        val player = Repository.lookupProfile(json("""{"data":{"user":{"displayName":"Astral","avatarUrl":"/pfp.png"}}}"""))
        assertEquals("Astral", player.name)
        assertEquals("/pfp.png", player.avatar)
    }

    @Test fun readsFlatLookupProfileAndMissingAvatar() {
        val player = Repository.lookupProfile(json("""{"username":"sun"}"""))
        assertEquals("sun", player.name)
        assertEquals("", player.avatar)
    }

    @Test fun readsLiveLookupShapeWithMaskedPhone() {
        // Live /api/auth/lookup answer: flat body with the signed handle,
        // the character name and a masked phone for the confirm screen.
        val player = Repository.lookupProfile(json(
            """{"found":true,"handle":"abc.def.ghi","name":"ShadowFang","maskedPhone":"+234•••678","avatarUrl":"https://i.ibb.co/x/pfp.jpg"}"""))
        assertEquals("ShadowFang", player.name)
        assertEquals("+234•••678", player.sub)
        assertEquals("https://i.ibb.co/x/pfp.jpg", player.avatar)
    }

    @Test fun emptyResponseDoesNotAuthenticate() {
        assertFalse(Repository.isAuthenticatedSession(json("{}")))
        assertFalse(Repository.isAuthenticatedSession(json("""{"success":true}""")))
    }

    @Test fun validSessionsAuthenticate() {
        assertTrue(Repository.isAuthenticatedSession(json("""{"data":{"authenticated":true}}""")))
        assertTrue(Repository.isAuthenticatedSession(json("""{"user":{"id":"123"}}""")))
    }

    @Test fun liveSessionShapeFromApiAuthSession() {
        // Exact shapes observed from the live bot's GET /api/auth/session.
        assertTrue(Repository.isAuthenticatedSession(
            json("""{"ok":true,"signedIn":true,"player":{"uid":"ca3a90719754f3ec","name":"Absolute Jester"}}""")))
        assertFalse(Repository.isAuthenticatedSession(
            json("""{"ok":true,"signedIn":false,"player":null}""")))
    }

    @Test fun explicitFailureNeverAuthenticatesEvenWithUser() {
        listOf(
            """{"loggedIn":false,"user":{"id":"123"}}""",
            """{"success":false,"data":{"authenticated":true}}""",
            """{"error":"Invalid code","user":{"id":"123"}}""",
            """{"data":{"valid":false,"user":{"id":"123"}}}""",
        ).forEach { response ->
            assertFalse(runCatching { Repository.isAuthenticatedSession(json(response)) }.getOrDefault(false))
        }
    }
}
