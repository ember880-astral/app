package com.astralofthesun.app.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

/* ============================================================
   Astral of the Sun — shared data layer (native port)
   Ships EMPTY on purpose: no fake names, numbers or images.
   Wire Astral.loadData to your backend and every screen
   hydrates from these observable states.
   ============================================================ */

data class Player(val name: String = "", val sub: String = "", val avatar: String = "")

data class Stats(val level: Int? = null, val solars: Long? = null, val gems: Long? = null)

data class Wallet(val solars: Long? = null, val gems: Long? = null)

data class ShopItem(
    val id: String = "",
    val name: String = "",
    val desc: String = "",
    val price: Long? = null,
    val image: String = "",
    val category: String = "items",
    val currency: String = "solars",
)

data class SeasonTier(val side: String = "free", val tier: Int? = null, val title: String = "", val image: String = "")
data class SeasonReward(val tier: Int? = null, val title: String = "", val image: String = "")
data class SeasonCharacter(val name: String = "", val meta: String = "", val cp: String = "", val image: String = "")

data class Season(
    val banner: String = "",
    val title: String = "",
    val duration: String = "",
    val tierIndex: Int = 0,
    val tierCount: Int = 0,
    val xpCurrent: Long = 0,
    val xpNeeded: Long = 0,
    val rewards: List<SeasonReward> = emptyList(),
    val characters: List<SeasonCharacter> = emptyList(),
    val tiers: List<SeasonTier> = emptyList(),
)

data class InvItem(val id: String, val name: String = "", val image: String = "")

data class Notification(val title: String = "", val sub: String = "")

data class TopUpPackage(
    val id: String = "",
    val title: String = "",
    val amount: Long? = null,
    val price: Long? = null,
    val currency: String = "solars",
    val desc: String = "",
)

data class ServerOffer(val id: String = "", val title: String = "", val desc: String = "", val image: String = "")

/* ── World Map ── */

/** One entry on the World Map — a town or a dungeon location. */
data class LocationEntry(
    val id: String = "",
    val name: String = "",
    val art: String = "",                      // background image URL
    val isTown: Boolean = false,               // town = safe, no run cost
    val isNewcomer: Boolean = false,           // true for Newcomer's Hollow
    val levelRange: String = "",               // e.g. "1–10"
    val unlocked: Boolean = false,
    val prerequisiteId: String? = null,        // locked until this dungeon cleared
    val prerequisiteName: String? = null,
    val prerequisiteLevel: Int? = null,        // locked until player reaches this level
    val currentFloor: Int? = null,             // floor the player last saved a checkpoint at
    val totalFloors: Int? = null,
    val bossFloors: List<Int> = emptyList(),
    val checkpointInterval: Int? = null,       // a checkpoint is saved every N floors
    val description: String = "",
)

/** Daily run/stamina limits — two separate pools. */
data class DungeonLimits(
    val runsUsed: Int = 0,
    val runsMax: Int = 7,          // 20 if premium
    val newcomerRunsUsed: Int = 0,
    val newcomerRunsMax: Int = 3,  // Newcomer's Hollow has its own allowance
    val staminaUsed: Int = 0,
    val staminaMax: Int = 30,
    val resetTimeMinutes: Int = 0, // minutes until daily reset (legacy)
    val resetAt: Long? = null,     // epoch ms of next reset from the server; null → local midnight
    val isPremium: Boolean = false,
)

/* ── Dungeon prep + battle ── */

data class DungeonInfo(
    val id: String = "",
    val name: String = "",
    val floor: Int? = null,
    val difficulty: String = "",
    val recommendedLevel: Int? = null,
    val rewardsPreview: String = "",
    val image: String = "",
)

data class PartyMember(
    val id: String = "",
    val name: String = "",
    val avatar: String = "",
    val level: Int? = null,
    val ready: Boolean = false,
)

data class LoadoutState(
    val weapon: InvItem? = null,
    val armor: InvItem? = null,
    val relic: InvItem? = null,
    val consumable: InvItem? = null,
)

/* ── Skill loadout ── */

/** One skill in the player's full skill pool. */
data class Skill(
    val id: String,
    val name: String = "",
    val mpCost: Int? = null,
    val effect: String = "",  // short description, e.g. "Deal 150% fire damage"
    val unlocked: Boolean = true,
)

/** The player's 4 equipped skill slots. null = empty slot. */
data class SkillLoadout(
    val slot1: Skill? = null,
    val slot2: Skill? = null,
    val slot3: Skill? = null,
    val slot4: Skill? = null,
) {
    fun toList() = listOf(slot1, slot2, slot3, slot4)
    fun withSlot(index: Int, skill: Skill?) = when (index) {
        0 -> copy(slot1 = skill)
        1 -> copy(slot2 = skill)
        2 -> copy(slot3 = skill)
        3 -> copy(slot4 = skill)
        else -> this
    }
    fun equippedIds() = toList().mapNotNull { it?.id }.toSet()
}

/* ── Floor result (win / death) ── */

data class LootItem(val name: String = "", val image: String = "", val qty: Int = 1)

/** What the backend sends back after a floor concludes. */
data class FloorResult(
    val victory: Boolean = false,
    val xpGained: Int? = null,
    val solarsGained: Long? = null,
    val leveledUp: Boolean = false,
    val newLevel: Int? = null,
    val newSkillUnlocked: Skill? = null,
    val loot: List<LootItem> = emptyList(),
    val floor: Int? = null,
    val isLastFloor: Boolean = false,          // dungeon fully cleared
    val checkpointSaved: Int? = null,          // floor saved as checkpoint on this win, if any
    // ── death fields ──
    val gearLost: List<LootItem> = emptyList(),   // equipped gear lost
    val bagLost: List<LootItem> = emptyList(),    // bag contents lost (everything not in the Chest)
    val savedByPremiumRevive: Boolean = false,
    val savedByItem: String? = null,           // name of the protective item (e.g. "Totem of Binding")
    val nextFloor: Int? = null,
)

object Astral {
    val player = mutableStateOf(Player())
    val banner = mutableStateOf("")
    val stats = mutableStateOf(Stats())
    val wallet = mutableStateOf(Wallet())

    val notifications = mutableStateListOf<Notification>()
    val shop = mutableStateListOf<ShopItem>()
    val roster = mutableStateListOf<SeasonCharacter>()
    val dungeons = mutableStateListOf<DungeonInfo>()
    val friends = mutableStateListOf<Player>()
    val season = mutableStateOf(Season())
    val inventory = mutableStateListOf<InvItem>()
    val vault = mutableStateListOf<InvItem>()
    val pvp = mutableStateListOf<InvItem>()

    // ── World map ──
    val locations = mutableStateListOf<LocationEntry>()
    val dungeonLimits = mutableStateOf(DungeonLimits())

    // ── Dungeon prep ──
    val selectedDungeon = mutableStateOf<DungeonInfo?>(null)
    val selectedLocation = mutableStateOf<LocationEntry?>(null)
    val loadout = mutableStateOf(LoadoutState())
    val party = mutableStateListOf<PartyMember>()

    // ── Skill loadout ──
    val skillLoadout = mutableStateOf(SkillLoadout())
    val skillPool = mutableStateListOf<Skill>()

    // ── Battle + floor result ──
    val battle = Battle()
    val floorResult = mutableStateOf<FloorResult?>(null)

    val topUp = TopUp()

    /** Replace with your backend fetch — keeps UI empty until wired. */
    var loadData: (suspend () -> Unit)? = null
}
