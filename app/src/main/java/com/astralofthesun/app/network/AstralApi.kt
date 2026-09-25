package com.astralofthesun.app.network

import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/* ============================================================
   Astral of the Sun — full backend surface.
   Base: https://astral-bot-production-afb0.up.railway.app/
   Auth is session-cookie based (set by verify-otp/register,
   carried automatically by ApiClient's PersistentCookieJar).

   Every request/response body is a generic JsonObject on
   purpose: the exact backend schema isn't published, so the
   Repository layer reads fields defensively (jsonObject["x"])
   and maps only what each screen actually needs. Tighten any
   of these into a @Serializable data class once you have a
   confirmed response shape.
   ============================================================ */
interface AstralApi {

    // ── Meta / health ──────────────────────────────────────
    @GET("api/health")
    suspend fun health(): JsonObject

    @GET("api/meta")
    suspend fun meta(): JsonObject

    @GET("api/stats")
    suspend fun stats(): JsonObject

    // ── Auth ────────────────────────────────────────────────
    @POST("api/auth/lookup")
    suspend fun authLookup(@Body body: JsonObject): JsonObject

    @POST("api/auth/request-otp")
    suspend fun requestOtp(@Body body: JsonObject): JsonObject

    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body body: JsonObject): JsonObject

    @POST("api/auth/register")
    suspend fun register(@Body body: JsonObject): JsonObject

    @GET("api/auth/session")
    suspend fun session(): JsonObject

    @POST("api/auth/logout")
    suspend fun logout(@Body body: JsonObject = JsonObject(emptyMap())): JsonObject

    // ── Me / profile ────────────────────────────────────────
    @GET("api/me")
    suspend fun me(): JsonObject

    @PATCH("api/me/settings")
    suspend fun updateSettings(@Body body: JsonObject): JsonObject

    @POST("api/me/sessions/revoke")
    suspend fun revokeSession(@Body body: JsonObject): JsonObject

    @GET("api/me/rankings")
    suspend fun myRankings(): JsonObject

    @Multipart
    @POST("api/me/pfp")
    suspend fun uploadPfp(@Part file: MultipartBody.Part): JsonObject

    @Multipart
    @POST("api/me/banner")
    suspend fun uploadBanner(@Part file: MultipartBody.Part): JsonObject

    // ── Notifications ───────────────────────────────────────
    @GET("api/notifications")
    suspend fun notifications(): JsonObject

    @POST("api/notifications/read-all")
    suspend fun markAllNotificationsRead(@Body body: JsonObject = JsonObject(emptyMap())): JsonObject

    @POST("api/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): JsonObject

    @DELETE("api/notifications/{id}")
    suspend fun deleteNotification(@Path("id") id: String): JsonObject

    // ── Leaderboard / players ───────────────────────────────
    @GET("api/leaderboard")
    suspend fun leaderboard(): JsonObject

    @GET("api/players/{uid}")
    suspend fun player(@Path("uid") uid: String): JsonObject

    // ── Characters / gacha spins ────────────────────────────
    @GET("api/characters")
    suspend fun characters(): JsonObject

    @GET("api/characters/{id}")
    suspend fun character(@Path("id") id: String): JsonObject

    @GET("api/spins")
    suspend fun spins(): JsonObject

    @POST("api/characters/{id}/spin")
    suspend fun spinCharacter(@Path("id") id: String, @Body body: JsonObject = JsonObject(emptyMap())): JsonObject

    // ── Season / battle pass ────────────────────────────────
    @GET("api/season")
    suspend fun season(): JsonObject

    @GET("api/season/tier/{tier}")
    suspend fun seasonTier(@Path("tier") tier: Int): JsonObject

    // ── Shop ─────────────────────────────────────────────────
    @GET("api/shop")
    suspend fun shop(): JsonObject

    @POST("api/shop/buy")
    suspend fun shopBuy(@Body body: JsonObject): JsonObject

    // ── Cards ────────────────────────────────────────────────
    @GET("api/cards/prices")
    suspend fun cardPrices(): JsonObject

    @POST("api/cards/buy-tier")
    suspend fun buyCardTier(@Body body: JsonObject): JsonObject

    @GET("api/cards/catalog")
    suspend fun cardCatalog(): JsonObject

    // ── Premium ──────────────────────────────────────────────
    @GET("api/premium")
    suspend fun premium(): JsonObject

    // ── Pokémon module ──────────────────────────────────────
    @GET("api/pokemon/meta")
    suspend fun pokemonMeta(): JsonObject

    @GET("api/pokemon/overview")
    suspend fun pokemonOverview(): JsonObject

    @GET("api/pokemon/starter")
    suspend fun pokemonStarter(): JsonObject

    @POST("api/pokemon/starter")
    suspend fun choosePokemonStarter(@Body body: JsonObject): JsonObject

    @GET("api/pokemon/mons")
    suspend fun pokemonMons(): JsonObject

    @GET("api/pokemon/mons/{id}")
    suspend fun pokemonMon(@Path("id") id: String): JsonObject

    @POST("api/pokemon/mons/{id}/main")
    suspend fun setMainPokemon(@Path("id") id: String): JsonObject

    @GET("api/pokemon/party")
    suspend fun pokemonParty(): JsonObject

    @POST("api/pokemon/heal")
    suspend fun healPokemon(@Body body: JsonObject = JsonObject(emptyMap())): JsonObject

    @POST("api/pokemon/mons/{id}/feed")
    suspend fun feedPokemon(@Path("id") id: String, @Body body: JsonObject): JsonObject

    @POST("api/pokemon/mons/{id}/train")
    suspend fun trainPokemon(@Path("id") id: String, @Body body: JsonObject = JsonObject(emptyMap())): JsonObject

    @POST("api/pokemon/mons/{id}/release")
    suspend fun releasePokemon(@Path("id") id: String): JsonObject

    @POST("api/pokemon/mons/{id}/hold")
    suspend fun holdPokemon(@Path("id") id: String): JsonObject

    @POST("api/pokemon/mons/{id}/unhold")
    suspend fun unholdPokemon(@Path("id") id: String): JsonObject

    @GET("api/pokemon/mons/{id}/evolution")
    suspend fun pokemonEvolution(@Path("id") id: String): JsonObject

    @POST("api/pokemon/mons/{id}/evolve")
    suspend fun evolvePokemon(@Path("id") id: String, @Body body: JsonObject = JsonObject(emptyMap())): JsonObject

    @GET("api/pokemon/mons/{id}/moves")
    suspend fun pokemonMoves(@Path("id") id: String): JsonObject

    @GET("api/pokemon/dex")
    suspend fun pokemonDex(): JsonObject

    @GET("api/pokemon/bag")
    suspend fun pokemonBag(): JsonObject

    @POST("api/pokemon/bag/import")
    suspend fun importPokemonBag(@Body body: JsonObject): JsonObject

    @POST("api/pokemon/bag/use")
    suspend fun usePokemonBagItem(@Body body: JsonObject): JsonObject

    @GET("api/pokemon/shop")
    suspend fun pokemonShop(): JsonObject

    @POST("api/pokemon/shop/buy")
    suspend fun pokemonShopBuy(@Body body: JsonObject): JsonObject

    @POST("api/pokemon/shop/sell")
    suspend fun pokemonShopSell(@Body body: JsonObject): JsonObject

    @POST("api/pokemon/hunt")
    suspend fun pokemonHunt(@Body body: JsonObject = JsonObject(emptyMap())): JsonObject

    @POST("api/pokemon/battle")
    suspend fun startPokemonBattle(@Body body: JsonObject): JsonObject

    @POST("api/pokemon/battle/act")
    suspend fun pokemonBattleAct(@Body body: JsonObject): JsonObject

    @POST("api/pokemon/battle/forfeit")
    suspend fun forfeitPokemonBattle(@Body body: JsonObject = JsonObject(emptyMap())): JsonObject

    @GET("api/pokemon/tower")
    suspend fun pokemonTower(): JsonObject

    @POST("api/pokemon/tower/challenge")
    suspend fun challengePokemonTower(@Body body: JsonObject = JsonObject(emptyMap())): JsonObject

    /* ── Dungeon / Skills — routes the dungeon screens call. ──────────
       Not deployed on the bot yet (they 404 today); the screens show a
       "not live yet" state until they are. Every one is a button tap —
       the server resolves it and answers with the new state. */

    /** { locations:[…], limits:{ runsUsed, runsMax, newcomerRunsUsed, newcomerRunsMax, staminaUsed, staminaMax, resetAt, premium } } */
    @GET("api/dungeon/world")
    suspend fun dungeonWorld(): JsonObject

    /** Active fight if one exists (resume after restart), else { battle:null }. */
    @GET("api/dungeon/battle")
    suspend fun dungeonBattle(): JsonObject

    /** { locationId } → spends 1 Run → { battle, limits } */
    @POST("api/dungeon/enter")
    suspend fun dungeonEnter(@Body body: JsonObject): JsonObject

    /** { battleId, action:"attack|skill|defend|item|flee", targetId?, skillId?, itemId? } → { battle, result? } */
    @POST("api/dungeon/act")
    suspend fun dungeonAct(@Body body: JsonObject): JsonObject

    /** { battleId } → next floor, spends 1 Stamina → { battle, limits } */
    @POST("api/dungeon/next")
    suspend fun dungeonNext(@Body body: JsonObject): JsonObject

    /** { battleId } → saves checkpoint, ends the run */
    @POST("api/dungeon/leave")
    suspend fun dungeonLeave(@Body body: JsonObject): JsonObject

    /** { equipped:[skill|null ×4], pool:[skill…] } */
    @GET("api/skills")
    suspend fun skills(): JsonObject

    /** { slot:0-3, skillId } */
    @POST("api/skills/equip")
    suspend fun equipSkill(@Body body: JsonObject): JsonObject

    /** { slot:0-3 } */
    @POST("api/skills/unequip")
    suspend fun unequipSkill(@Body body: JsonObject): JsonObject

    /** { bag:[item…], … } — the fight's Item button lists usable consumables from here. */
    @GET("api/inventory")
    suspend fun inventory(): JsonObject
}
