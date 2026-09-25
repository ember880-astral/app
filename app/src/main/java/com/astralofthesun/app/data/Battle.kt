package com.astralofthesun.app.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/* ============================================================
   Dungeon battle — turn-based combat state and action processor.

   Two separate resource pools (neither is the other):
     • Runs      — spent once per dungeon ENTRY (7/day free, 20 premium)
     • Stamina   — spent once per FIGHT inside a dungeon (30/day)

   Death is real: on Defeat the backend decides whether gear is lost,
   whether Premium auto-revive or a held item (Totem etc.) saves the
   player. The FloorResult carries that truth back to the UI.

   Ships empty by design — no fake damage numbers. Wire onAction and
   the backend fills every field.
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
)

data class BattleSkill(
    val id: String,
    val name: String = "",
    val mpCost: Int? = null,
    val effect: String = "",
)

data class LogEntry(
    val id: String,
    val text: String,
)

enum class BattleOutcome { InProgress, Victory, Defeat, Fled }

/** Whether this is a swarm floor (multiple enemies) or a 1-on-1 boss floor. */
enum class FloorKind { Normal, Swarm, Boss }

/** Boss telegraphed attack: shown to the player before the boss acts. */
data class BossTelegraph(
    val label: String = "",      // e.g. "Frost Claw — AoE ice attack"
    val countdownSec: Int? = null,  // null = no timer; non-null shows a countdown
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
    val player = mutableStateOf(Combatant())
    val enemies = mutableStateListOf<Combatant>()   // 1 for normal/boss, N for swarm
    val skills = mutableStateListOf<BattleSkill>()
    val log = mutableStateListOf<LogEntry>()
    val outcome = mutableStateOf(BattleOutcome.InProgress)
    val actionInFlight = mutableStateOf(false)

    val floorKind = mutableStateOf(FloorKind.Normal)
    val bossTelegraph = mutableStateOf<BossTelegraph?>(null)
    val bossTimerRemaining = mutableStateOf<Int?>(null)  // seconds, null if no timer
    val bossPhase = mutableStateOf(1)
    val selectedTargetId = mutableStateOf<String?>(null)

    val isDefending = mutableStateOf(false)          // "Defend" was the last action
    val skillMenuOpen = mutableStateOf(false)
    val itemMenuOpen = mutableStateOf(false)

    /** Backend hook — resolve one action, mutate state, return true on success. */
    var onAction: (suspend (BattleAction) -> Boolean)? = null

    private var seq = 0
    private val scope = CoroutineScope(Dispatchers.Default)

    fun reset() {
        player.value = Combatant()
        enemies.clear()
        skills.clear()
        log.clear()
        outcome.value = BattleOutcome.InProgress
        actionInFlight.value = false
        floorKind.value = FloorKind.Normal
        bossTelegraph.value = null
        bossTimerRemaining.value = null
        bossPhase.value = 1
        selectedTargetId.value = null
        isDefending.value = false
        skillMenuOpen.value = false
        itemMenuOpen.value = false
    }

    /** Convenience: the single enemy for normal/boss floors. */
    val singleEnemy get() = enemies.firstOrNull()

    /** Tap an enemy to target it (swarm floors). */
    fun selectTarget(id: String) {
        selectedTargetId.value = if (selectedTargetId.value == id) null else id
    }

    fun act(kind: String, skillId: String? = null, itemId: String? = null): BattleAction {
        // Flee is not allowed in boss fights — drop it silently; UI should grey the button.
        if (kind == "flee" && floorKind.value == FloorKind.Boss) {
            appendLog("You cannot flee from a boss.")
            return BattleAction(id = "noop", kind = kind, status = ActionStatus.Failed)
        }

        skillMenuOpen.value = false
        itemMenuOpen.value = false
        if (kind == "defend") isDefending.value = true

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
            if (!ok) appendLog("Action failed — try again.")
            if (kind != "defend") isDefending.value = false
            actionInFlight.value = false
        }
        return action
    }

    fun appendLog(text: String) {
        seq += 1
        log.add(0, LogEntry(id = "log-$seq", text = text))
        if (log.size > 30) log.removeAt(log.lastIndex)
    }
}
