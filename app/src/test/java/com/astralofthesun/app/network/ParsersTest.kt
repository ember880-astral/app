package com.astralofthesun.app.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject

    /** The live /api/shop shape: shelves → groups → rows with buyPrice / gemPrice. */
    @Test fun parsesLiveShopShelves() {
        val items = Parsers.shop(json("""
            {"ok":true,"currency":"solars","shelves":[
              {"key":"weapons","label":"Weapons","groups":[
                {"label":"Swords","rows":[
                  {"id":"iron_sword","name":"Iron Sword","description":"Basic.","buyPrice":250,"sellPrice":100},
                  {"id":"star_blade","name":"Star Blade","gemPrice":5}
                ]}
              ]},
              {"key":"tools","label":"Tools","groups":[{"label":"Picks","rows":[{"id":"pick","name":"Pick","buyPrice":80}]}]}
            ]}
        """))
        assertEquals(3, items.size)
        assertEquals("iron_sword", items[0].id)
        assertEquals(250L, items[0].price)
        assertEquals("solars", items[0].currency)
        assertEquals("weapons", items[0].category)
        assertEquals("gems", items[1].currency)
        assertEquals(5L, items[1].price)
        assertEquals("tools", items[2].category)
    }

    @Test fun parsesFlatShopFallback() {
        val items = Parsers.shop(json("""{"items":[{"id":"a","name":"A","price":10,"category":"items"}]}"""))
        assertEquals(1, items.size)
        assertEquals(10L, items[0].price)
    }

    @Test fun limitsKeepRunsAndStaminaSeparate() {
        val l = Parsers.limits(json("""{"runsUsed":2,"staminaUsed":11,"premium":true,"resetAt":1790000000}"""))
        assertEquals(2, l.runsUsed)
        assertEquals(20, l.runsMax)
        assertEquals(11, l.staminaUsed)
        assertEquals(30, l.staminaMax)
        assertEquals(1790000000000L, l.resetAt)
        assertTrue(l.isPremium)
    }

    @Test fun limitsFreeDefaultIsSeven() {
        assertEquals(7, Parsers.limits(json("""{"runsUsed":0}""")).runsMax)
    }

    @Test fun parsesLocation() {
        val loc = Parsers.location(json("""
            {"id":"frost_cavern","name":"Frost Cavern","minLevel":10,"maxLevel":20,"unlocked":false,
             "prerequisite":{"id":"newcomers_hollow","name":"Newcomer's Hollow"},
             "floors":30,"bossFloors":[10,20,30],"checkpointInterval":5,"checkpoint":10}
        """))
        assertEquals("10–20", loc.levelRange)
        assertFalse(loc.unlocked)
        assertEquals("Newcomer's Hollow", loc.prerequisiteName)
        assertEquals(listOf(10, 20, 30), loc.bossFloors)
        assertEquals(5, loc.checkpointInterval)
        assertEquals(10, loc.currentFloor)
    }

    @Test fun deathResultIsHonest() {
        val r = Parsers.floorResult(json("""
            {"victory":false,"gearLost":[{"name":"Iron Sword"}],"bagLost":[{"name":"Potion","qty":3}]}
        """))
        assertFalse(r.victory)
        assertEquals("Iron Sword", r.gearLost.single().name)
        assertEquals(3, r.bagLost.single().qty)
        assertFalse(r.savedByPremiumRevive)
        assertNull(r.savedByItem)
    }

    @Test fun skillsPadToFourSlots() {
        val (equipped, pool) = Parsers.skills(json("""
            {"equipped":[{"id":"fire","name":"Fireball","mpCost":10},null],"pool":[{"id":"fire"},{"id":"ice"}]}
        """))
        assertEquals(4, equipped.size)
        assertEquals("fire", equipped[0]?.id)
        assertNull(equipped[1])
        assertEquals(2, pool.size)
    }

    @Test fun notFoundBecomesNotLive() {
        assertTrue(errorFromBody(404, """{"ok":false,"error":"Not found."}""").notLive)
        assertEquals("Sign in to continue.", errorFromBody(401, """{"ok":false,"error":"Sign in to continue."}""").message)
    }
}
