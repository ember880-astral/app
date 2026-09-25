package com.astralofthesun.app.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/* ============================================================
   Dungeon battle — turn-based combat state.

   Two separate resource pools (neither is the other):
     • Runs      — spent once per dungeon ENTRY (7/day free, 20 premium)
     • Stamina   — spent once per FIGHT inside a dungeon (30/day)

   The app never resolves combat. act() only sends the tap
   ("attack", "skill 2 on enemy B", …) through onAction; the server
   answers with the new state and Repository writes it back here.

   Death is real: on Defeat the server decides what was lost and
   whether Premium auto-revive or a held item saved the player.
   FloorResult carries that answer to the UI.
   ============================================================ */

enum class ActionStatus { Idle, Resolving, AwaitingBackend, Resolved, Failed }

data class Combatant(
    val id: String = "",
    val name: String = "",
    val image: String = "",
    val level: Int? = null,
    val hp: Int? = null,
    val maxHp: Int? = null,
    val mp: Int? = null,
    val maxMp: Int? = null,
    val isTargeted: Boolean = false,   // true when the player has tapped this enemy
    val alive: Boolean = true,
)

data class BattleSkill(
    val id: String,
    val name: String = "",
    val mpCost: Int? = null,
    val effect: String = "",
    val cooldown: Int? = null,         // turns left before usable again (PvP abilities etc.)
)

data class LogEntry(
    val id: String,
    val text: String,
)

enum class BattleOutcome { InProgress, Victory, Defeat, Fled }

/** Regular floor (1 enemy), a multi-enemy floor on the big dungeons, or a 1-on-1 boss floor. */
enum class FloorKind { Normal, Swarm, Boss }

/** Boss telegraphed attack: shown to the player before the boss acts. */
data class BossTelegraph(
    val label: String = "",          // e.g. "Frost Claw"
    val detail: String = "",         // e.g. "AoE ice attack — Defend halves it"
    val inTurns: Int? = null,        // lands in N turns (null = next turn)
)

data class BattleAction(
    val id: String,
    val kind: String,       // "attack" | "skill" | "defend" | "item" | "flee"
    val targetId: String? = null,
    val skillId: String? = null,
    val itemId: String? = null,
    var status: ActionStatus = ActionStatus.Idle,
)

class Battle {
    val battleId = mutableStateOf<String?>(null)
    val locationId = mutableStateOf<String?>(null)
    val locationName = mutableStateOf("")
    val backgroundArt = mutableStateOf("")
    val floor = mutableStateOf<Int?>(null)
    val totalFloors = mutableStateOf<Int?>(null)

    val player = mutableStateOf(Combatant())
    val enemies = mutableStateListOf<Combatant>()   // 1 for normal/boss, N on multi-enemy floors
    val skills = mutableStateListOf<BattleSkill>()  // the 4 equipped skills
    val log = mutableStateListOf<LogEntry>()
    val outcome = mutableStateOf(BattleOutcome.InProgress)
    val actionInFlight = mutableStateOf(false)
    val lastError = mutableStateOf<String?>(null)

    val floorKind = mutableStateOf(FloorKind.Normal)
    val bossTelegraph = mutableStateOf<BossTelegraph?>(null)
    val turnDeadlineAt = mutableStateOf<Long?>(null)     // boss floors: 5-minute turn timer (epoch ms)
    val bossTimerRemaining = mutableStateOf<Int?>(null)  // kept for compatibility (seconds)
    val bossPhase = mutableStateOf(1)
    val selectedTargetId = mutableStateOf<String?>(null)
    val fleeChance = mutableStateOf<Int?>(null)          // % shown on the Flee button if the server sends it

    val isDefending = mutableStateOf(false)
    val skillMenuOpen = mutableStateOf(false)
    val itemMenuOpen = mutableStateOf(false)

    /** Backend hook — send one action, write the server's answer back, return true on success. */
    var onAction: (suspend (BattleAction) -> Boolean)? = null

    private var seq = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun reset() {
        battleId.value = null
        locationId.value = null
        locationName.value = ""
        backgroundArt.value = ""
        floor.value = null
        totalFloors.value = null
        player.value = Combatant()
        enemies.clear()
        skills.clear()
        log.clear()
        outcome.value = BattleOutcome.InProgress
        actionInFlight.value = false
        lastError.value = null
        floorKind.value = FloorKind.Normal
        bossTelegraph.value = null
        turnDeadlineAt.value = null
        bossTimerRemaining.value = null
        bossPhase.value = 1
        selectedTargetId.value = null
        fleeChance.value = null
        isDefending.value = false
        skillMenuOpen.value = false
        itemMenuOpen.value = false
    }

    /** Convenience: the single enemy for normal/boss floors. */
    val singleEnemy get() = enemies.firstOrNull { it.alive } ?: enemies.firstOrNull()

    /** Tap an enemy to target it (multi-enemy floors). */
    fun selectTarget(id: String) {
        selectedTargetId.value = if (selectedTargetId.value == id) null else id
    }

    fun act(kind: String, skillId: String? = null, itemId: String? = null): BattleAction {
        // Flee is not offered on boss floors — the button is disabled; this is a guard only.
        if (kind == "flee" && floorKind.value == FloorKind.Boss) {
            appendLog("You cannot flee from a boss.")
            return BattleAction(id = "noop", kind = kind, status = ActionStatus.Failed)
        }
        if (actionInFlight.value) return BattleAction(id = "busy", kind = kind, status = ActionStatus.Failed)

        skillMenuOpen.value = false
        itemMenuOpen.value = false
        lastError.value = null

        seq += 1
        val action = BattleAction(
            id = "act-$seq-" + System.currentTimeMillis().toString(36),
            kind = kind,
            targetId = selectedTargetId.value ?: singleEnemy?.id,
            skillId = skillId,
            itemId = itemId,
        )

        val hook = onAction
        if (hook == null) {
            action.status = ActionStatus.AwaitingBackend
            appendLog("Waiting for battle server…")
            return action
        }

        actionInFlight.value = true
        action.status = ActionStatus.Resolving
        scope.launch {
            val ok = runCatching { hook(action) }.getOrDefault(false)
            action.status = if (ok) ActionStatus.Resolved else ActionStatus.Failed
            if (!ok && lastError.value == null) lastError.value = "Action failed — try again."
            actionInFlight.value = false
        }
        return action
    }

    fun appendLog(text: String) {
        seq += 1
        log.add(0, LogEntry(id = "log-$seq", text = text))
        if (log.size > 30) log.removeAt(log.lastIndex)
    }

    /** Replace the log with the server's copy (newest first). */
    fun setLog(lines: List<String>) {
        log.clear()
        lines.takeLast(30).reversed().forEach { line ->
            seq += 1
            log.add(LogEntry(id = "log-$seq", text = line))
        }
    }
}
