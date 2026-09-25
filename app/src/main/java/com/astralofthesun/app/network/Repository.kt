package com.astralofthesun.app.network

import androidx.compose.runtime.mutableStateOf
import com.astralofthesun.app.data.Astral
import com.astralofthesun.app.data.BattleAction
import com.astralofthesun.app.data.BattleOutcome
import com.astralofthesun.app.data.FloorKind
import com.astralofthesun.app.data.SkillLoadout
import com.astralofthesun.app.data.Notification
import com.astralofthesun.app.data.Player
import com.astralofthesun.app.data.Season
import com.astralofthesun.app.data.SeasonCharacter
import com.astralofthesun.app.data.SeasonReward
import com.astralofthesun.app.data.SeasonTier
import com.astralofthesun.app.data.ShopItem
import com.astralofthesun.app.data.Stats
import com.astralofthesun.app.data.Wallet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import retrofit2.HttpException
import java.io.IOException

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

    /* Signed handle from /api/auth/lookup (10-minute token with the JID baked
       in) — sent back on request-otp/verify-otp. Kept in memory only. */
    val pendingHandle = mutableStateOf<String?>(null)

    /* True after verify-otp for a brand-new phone with no character yet:
       the login screen shows character creation before entering the app. */
    val needsRegistration = mutableStateOf(false)
}

object Repository {

    private val api get() = ApiClient.api

    // ── Bootstrap ────────────────────────────────────────────
    /** Wired to Astral.loadData — call once the app has a session (or to check for one). */
    fun install() {
        Astral.loadData = { refreshAll() }
        // Every Attack/Skill/Defend/Item/Flee tap goes to the server, which resolves the turn.
        Astral.battle.onAction = ::sendBattleAction
        AuthState.isLoggedIn.value = false
    }

    /** Checks the saved login token. App hydrates data after the auth gate opens. */
    suspend fun bootstrap() {
        if (AuthTokenStore.token.isNullOrBlank()) {
            AuthState.isLoggedIn.value = false
            return
        }
        // The interceptor attaches the token; the session endpoint validates it.
        AuthState.isLoggedIn.value = authCall { isAuthenticatedSession(api.session()) }
            .getOrElse { error ->
                val apiError = error as? ApiError
                when {
                    // Token rejected → force re-login.
                    apiError?.unauthorized == true -> { AuthTokenStore.clear(); false }
                    // Session endpoint not deployed → trust the saved token.
                    apiError?.message == NOT_LIVE_MESSAGE -> true
                    // Offline / cold start → keep the local session; the screens
                    // will surface connectivity errors themselves.
                    error.message?.contains("reach the server") == true -> true
                    else -> false
                }
            }
    }

    internal fun isAuthenticatedSession(response: JsonObject): Boolean {
        val p = response.requireAuthSuccess().payload()
        // Live shape from /api/auth/session: {"ok":true,"signedIn":bool,"player":{…}|null}
        return p.booleanFlag("signedIn") ?: p.booleanFlag("loggedIn") ?: p.booleanFlag("valid")
            ?: p.booleanFlag("authenticated")
            ?: (p.obj("player", "user") != null || p.str("uid", "id") != null)
    }

    private fun JsonObject.booleanFlag(key: String): Boolean? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull

    // ── Auth flow: lookup -> request-otp -> verify-otp, or register ──
    private suspend fun <T> authCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: HttpException) {
        // Surface the server's own error text ({"ok":false,"error":"…"}) when it sent one.
        Result.failure(errorFromBody(e.code(), runCatching { e.response()?.errorBody()?.string() }.getOrNull()))
    } catch (e: IOException) {
        Result.failure(ApiError("Can't reach the server. Check your connection and try again."))
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun JsonObject.requireAuthSuccess(): JsonObject {
        val p = payload()
        val rejected = listOf(this, p).any {
            it.booleanFlag("success") == false || it.booleanFlag("ok") == false ||
                it.booleanFlag("found") == false || it.booleanFlag("authenticated") == false ||
                it.booleanFlag("valid") == false || it.booleanFlag("loggedIn") == false ||
                (it["error"] != null && it["error"] !is kotlinx.serialization.json.JsonNull &&
                    it.str("error") != "false" && it.str("error") != "")
        }
        check(!rejected) { str("error", "message") ?: p.str("error", "message")
            ?: "The request was not accepted. Please check your details and try again." }
        return this
    }

    fun lookupProfile(response: JsonObject): Player {
        val p = response.payload()
        val user = p.obj("user", "player", "profile") ?: p
        return Player(
            name = user.str("displayName", "display_name", "username", "name").orEmpty(),
            sub = user.str("maskedPhone", "masked_phone", "phone").orEmpty(),
            avatar = user.str("avatar", "pfp", "avatarUrl", "avatar_url").orEmpty(),
        )
    }

    /** Step 1 — find the account by username/handle or character name.
     *  Server replies { found, handle(signed token), name, maskedPhone, … }
     *  or 404 "No character goes by that name." */
    suspend fun lookup(username: String): Result<JsonObject> = authCall {
        val res = api.authLookup(buildJsonObject { put("username", username.trim()) })
            .requireAuthSuccess()
        val handle = res.payload().str("handle", "token")
            ?: error("The server accepted the lookup but returned no handle.")
        AuthState.pendingHandle.value = handle
        res
    }

    /** Step 2 — send the signed handle back; the bot DMs a 6-digit code on WhatsApp. */
    suspend fun requestOtp(handle: String): Result<JsonObject> = authCall {
        val res = api.requestOtp(buildJsonObject { put("handle", handle) }).requireAuthSuccess()
        AuthState.pendingHandle.value = handle
        res
    }

    /** New-player path: no character yet, so a raw WhatsApp number goes in instead
     *  of a handle. The server resolves the number and returns a handle like step 1. */
    suspend fun requestOtpForPhone(phone: String): Result<JsonObject> = authCall {
        val res = api.requestOtp(buildJsonObject { put("phone", phone.trim()) }).requireAuthSuccess()
        val handle = res.payload().str("handle", "token") ?: phone.trim()
        AuthState.pendingHandle.value = handle
        res
    }

    /** Step 3 — code from the WhatsApp DM + handle → JWT login token. */
    suspend fun verifyOtp(code: String, handle: String? = null): Result<JsonObject> = authCall {
        val h = handle ?: AuthState.pendingHandle.value ?: error("Request a code first")
        val res = api.verifyOtp(buildJsonObject { put("handle", h); put("code", code.trim()) })
            .requireAuthSuccess()
        val p = res.payload()
        val token = p.str("token", "jwt")
            ?: error("Login was accepted but no token was returned. Please try again.")
        AuthTokenStore.save(token)
        AuthState.needsRegistration.value = p.bool("needsRegistration", "needs_registration") == true
        if (!AuthState.needsRegistration.value) {
            AuthState.pendingHandle.value = null
            AuthState.isLoggedIn.value = true
        }
        res
    }

    /** Step 4 — brand-new phones only (needsRegistration): create the character.
     *  Requires the JWT from step 3, attached automatically by the interceptor. */
    suspend fun register(name: String, charClass: String, race: String): Result<JsonObject> = authCall {
        val res = api.register(buildJsonObject {
            put("name", name.trim())
            put("class", charClass)
            put("race", race)
        }).requireOk()
        AuthState.pendingHandle.value = null
        AuthState.needsRegistration.value = false
        AuthState.isLoggedIn.value = true
        refreshAll()
        res
    }

    suspend fun logout() {
        runCatching { api.logout() }
        AuthTokenStore.clear()
        AuthState.isLoggedIn.value = false
        AuthState.needsRegistration.value = false
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
            launch { runCatching { loadWorld() } },
            launch { runCatching { loadSkills() } },
            launch { runCatching { loadBattleItems() } },
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
        // Live shape: { currency, shelves:[{ key, label, groups:[{ label, rows:[item…] }] }] }
        val items = Parsers.shop(api.shop())
        Astral.shop.clear()
        Astral.shop.addAll(items)
    }

    /** Sends the tap only — the server checks funds/level, charges and delivers (or refuses). */
    suspend fun buyShopItem(itemId: String, qty: Int = 1): Result<JsonObject> =
        apiCall { api.shopBuy(buildJsonObject { put("itemId", itemId); put("qty", qty) }) }
            .onSuccess { runCatching { loadMe() } }

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

    // ── Dungeon ──────────────────────────────────────────────
    // Runs (7/day free, 20 Premium) are spent once per ENTRY; Stamina (30/day)
    // once per FIGHT. The server owns both counters and every combat result —
    // the app sends the tap and renders the answer.

    suspend fun loadWorld(): Result<Unit> = apiCall { api.dungeonWorld() }.map { res ->
        val p = res.payload()
        val locs = p.objs("locations", "dungeons").map(Parsers::location)
        Astral.locations.clear()
        Astral.locations.addAll(locs)
        Astral.dungeonLimits.value = Parsers.limits(p.obj("limits"), Astral.dungeonLimits.value)
    }

    suspend fun loadSkills(): Result<Unit> = apiCall { api.skills() }.map { res ->
        val (equipped, pool) = Parsers.skills(res)
        Astral.skillLoadout.value = SkillLoadout(equipped[0], equipped[1], equipped[2], equipped[3])
        Astral.skillPool.clear()
        Astral.skillPool.addAll(pool)
        Unit
    }

    /** Equip/Unequip are server calls; the loadout shown is always the server's copy. */
    suspend fun equipSkill(slot: Int, skillId: String): Result<Unit> =
        apiCall { api.equipSkill(buildJsonObject { put("slot", slot); put("skillId", skillId) }) }
            .map { loadSkills().getOrThrow() }

    suspend fun unequipSkill(slot: Int): Result<Unit> =
        apiCall { api.unequipSkill(buildJsonObject { put("slot", slot) }) }
            .map { loadSkills().getOrThrow() }

    /** Consumables for the fight's Item button. */
    suspend fun loadBattleItems(): Result<Unit> = apiCall { api.inventory() }.map { res ->
        val items = Parsers.usableItems(res)
        Astral.inventory.clear()
        Astral.inventory.addAll(items)
        Unit
    }

    /** Resume an in-progress fight after an app restart. */
    suspend fun loadActiveBattle(): Result<Boolean> = apiCall { api.dungeonBattle() }.map { res ->
        val b = res.payload().obj("battle") ?: return@map false
        applyBattle(b)
        true
    }

    /* State is swapped in one synchronous step AFTER any follow-up network
       refresh, so the result screen never sees a half-cleared state. */

    /** Enter / Resume — the server spends 1 Run (or a Newcomer's Hollow floor) and opens the fight. */
    suspend fun enterDungeon(locationId: String): Result<Unit> {
        val r = apiCall { api.dungeonEnter(buildJsonObject { put("locationId", locationId) }) }
        return r.map { res ->
            Astral.battle.reset()
            Astral.floorResult.value = null
            Astral.battle.locationId.value = locationId
            applyBattleResponse(res)
        }.onSuccess { runCatching { loadWorld() } }
    }

    /** Next Floor — the server spends 1 Stamina for the next fight. */
    suspend fun nextFloor(): Result<Unit> {
        val id = Astral.battle.battleId.value ?: return Result.failure(ApiError("No active run."))
        val r = apiCall { api.dungeonNext(buildJsonObject { put("battleId", id) }) }
        return r.map { res ->
            val loc = Astral.battle.locationId.value
            val name = Astral.battle.locationName.value
            Astral.battle.reset()
            Astral.battle.locationId.value = loc
            Astral.battle.locationName.value = name
            Astral.floorResult.value = null
            applyBattleResponse(res)
        }
    }

    /** Leave Dungeon — the server saves the checkpoint and ends the run. */
    suspend fun leaveDungeon(): Result<Unit> {
        val id = Astral.battle.battleId.value
        val r: Result<Unit> = if (id == null) Result.success(Unit)
        else apiCall { api.dungeonLeave(buildJsonObject { put("battleId", id) }) }.map { }
        if (r.isSuccess) {
            runCatching { loadWorld() }
            Astral.battle.reset()
            Astral.floorResult.value = null
        }
        return r
    }

    /** After death the bag may be wiped server-side — re-read it, then clear the fight. */
    suspend fun afterDeath() {
        runCatching { loadMe() }
        runCatching { loadWorld() }
        runCatching { loadBattleItems() }
        Astral.battle.reset()
        Astral.floorResult.value = null
    }

    private suspend fun sendBattleAction(action: BattleAction): Boolean {
        val id = Astral.battle.battleId.value
        if (id == null) {
            Astral.battle.lastError.value = "No active battle."
            return false
        }
        return apiCall {
            api.dungeonAct(buildJsonObject {
                put("battleId", id)
                put("action", action.kind)
                action.targetId?.let { put("targetId", it) }
                action.skillId?.let { put("skillId", it) }
                action.itemId?.let { put("itemId", it) }
            })
        }.fold(
            onSuccess = { applyBattleResponse(it); true },
            onFailure = { Astral.battle.lastError.value = it.userMessage(); false },
        )
    }

    private fun applyBattleResponse(res: JsonObject) {
        val p = res.payload()
        (p.obj("battle") ?: p.takeIf { it.obj("player") != null })?.let(::applyBattle)
        p.obj("result", "floorResult")?.let { Astral.floorResult.value = Parsers.floorResult(it) }
        p.obj("limits")?.let { Astral.dungeonLimits.value = Parsers.limits(it, Astral.dungeonLimits.value) }
    }

    private fun applyBattle(b: JsonObject) {
        val battle = Astral.battle
        b.str("id", "battleId")?.let { battle.battleId.value = it }
        b.str("locationId")?.let { battle.locationId.value = it }
        b.str("locationName")?.let { battle.locationName.value = it }
        b.str("art", "background", "backgroundArt")?.let { battle.backgroundArt.value = it }
        b.numInt("floor")?.let { battle.floor.value = it }
        b.numInt("totalFloors", "floors")?.let { battle.totalFloors.value = it }
        battle.player.value = Parsers.combatant(b.obj("player"))

        val enemies = b.objs("enemies").map(Parsers::combatant).ifEmpty {
            listOfNotNull(b.obj("enemy", "boss")?.let(Parsers::combatant))
        }
        battle.enemies.clear()
        battle.enemies.addAll(enemies)
        val target = battle.selectedTargetId.value
        if (target != null && enemies.none { it.id == target && it.alive }) battle.selectedTargetId.value = null

        battle.skills.clear()
        battle.skills.addAll(b.objs("skills").map(Parsers::battleSkill))

        val isBoss = b.bool("boss", "isBoss") == true || b.str("kind", "floorKind") == "boss"
        battle.floorKind.value = when {
            isBoss -> FloorKind.Boss
            enemies.size > 1 -> FloorKind.Swarm
            else -> FloorKind.Normal
        }
        battle.bossTelegraph.value = Parsers.telegraph(b.obj("telegraph", "bossTelegraph"))
        battle.turnDeadlineAt.value = Parsers.time(b, "turnDeadlineAt", "deadline")
        b.numInt("bossPhase", "phase")?.let { battle.bossPhase.value = it }
        battle.fleeChance.value = b.numInt("fleeChance")
        battle.isDefending.value = b.bool("defending") ?: false
        val log = b.strs("log")
        if (log.isNotEmpty()) battle.setLog(log)
        battle.outcome.value = when (b.str("outcome", "status")?.lowercase()) {
            "victory", "won", "win" -> BattleOutcome.Victory
            "defeat", "lost", "dead", "death" -> BattleOutcome.Defeat
            "fled", "flee" -> BattleOutcome.Fled
            else -> BattleOutcome.InProgress
        }
    }

    // ── Pokémon module ───────────────────────────────────────
    suspend fun pokemonOverview(): Result<JsonObject> = apiMap({ api.pokemonOverview() }) { it }
    suspend fun pokemonMeta(): Result<JsonObject> = apiMap({ api.pokemonMeta() }) { it }
    suspend fun pokemonStarter(): Result<JsonObject> = apiMap({ api.pokemonStarter() }) { it }
    suspend fun choosePokemonStarter(speciesId: String): Result<JsonObject> = apiCall {
        api.choosePokemonStarter(buildJsonObject { put("speciesId", speciesId) })
    }
    suspend fun pokemonMons(): Result<List<JsonObject>> = apiMap({ api.pokemonMons() }) { it.objs("mons", "items", "results") }
    suspend fun pokemonMon(id: String): Result<JsonObject> = apiMap({ api.pokemonMon(id) }) { it }
    suspend fun setMainPokemon(id: String): Result<JsonObject> = apiCall { api.setMainPokemon(id) }
    suspend fun pokemonParty(): Result<List<JsonObject>> = apiMap({ api.pokemonParty() }) { it.objs("party", "items", "results") }
    suspend fun healPokemon(): Result<JsonObject> = apiCall { api.healPokemon() }
    /** Feed costs Solars server-side (meta.costs.feed); an item is optional. */
    suspend fun feedPokemon(id: String, itemId: String? = null): Result<JsonObject> = apiCall {
        api.feedPokemon(id, buildJsonObject { if (itemId != null) put("itemId", itemId) })
    }
    suspend fun trainPokemon(id: String): Result<JsonObject> = apiCall { api.trainPokemon(id) }
    suspend fun releasePokemon(id: String): Result<JsonObject> = apiCall { api.releasePokemon(id) }
    suspend fun holdPokemon(id: String): Result<JsonObject> = apiCall { api.holdPokemon(id) }
    suspend fun unholdPokemon(id: String): Result<JsonObject> = apiCall { api.unholdPokemon(id) }
    suspend fun pokemonEvolution(id: String): Result<JsonObject> = apiMap({ api.pokemonEvolution(id) }) { it }
    suspend fun evolvePokemon(id: String): Result<JsonObject> = apiCall { api.evolvePokemon(id) }
    suspend fun pokemonMoves(id: String): Result<JsonObject> = apiMap({ api.pokemonMoves(id) }) { it }
    suspend fun pokemonDex(): Result<List<JsonObject>> = apiMap({ api.pokemonDex() }) { it.objs("dex", "items", "results") }
    suspend fun pokemonBag(): Result<List<JsonObject>> = apiMap({ api.pokemonBag() }) { it.objs("bag", "items", "results") }
    suspend fun importPokemonBag(code: String): Result<JsonObject> = apiCall {
        api.importPokemonBag(buildJsonObject { put("code", code) })
    }
    suspend fun usePokemonBagItem(itemId: String, monId: String? = null): Result<JsonObject> = apiCall {
        api.usePokemonBagItem(buildJsonObject { put("itemId", itemId); if (monId != null) put("monId", monId) })
    }
    suspend fun pokemonShop(): Result<List<JsonObject>> = apiMap({ api.pokemonShop() }) { it.objs("items", "shop", "results") }
    suspend fun pokemonShopBuy(itemId: String, qty: Int = 1): Result<JsonObject> = apiCall {
        api.pokemonShopBuy(buildJsonObject { put("itemId", itemId); put("qty", qty) })
    }
    suspend fun pokemonShopSell(itemId: String, qty: Int = 1): Result<JsonObject> = apiCall {
        api.pokemonShopSell(buildJsonObject { put("itemId", itemId); put("qty", qty) })
    }
    suspend fun pokemonHunt(): Result<JsonObject> = apiCall { api.pokemonHunt() }
    suspend fun startPokemonBattle(monId: String, opponentId: String? = null): Result<JsonObject> = apiCall {
        api.startPokemonBattle(buildJsonObject { put("monId", monId); if (opponentId != null) put("opponentId", opponentId) })
    }
    suspend fun pokemonBattleAct(battleId: String, move: String): Result<JsonObject> = apiCall {
        api.pokemonBattleAct(buildJsonObject { put("battleId", battleId); put("move", move) })
    }
    suspend fun forfeitPokemonBattle(battleId: String): Result<JsonObject> = apiCall {
        api.forfeitPokemonBattle(buildJsonObject { put("battleId", battleId) })
    }
    suspend fun pokemonTower(): Result<JsonObject> = apiMap({ api.pokemonTower() }) { it }
    suspend fun challengePokemonTower(): Result<JsonObject> = apiCall { api.challengePokemonTower() }
}
