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

    @Test fun emptyResponseDoesNotAuthenticate() {
        assertFalse(Repository.isAuthenticatedSession(json("{}")))
        assertFalse(Repository.isAuthenticatedSession(json("""{"success":true}""")))
    }

    @Test fun validSessionsAuthenticate() {
        assertTrue(Repository.isAuthenticatedSession(json("""{"data":{"authenticated":true}}""")))
        assertTrue(Repository.isAuthenticatedSession(json("""{"user":{"id":"123"}}""")))
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
