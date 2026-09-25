package com.astralofthesun.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astralofthesun.app.data.Astral
import com.astralofthesun.app.data.BattleOutcome
import com.astralofthesun.app.data.BattleSkill
import com.astralofthesun.app.data.BossTelegraph
import com.astralofthesun.app.data.Combatant
import com.astralofthesun.app.data.FloorKind
import com.astralofthesun.app.data.InvItem
import com.astralofthesun.app.data.LogEntry
import com.astralofthesun.app.ui.components.cardModifier
import com.astralofthesun.app.ui.theme.CardBorder
import com.astralofthesun.app.ui.theme.Gold
import com.astralofthesun.app.ui.theme.Primary
import com.astralofthesun.app.ui.theme.TextDim
import com.astralofthesun.app.ui.theme.TextFaint

/* ── Dungeon Battle Screen ────────────────────────────────────────────
   The main combat loop. Entered only from DungeonDetail (after paying
   1 Run). Stamina is spent once per fight — shown in the floor counter.

   Floors:
     • Normal  — 1 enemy, standard action bar
     • Swarm   — N enemies, tap to target before acting
     • Boss    — 1 enemy with phases, telegraphed attacks, optional
                 5-min turn timer; Flee is disabled

   When the floor ends (Victory or Defeat) the caller reads
   Astral.floorResult and routes to FloorResultScreen.
   ──────────────────────────────────────────────────────────────────── */
@Composable
fun DungeonBattleScreen(onFloorEnd: () -> Unit) {
    val player by Astral.battle.player
    val enemies = Astral.battle.enemies
    val skills = Astral.battle.skills
    val log = Astral.battle.log
    val outcome by Astral.battle.outcome
    val inFlight by Astral.battle.actionInFlight
    val floorKind by Astral.battle.floorKind
    val selectedTarget by Astral.battle.selectedTargetId
    val bossPhase by Astral.battle.bossPhase
    val telegraph by Astral.battle.bossTelegraph
    val timerSec by Astral.battle.bossTimerRemaining
    val skillMenuOpen by Astral.battle.skillMenuOpen
    val itemMenuOpen by Astral.battle.itemMenuOpen
    val isDefending by Astral.battle.isDefending
    val inventory = Astral.inventory

    val inBattle = outcome == BattleOutcome.InProgress

    // When the floor ends, bubble up
    if (!inBattle) {
        onFloorEnd()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {

        // ── Floor kind label ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val kindLabel = when (floorKind) {
                FloorKind.Normal -> "Floor"
                FloorKind.Swarm -> "⚠ Swarm"
                FloorKind.Boss -> "★ Boss"
            }
            val kindColor = when (floorKind) {
                FloorKind.Boss -> Color(0xFFE76E6E)
                FloorKind.Swarm -> Color(0xFFE7A56E)
                FloorKind.Normal -> TextDim
            }
            Text(kindLabel, color = kindColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (floorKind == FloorKind.Boss && bossPhase > 1) {
                Text("Phase $bossPhase", color = Color(0xFFE76E6E), fontSize = 12.sp)
            }
        }

        // ── Boss telegraph panel ──
        if (floorKind == FloorKind.Boss && telegraph != null) {
            BossTelegraphPanel(telegraph!!, timerSec)
        }

        // ── Enemy side ──
        when (floorKind) {
            FloorKind.Swarm -> {
                // Multiple enemies — tappable to select target
                Text("Tap an enemy to target  •  ${enemies.size} remaining", color = TextDim, fontSize = 10.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(enemies) { enemy ->
                        val isSelected = enemy.id == selectedTarget
                        SwarmEnemyCard(enemy, isSelected) {
                            if (!inFlight) Astral.battle.selectTarget(enemy.id)
                        }
                    }
                }
            }
            FloorKind.Boss -> {
                enemies.firstOrNull()?.let { boss ->
                    CombatantCard(boss, label = "Boss", reverse = true, isBoss = true)
                }
            }
            FloorKind.Normal -> {
                enemies.firstOrNull()?.let { enemy ->
                    CombatantCard(enemy, label = "Enemy", reverse = true)
                }
            }
        }

        // ── Battle log ──
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Log", color = TextFaint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF060606))
                    .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp)),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (log.isEmpty()) {
                    item { Text("Battle starting…", color = TextFaint, fontSize = 11.sp) }
                } else {
                    items(log) { LogLine(it) }
                }
            }
        }

        // ── Player card ──
        CombatantCard(player, label = "You", reverse = false, defending = isDefending)

        // ── Skill sub-menu ──
        AnimatedVisibility(visible = skillMenuOpen, enter = fadeIn(), exit = fadeOut()) {
            SkillSubMenu(skills, player.mp, inFlight) { sk ->
                Astral.battle.act("skill", skillId = sk.id)
            }
        }

        // ── Item sub-menu ──
        AnimatedVisibility(visible = itemMenuOpen, enter = fadeIn(), exit = fadeOut()) {
            ItemSubMenu(inventory, inFlight) { item ->
                Astral.battle.act("item", itemId = item.id)
            }
        }

        // ── Action bar ──
        val canFlee = floorKind != FloorKind.Boss
        ActionBar(inFlight, canFlee, skillMenuOpen, itemMenuOpen)
    }
}

/* ── Boss telegraphed attack panel ── */
@Composable
private fun BossTelegraphPanel(telegraph: BossTelegraph, timerSec: Int?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A0505))
            .border(0.5.dp, Color(0xFFE76E6E).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("⚠", fontSize = 16.sp)
        Column(Modifier.weight(1f)) {
            Text("Boss is preparing:", color = Color(0xFFE76E6E), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(telegraph.label.ifEmpty { "Unknown attack" }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        if (timerSec != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text("Act in", color = TextDim, fontSize = 10.sp)
                Text(
                    formatTimer(timerSec),
                    color = if (timerSec <= 30) Color(0xFFE76E6E) else Color(0xFFE7A56E),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        }
    }
}

/* ── Swarm enemy: tappable card ── */
@Composable
private fun SwarmEnemyCard(enemy: Combatant, selected: Boolean, onTap: () -> Unit) {
    val fraction = if (enemy.hp != null && enemy.maxHp != null && enemy.maxHp > 0)
        (enemy.hp.toFloat() / enemy.maxHp.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFF1A0A0A) else Color(0xFF060606))
            .border(
                1.dp,
                if (selected) Color(0xFFE76E6E) else CardBorder,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onTap)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF111111)),
            contentAlignment = Alignment.Center,
        ) { Text("👹", fontSize = 18.sp) }
        Text(enemy.name.ifEmpty { "Enemy" }, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
        // HP bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF1A1A1A)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFE76E6E)),
            )
        }
        if (selected) Text("◉ Target", color = Color(0xFFE76E6E), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

/* ── Combatant card (player or single enemy/boss) ── */
@Composable
private fun CombatantCard(
    c: Combatant,
    label: String,
    reverse: Boolean,
    isBoss: Boolean = false,
    defending: Boolean = false,
) {
    Row(
        modifier = cardModifier()
            .run { if (defending) border(1.dp, Primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp)) else this }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!reverse) Portrait(label, isBoss)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(c.name.ifEmpty { label }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (c.level != null) Text("Lv. ${c.level}", color = TextDim, fontSize = 11.sp)
                if (defending) Text("🛡 Defending", color = Primary, fontSize = 10.sp)
                if (isBoss) Text("★", color = Color(0xFFE76E6E), fontSize = 12.sp)
            }
            StatBar("HP", c.hp, c.maxHp, Color(0xFFE76E6E))
            if (c.maxMp != null) StatBar("MP", c.mp, c.maxMp, Color(0xFF6EA8E7))
        }
        if (reverse) Portrait(label, isBoss)
    }
}

@Composable
private fun Portrait(label: String, isBoss: Boolean) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isBoss) Color(0xFF1A0505) else Color(0xFF111111))
            .border(0.5.dp, if (isBoss) Color(0xFFE76E6E).copy(alpha = 0.4f) else CardBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (isBoss) "☠" else "👤", fontSize = 18.sp)
    }
}

@Composable
private fun StatBar(label: String, value: Int?, max: Int?, color: Color) {
    val fraction = if (value != null && max != null && max > 0)
        (value.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = TextDim, fontSize = 9.sp)
            Text(
                if (value != null && max != null) "$value / $max" else "—",
                color = TextFaint,
                fontSize = 9.sp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF1A1A1A)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
        }
    }
}

/* ── Skill sub-menu ── */
@Composable
private fun SkillSubMenu(skills: List<BattleSkill>, currentMp: Int?, inFlight: Boolean, onPick: (BattleSkill) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A0A14))
            .border(0.5.dp, Primary.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Choose a skill", color = TextDim, fontSize = 11.sp)
        if (skills.isEmpty()) {
            Text("No skills equipped — visit Skill Loadout.", color = TextFaint, fontSize = 11.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                skills.forEach { sk ->
                    val canAfford = currentMp == null || sk.mpCost == null || currentMp >= sk.mpCost
                    OutlinedButton(
                        onClick = { if (canAfford && !inFlight) onPick(sk) },
                        enabled = canAfford && !inFlight,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(sk.name.ifEmpty { "Skill" }, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            sk.mpCost?.let { Text("$it MP", fontSize = 9.sp, color = if (canAfford) Primary else Color(0xFFE76E6E)) }
                        }
                    }
                }
            }
        }
    }
}

/* ── Item sub-menu ── */
@Composable
private fun ItemSubMenu(inventory: List<InvItem>, inFlight: Boolean, onPick: (InvItem) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A0C0A))
            .border(0.5.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Use an item", color = TextDim, fontSize = 11.sp)
        if (inventory.isEmpty()) {
            Text("No usable items in inventory.", color = TextFaint, fontSize = 11.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(inventory) { item ->
                    OutlinedButton(
                        onClick = { if (!inFlight) onPick(item) },
                        enabled = !inFlight,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(item.name.ifEmpty { "Item" }, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/* ── Action bar ── */
@Composable
private fun ActionBar(
    inFlight: Boolean,
    canFlee: Boolean,
    skillMenuOpen: Boolean,
    itemMenuOpen: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Row 1: Attack + Skill + Defend
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { Astral.battle.act("attack") },
                enabled = !inFlight,
                modifier = Modifier.weight(1f).height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB32B2B)),
            ) {
                if (inFlight) CircularProgressIndicator(modifier = Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Attack", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = { Astral.battle.skillMenuOpen.value = !skillMenuOpen },
                enabled = !inFlight,
                modifier = Modifier.weight(1f).height(46.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (skillMenuOpen) Primary else CardBorder,
                ),
            ) { Text("Skill", fontSize = 13.sp, color = if (skillMenuOpen) Primary else Color.White) }
            OutlinedButton(
                onClick = { Astral.battle.act("defend") },
                enabled = !inFlight,
                modifier = Modifier.weight(1f).height(46.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.5f)),
            ) { Text("Defend", fontSize = 13.sp, color = Primary) }
        }
        // Row 2: Item + Flee
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { Astral.battle.itemMenuOpen.value = !itemMenuOpen },
                enabled = !inFlight,
                modifier = Modifier.weight(1f).height(40.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (itemMenuOpen) Gold else CardBorder,
                ),
            ) { Text("Item", fontSize = 12.sp, color = if (itemMenuOpen) Gold else Color.White) }
            OutlinedButton(
                onClick = { Astral.battle.act("flee") },
                enabled = canFlee && !inFlight,
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Text(
                    if (canFlee) "Flee" else "Can't flee",
                    fontSize = 12.sp,
                    color = if (canFlee) TextDim else TextFaint,
                )
            }
        }
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    Text(entry.text, color = TextDim, fontSize = 11.sp)
}

private fun formatTimer(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) "${m}:${s.toString().padStart(2, '0')}" else "${s}s"
}
