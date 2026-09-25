package com.astralofthesun.app.network

import androidx.compose.runtime.mutableStateOf
import com.astralofthesun.app.data.Astral
import com.astralofthesun.app.data.Notification
import com.astralofthesun.app.data.Player
import com.astralofthesun.app.data.Season
import com.astralofthesun.app.data.SeasonCharacter
import com.astralofthesun.app.data.SeasonReward
import com.astralofthesun.app.data.SeasonTier
import com.astralofthesun.app.data.ShopItem
import com.astralofthesun.app.data.Stats
import com.astralofthesun.app.data.Wallet
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/* ============================================================
   Astral of the Sun — repository
   Bridges AstralApi (network/AstralApi.kt) to the observable
   state in Astral (data/AstralData.kt). Every public function
   here is safe to call from a Composable's coroutine scope or
   a ViewModel; failures are caught and surfaced via
   [AuthState.lastError] / return values rather than thrown,
   so a flaky endpoint never crashes a screen.
   ============================================================ */

/** Small login/session state the UI (auth screens, ProfileScreen) can observe. */
object AuthState {
    val isLoggedIn = mutableStateOf(false)
    val isLoading = mutableStateOf(false)
    val lastError = mutableStateOf<String?>(null)
    val pendingIdentifier = mutableStateOf<String?>(null) // set after lookup/request-otp, used by verify-otp
}

// ── tiny defensive JSON helpers (backend field names aren't guaranteed) ──
private fun JsonObject.str(vararg keys: String): String? {
    for (k in keys) {
        val v = this[k] ?: continue
        (v as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.let { return it }
    }
    return null
}
private fun JsonObject.num(vararg keys: String): Long? {
    for (k in keys) {
        val v = this[k] ?: continue
        (v as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull?.let { return it }
    }
    return null
}
private fun JsonObject.numInt(vararg keys: String): Int? = num(*keys)?.toInt()
private fun JsonObject.obj(vararg keys: String): JsonObject? {
    for (k in keys) (this[k] as? JsonObject)?.let { return it }
    return null
}
private fun JsonObject.arr(vararg keys: String): List<JsonElement> {
    for (k in keys) (this[k] as? kotlinx.serialization.json.JsonArray)?.let { return it }
    return emptyList()
}
/** Some endpoints wrap the payload in {data: {...}} or {result: {...}}; unwrap if present. */
private fun JsonObject.payload(): JsonObject = obj("data", "result") ?: this

object Repository {

    private val api get() = ApiClient.api

    // ── Bootstrap ────────────────────────────────────────────
    /** Wired to Astral.loadData — call once the app has a session (or to check for one). */
    fun install() {
        Astral.loadData = { refreshAll() }
        AuthState.isLoggedIn.value = false
    }

    /** Checks for an existing session cookie and, if valid, loads everything. */
    suspend fun bootstrap() {
        val ok = runCatching { api.session() }
            .map { res ->
                val p = res.payload()
                val explicitFlag = p.booleanFlag("loggedIn") ?: p.booleanFlag("valid") ?: p.booleanFlag("authenticated")
                explicitFlag ?: (p.obj("user") != null || p.str("uid", "id") != null)
            }
            .getOrDefault(false)
        AuthState.isLoggedIn.value = ok
        if (ok) refreshAll()
    }

    private fun JsonObject.booleanFlag(key: String): Boolean? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull

    // ── Auth flow: lookup -> request-otp -> verify-otp, or register ──
    suspend fun lookup(identifier: String): Result<JsonObject> = runCatching {
        AuthState.pendingIdentifier.value = identifier
        api.authLookup(buildJsonObject { put("identifier", identifier) })
    }

    suspend fun requestOtp(identifier: String): Result<JsonObject> = runCatching {
        AuthState.pendingIdentifier.value = identifier
        api.requestOtp(buildJsonObject { put("identifier", identifier) })
    }

    suspend fun verifyOtp(otp: String, identifier: String? = null): Result<JsonObject> = runCatching {
        val id = identifier ?: AuthState.pendingIdentifier.value ?: error("No pending identifier — call requestOtp first")
        val res = api.verifyOtp(buildJsonObject { put("identifier", id); put("otp", otp) })
        AuthState.isLoggedIn.value = true
        refreshAll()
        res
    }

    suspend fun register(identifier: String, username: String, otp: String? = null): Result<JsonObject> = runCatching {
        val res = api.register(buildJsonObject {
            put("identifier", identifier)
            put("username", username)
            if (otp != null) put("otp", otp)
        })
        AuthState.isLoggedIn.value = true
        refreshAll()
        res
    }

    suspend fun logout() {
        runCatching { api.logout() }
        ApiClient.cookieJar.clear()
        AuthState.isLoggedIn.value = false
    }

    // ── Full refresh: populates every Astral state the backend covers ──
    suspend fun refreshAll() = coroutineScope {
        AuthState.isLoading.value = true
        AuthState.lastError.value = null
        listOf(
            launch { runCatching { loadMe() } },
            launch { runCatching { loadStats() } },
            launch { runCatching { loadNotifications() } },
            launch { runCatching { loadShop() } },
            launch { runCatching { loadSeason() } },
            launch { runCatching { loadCharacters() } },
            launch { runCatching { loadPremium() } },
            launch { runCatching { loadTopUp() } },
        ).forEach { it.join() }
        AuthState.isLoading.value = false
    }

    // ── Profile / wallet ─────────────────────────────────────
    suspend fun loadMe() {
        val me = api.me().payload()
        Astral.player.value = Player(
            name = me.str("username", "name", "displayName").orEmpty(),
            sub = me.str("title", "tag", "sub").orEmpty(),
            avatar = me.str("avatar", "pfp", "avatarUrl").orEmpty(),
        )
        me.str("banner")?.let { Astral.banner.value = it }
        val level = me.numInt("level")
        val solars = me.num("solars", "coins")
        val gems = me.num("gems")
        if (level != null || solars != null || gems != null) {
            Astral.stats.value = Astral.stats.value.copy(
                level = level ?: Astral.stats.value.level,
                solars = solars ?: Astral.stats.value.solars,
                gems = gems ?: Astral.stats.value.gems,
            )
        }
        if (solars != null || gems != null) {
            Astral.wallet.value = Wallet(solars = solars, gems = gems)
        }
    }

    suspend fun loadStats() {
        val s = api.stats().payload()
        Astral.stats.value = Stats(
            level = s.numInt("level") ?: Astral.stats.value.level,
            solars = s.num("solars", "coins") ?: Astral.stats.value.solars,
            gems = s.num("gems") ?: Astral.stats.value.gems,
        )
    }

    suspend fun updateSettings(fields: Map<String, String>): Result<JsonObject> = runCatching {
        api.updateSettings(buildJsonObject { fields.forEach { (k, v) -> put(k, v) } })
    }

    // ── Notifications ────────────────────────────────────────
    suspend fun loadNotifications() {
        val res = api.notifications().payload()
        val list = res.arr("notifications", "items", "results")
        Astral.notifications.clear()
        list.forEach { el ->
            val o = el.jsonObject
            Astral.notifications.add(
                Notification(
                    title = o.str("title", "message").orEmpty(),
                    sub = o.str("sub", "detail", "body").orEmpty(),
                )
            )
        }
    }

    suspend fun markAllNotificationsRead() = runCatching {
        api.markAllNotificationsRead()
        loadNotifications()
    }

    suspend fun markNotificationRead(id: String) = runCatching {
        api.markNotificationRead(id)
        loadNotifications()
    }

    suspend fun deleteNotification(id: String) = runCatching {
        api.deleteNotification(id)
        loadNotifications()
    }

    // ── Leaderboard / players ────────────────────────────────
    suspend fun leaderboard(): Result<List<JsonObject>> = runCatching {
        api.leaderboard().payload().arr("leaderboard", "entries", "results").map { it.jsonObject }
    }

    suspend fun player(uid: String): Result<JsonObject> = runCatching { api.player(uid).payload() }

    // ── Characters / spins ───────────────────────────────────
    suspend fun loadCharacters() {
        val res = api.characters().payload()
        val list = res.arr("characters", "items", "results")
        Astral.roster.clear()
        list.forEach { el ->
            val o = el.jsonObject
            Astral.roster.add(
                SeasonCharacter(
                    name = o.str("name").orEmpty(),
                    meta = o.str("meta", "type").orEmpty(),
                    cp = o.str("cp", "power").orEmpty(),
                    image = o.str("image", "art").orEmpty(),
                )
            )
        }
    }

    suspend fun spins(): Result<JsonObject> = runCatching { api.spins().payload() }

    suspend fun spinCharacter(id: String): Result<JsonObject> = runCatching {
        val res = api.spinCharacter(id)
        loadCharacters()
        loadMe()
        res
    }

    // ── Season ────────────────────────────────────────────────
    suspend fun loadSeason() {
        val s = api.season().payload()
        Astral.season.value = Season(
            banner = s.str("banner").orEmpty(),
            title = s.str("title").orEmpty(),
            duration = s.str("duration").orEmpty(),
            tierIndex = s.numInt("tierIndex", "currentTier") ?: 0,
            tierCount = s.numInt("tierCount") ?: 0,
            xpCurrent = s.num("xpCurrent", "xp") ?: 0,
            xpNeeded = s.num("xpNeeded") ?: 0,
            rewards = s.arr("rewards").map { el ->
                val o = el.jsonObject
                SeasonReward(tier = o.numInt("tier"), title = o.str("title").orEmpty(), image = o.str("image").orEmpty())
            },
            characters = s.arr("characters").map { el ->
                val o = el.jsonObject
                SeasonCharacter(name = o.str("name").orEmpty(), meta = o.str("meta").orEmpty(), cp = o.str("cp").orEmpty(), image = o.str("image").orEmpty())
            },
            tiers = s.arr("tiers").map { el ->
                val o = el.jsonObject
                SeasonTier(side = o.str("side").orEmpty(), tier = o.numInt("tier"), title = o.str("title").orEmpty(), image = o.str("image").orEmpty())
            },
        )
    }

    suspend fun seasonTier(tier: Int): Result<JsonObject> = runCatching { api.seasonTier(tier).payload() }

    // ── Shop ──────────────────────────────────────────────────
    suspend fun loadShop() {
        val res = api.shop().payload()
        val list = res.arr("items", "shop", "results")
        Astral.shop.clear()
        list.forEach { el ->
            val o = el.jsonObject
            Astral.shop.add(
                ShopItem(
                    name = o.str("name").orEmpty(),
                    desc = o.str("desc", "description").orEmpty(),
                    price = o.num("price"),
                    image = o.str("image").orEmpty(),
                    category = o.str("category").orEmpty().ifEmpty { "items" },
                    currency = o.str("currency").orEmpty().ifEmpty { "solars" },
                )
            )
        }
    }

    suspend fun buyShopItem(itemId: String, qty: Int = 1): Result<JsonObject> = runCatching {
        val res = api.shopBuy(buildJsonObject { put("itemId", itemId); put("qty", qty) })
        loadMe()
        res
    }

    // ── Cards ─────────────────────────────────────────────────
    suspend fun cardPrices(): Result<JsonObject> = runCatching { api.cardPrices().payload() }
    suspend fun cardCatalog(): Result<JsonObject> = runCatching { api.cardCatalog().payload() }
    suspend fun buyCardTier(tier: Int): Result<JsonObject> = runCatching {
        val res = api.buyCardTier(buildJsonObject { put("tier", tier) })
        loadMe()
        res
    }

    // ── Premium / top-up ─────────────────────────────────────
    suspend fun loadPremium() {
        val res = api.premium().payload()
        Astral.topUp.premium.clear()
        res.arr("packages", "items", "results").forEach { el ->
            val o = el.jsonObject
            Astral.topUp.premium.add(
                com.astralofthesun.app.data.TopUpPackage(
                    id = o.str("id").orEmpty(),
                    title = o.str("title", "name").orEmpty(),
                    amount = o.num("amount"),
                    price = o.num("price"),
                    currency = o.str("currency").orEmpty().ifEmpty { "solars" },
                    desc = o.str("desc").orEmpty(),
                )
            )
        }
    }

    suspend fun loadTopUp() {
        // No dedicated /api/topup endpoint is published; premium doubles as the
        // top-up package list until a /api/topup/packages route exists.
    }

    // ── Pokémon module ───────────────────────────────────────
    suspend fun pokemonOverview(): Result<JsonObject> = runCatching { api.pokemonOverview().payload() }
    suspend fun pokemonMeta(): Result<JsonObject> = runCatching { api.pokemonMeta().payload() }
    suspend fun pokemonStarter(): Result<JsonObject> = runCatching { api.pokemonStarter().payload() }
    suspend fun choosePokemonStarter(speciesId: String): Result<JsonObject> = runCatching {
        api.choosePokemonStarter(buildJsonObject { put("speciesId", speciesId) })
    }
    suspend fun pokemonMons(): Result<List<JsonObject>> = runCatching {
        api.pokemonMons().payload().arr("mons", "items", "results").map { it.jsonObject }
    }
    suspend fun pokemonMon(id: String): Result<JsonObject> = runCatching { api.pokemonMon(id).payload() }
    suspend fun setMainPokemon(id: String): Result<JsonObject> = runCatching { api.setMainPokemon(id) }
    suspend fun pokemonParty(): Result<List<JsonObject>> = runCatching {
        api.pokemonParty().payload().arr("party", "items", "results").map { it.jsonObject }
    }
    suspend fun healPokemon(): Result<JsonObject> = runCatching { api.healPokemon() }
    suspend fun feedPokemon(id: String, itemId: String): Result<JsonObject> = runCatching {
        api.feedPokemon(id, buildJsonObject { put("itemId", itemId) })
    }
    suspend fun trainPokemon(id: String): Result<JsonObject> = runCatching { api.trainPokemon(id) }
    suspend fun releasePokemon(id: String): Result<JsonObject> = runCatching { api.releasePokemon(id) }
    suspend fun holdPokemon(id: String): Result<JsonObject> = runCatching { api.holdPokemon(id) }
    suspend fun unholdPokemon(id: String): Result<JsonObject> = runCatching { api.unholdPokemon(id) }
    suspend fun pokemonEvolution(id: String): Result<JsonObject> = runCatching { api.pokemonEvolution(id).payload() }
    suspend fun evolvePokemon(id: String): Result<JsonObject> = runCatching { api.evolvePokemon(id) }
    suspend fun pokemonMoves(id: String): Result<JsonObject> = runCatching { api.pokemonMoves(id).payload() }
    suspend fun pokemonDex(): Result<List<JsonObject>> = runCatching {
        api.pokemonDex().payload().arr("dex", "items", "results").map { it.jsonObject }
    }
    suspend fun pokemonBag(): Result<List<JsonObject>> = runCatching {
        api.pokemonBag().payload().arr("bag", "items", "results").map { it.jsonObject }
    }
    suspend fun importPokemonBag(code: String): Result<JsonObject> = runCatching {
        api.importPokemonBag(buildJsonObject { put("code", code) })
    }
    suspend fun usePokemonBagItem(itemId: String, monId: String? = null): Result<JsonObject> = runCatching {
        api.usePokemonBagItem(buildJsonObject { put("itemId", itemId); if (monId != null) put("monId", monId) })
    }
    suspend fun pokemonShop(): Result<List<JsonObject>> = runCatching {
        api.pokemonShop().payload().arr("items", "shop", "results").map { it.jsonObject }
    }
    suspend fun pokemonShopBuy(itemId: String, qty: Int = 1): Result<JsonObject> = runCatching {
        api.pokemonShopBuy(buildJsonObject { put("itemId", itemId); put("qty", qty) })
    }
    suspend fun pokemonShopSell(itemId: String, qty: Int = 1): Result<JsonObject> = runCatching {
        api.pokemonShopSell(buildJsonObject { put("itemId", itemId); put("qty", qty) })
    }
    suspend fun pokemonHunt(): Result<JsonObject> = runCatching { api.pokemonHunt() }
    suspend fun startPokemonBattle(monId: String, opponentId: String? = null): Result<JsonObject> = runCatching {
        api.startPokemonBattle(buildJsonObject { put("monId", monId); if (opponentId != null) put("opponentId", opponentId) })
    }
    suspend fun pokemonBattleAct(battleId: String, move: String): Result<JsonObject> = runCatching {
        api.pokemonBattleAct(buildJsonObject { put("battleId", battleId); put("move", move) })
    }
    suspend fun forfeitPokemonBattle(battleId: String): Result<JsonObject> = runCatching {
        api.forfeitPokemonBattle(buildJsonObject { put("battleId", battleId) })
    }
    suspend fun pokemonTower(): Result<JsonObject> = runCatching { api.pokemonTower().payload() }
    suspend fun challengePokemonTower(): Result<JsonObject> = runCatching { api.challengePokemonTower() }
}
