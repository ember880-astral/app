package com.astralofthesun.app.network

import com.astralofthesun.app.data.BattleSkill
import com.astralofthesun.app.data.BossTelegraph
import com.astralofthesun.app.data.Combatant
import com.astralofthesun.app.data.DungeonLimits
import com.astralofthesun.app.data.FloorResult
import com.astralofthesun.app.data.InvItem
import com.astralofthesun.app.data.LocationEntry
import com.astralofthesun.app.data.LootItem
import com.astralofthesun.app.data.ShopItem
import com.astralofthesun.app.data.Skill
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/* ============================================================
   Pure JSON → model mappers for the dungeon flow + shop.
   No network, no state — unit-tested in ParsersTest.

   /api/shop is LIVE and returns shelves[].groups[].rows[] with
   buyPrice/sellPrice (not a flat items[] with price), so the
   shop parser reads that shape first and falls back to flat.
   ============================================================ */
object Parsers {

    private val isoPatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
    )

    /** Epoch millis from a number (seconds or millis) or an ISO-8601 string. */
    fun time(o: JsonObject, vararg keys: String): Long? {
        for (k in keys) {
            val p = o[k] as? JsonPrimitive ?: continue
            val n = o.num(k)
            if (n != null) return if (n in 1..99_999_999_999L) n * 1000 else n
            val s = p.contentOrNull ?: continue
            for (pattern in isoPatterns) {
                val f = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                runCatching { f.parse(s) }.getOrNull()?.let { return it.time }
            }
        }
        return null
    }

    // ── Shop (LIVE) ─────────────────────────────────────────
    fun shopItem(o: JsonObject, category: String, defaultCurrency: String): ShopItem {
        val gemPrice = o.num("gemPrice")?.takeIf { it > 0 }
        val buy = o.num("buyPrice", "price")
        val currency = o.str("currency") ?: if (buy == null && gemPrice != null) "gems" else defaultCurrency
        return ShopItem(
            id = o.str("id", "itemId").orEmpty(),
            name = o.str("name").orEmpty(),
            desc = o.str("description", "desc").orEmpty(),
            price = if (currency == "gems") gemPrice ?: buy else buy ?: gemPrice,
            image = o.str("image", "iconUrl").orEmpty(),
            category = category,
            currency = currency,
        )
    }

    fun shop(res: JsonObject): List<ShopItem> {
        val p = res.payload()
        val cur = p.str("currency") ?: "solars"
        val shelves = p.objs("shelves")
        if (shelves.isEmpty()) {
            return p.objs("items", "shop", "results").map { shopItem(it, it.str("category") ?: "items", cur) }
        }
        return shelves.flatMap { shelf ->
            val key = shelf.str("key", "id") ?: "items"
            val groups = shelf.objs("groups")
            if (groups.isEmpty()) shelf.objs("rows", "items").map { shopItem(it, key, cur) }
            else groups.flatMap { g -> g.objs("rows", "items").map { shopItem(it, key, cur) } }
        }
    }

    // ── Skills ──────────────────────────────────────────────
    fun skill(o: JsonObject): Skill = Skill(
        id = o.str("id", "skillId").orEmpty(),
        name = o.str("name").orEmpty(),
        mpCost = o.numInt("mpCost", "mp", "cost"),
        effect = o.str("effect", "description", "desc").orEmpty(),
        unlocked = o.bool("unlocked") ?: true,
    )

    /** { equipped:[skill|null ×4], pool:[…] } → (4 slots, pool) */
    fun skills(res: JsonObject): Pair<List<Skill?>, List<Skill>> {
        val p = res.payload()
        val equipped = p.arr("equipped", "loadout", "slots").map { (it as? JsonObject)?.let(::skill) }
        val pool = p.objs("pool", "skills", "unlocked").map(::skill)
        return (equipped + List((4 - equipped.size).coerceAtLeast(0)) { null }).take(4) to pool
    }

    fun battleSkill(o: JsonObject) = BattleSkill(
        id = o.str("id", "skillId").orEmpty(),
        name = o.str("name").orEmpty(),
        mpCost = o.numInt("mpCost", "mp", "cost"),
        effect = o.str("effect", "description").orEmpty(),
    )

    /** Usable items for the fight's Item menu (consumables/potions from the bag). */
    fun usableItems(res: JsonObject): List<InvItem> {
        val p = res.payload()
        return p.objs("bag", "items", "inventory").filter { o ->
            val type = o.str("type", "category").orEmpty().lowercase()
            o.bool("usable", "usableInBattle") ?: (type in setOf("consumable", "potion", "food"))
        }.map { o ->
            val qty = o.numInt("qty", "quantity")
            InvItem(
                id = o.str("id", "itemId").orEmpty(),
                name = o.str("name").orEmpty() + (qty?.takeIf { it > 1 }?.let { " ×$it" } ?: ""),
                image = o.str("image", "iconUrl").orEmpty(),
            )
        }
    }

    // ── World map ───────────────────────────────────────────
    fun location(o: JsonObject): LocationEntry {
        val min = o.numInt("minLevel", "levelMin")
        val max = o.numInt("maxLevel", "levelMax")
        val range = o.str("levelRange") ?: when {
            min != null && max != null -> "$min–$max"
            min != null -> "$min+"
            else -> ""
        }
        val prereq = o.obj("prerequisite", "requires")
        return LocationEntry(
            id = o.str("id", "key").orEmpty(),
            name = o.str("name").orEmpty(),
            art = o.str("art", "image", "background").orEmpty(),
            isTown = o.bool("isTown", "town") ?: (o.str("type") == "town"),
            isNewcomer = o.bool("isNewcomer", "newcomer") ?: (o.str("id", "key")?.contains("newcomer") == true),
            levelRange = range,
            unlocked = o.bool("unlocked") ?: false,
            prerequisiteId = prereq?.str("id", "dungeonId") ?: o.str("prerequisiteId"),
            prerequisiteName = prereq?.str("name") ?: o.str("prerequisiteName"),
            prerequisiteLevel = prereq?.numInt("level") ?: o.numInt("prerequisiteLevel", "requiredLevel"),
            currentFloor = o.numInt("checkpoint", "currentFloor", "checkpointFloor"),
            totalFloors = o.numInt("floors", "totalFloors", "floorCount"),
            bossFloors = o.ints("bossFloors"),
            checkpointInterval = o.numInt("checkpointInterval", "checkpointEvery"),
            description = o.str("description").orEmpty(),
        )
    }

    /** Runs (per entry) and Stamina (per fight) are separate pools — never merged. */
    fun limits(o: JsonObject?, base: DungeonLimits = DungeonLimits()): DungeonLimits {
        if (o == null) return base
        val premium = o.bool("premium", "isPremium") ?: base.isPremium
        return base.copy(
            runsUsed = o.numInt("runsUsed") ?: base.runsUsed,
            runsMax = o.numInt("runsMax") ?: if (premium) 20 else 7,
            newcomerRunsUsed = o.numInt("newcomerRunsUsed", "newcomerFloorsUsed") ?: base.newcomerRunsUsed,
            newcomerRunsMax = o.numInt("newcomerRunsMax", "newcomerFloorsMax") ?: base.newcomerRunsMax,
            staminaUsed = o.numInt("staminaUsed") ?: base.staminaUsed,
            staminaMax = o.numInt("staminaMax") ?: 30,
            resetAt = time(o, "resetAt", "resetsAt") ?: base.resetAt,
            isPremium = premium,
        )
    }

    // ── Fight ───────────────────────────────────────────────
    fun combatant(o: JsonObject?): Combatant = if (o == null) Combatant() else Combatant(
        id = o.str("id", "uid").orEmpty(),
        name = o.str("name").orEmpty(),
        image = o.str("image", "avatarUrl", "art").orEmpty(),
        level = o.numInt("level"),
        hp = o.numInt("hp"),
        maxHp = o.numInt("maxHp"),
        mp = o.numInt("mp"),
        maxMp = o.numInt("maxMp"),
        alive = o.bool("alive") ?: ((o.numInt("hp") ?: 1) > 0),
    )

    fun telegraph(o: JsonObject?): BossTelegraph? = o?.let {
        BossTelegraph(
            label = it.str("label", "name").orEmpty(),
            detail = it.str("detail", "description").orEmpty(),
            inTurns = it.numInt("inTurns", "turns"),
        )
    }

    private fun loot(list: List<JsonObject>) = list.map {
        LootItem(name = it.str("name").orEmpty(), image = it.str("image", "iconUrl").orEmpty(), qty = it.numInt("qty", "quantity") ?: 1)
    }

    fun floorResult(o: JsonObject): FloorResult = FloorResult(
        victory = o.bool("victory", "won") ?: (o.str("outcome") == "victory"),
        xpGained = o.numInt("xpGained", "xp"),
        solarsGained = o.num("solarsGained", "solars"),
        leveledUp = o.bool("leveledUp") ?: false,
        newLevel = o.numInt("newLevel"),
        newSkillUnlocked = o.obj("newSkill", "skillUnlocked")?.let(::skill),
        loot = loot(o.objs("loot")),
        floor = o.numInt("floor"),
        isLastFloor = o.bool("isLastFloor", "cleared") ?: false,
        checkpointSaved = o.numInt("checkpointSaved", "checkpoint"),
        gearLost = loot(o.objs("gearLost", "equippedLost")),
        bagLost = loot(o.objs("bagLost", "itemsLost")),
        savedByPremiumRevive = o.bool("savedByPremiumRevive", "premiumRevive") ?: false,
        savedByItem = o.str("savedByItem", "protectedBy"),
        nextFloor = o.numInt("nextFloor"),
    )
}
