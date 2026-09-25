package com.astralofthesun.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.astralofthesun.app.network.Repository
import com.astralofthesun.app.network.isNotLive
import com.astralofthesun.app.network.userMessage
import com.astralofthesun.app.ui.components.BannerTone
import com.astralofthesun.app.ui.components.Notice
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astralofthesun.app.data.Astral
import com.astralofthesun.app.data.Skill
import com.astralofthesun.app.data.SkillLoadout
import com.astralofthesun.app.ui.components.EmptyNote
import com.astralofthesun.app.ui.components.SectionHeader
import com.astralofthesun.app.ui.components.cardModifier
import com.astralofthesun.app.ui.theme.CardBorder
import com.astralofthesun.app.ui.theme.Primary
import com.astralofthesun.app.ui.theme.TextDim
import com.astralofthesun.app.ui.theme.TextFaint

/* ── Skill Loadout ────────────────────────────────────────────────────
   4 equipped skill slots at the top. Below: the player's full skill
   pool (greyed if already equipped). Tap a slot or an unequipped skill
   to open the inline picker. Not forced on every run — reachable from
   the map header or a menu.
   ──────────────────────────────────────────────────────────────────── */
@Composable
fun SkillLoadoutScreen(onBack: () -> Unit) {
    val loadout by Astral.skillLoadout
    val pool = Astral.skillPool

    // Which slot index (0–3) the player is currently picking for; null = no picker open
    var editingSlot by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        Repository.loadSkills().onFailure { message = if (it.isNotLive) "Skill loadout isn't live on the server yet." else it.userMessage() }
    }

    /** Equip / Unequip are server calls — the slots re-render from the server's answer. */
    fun send(block: suspend () -> Result<Unit>) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            block().onFailure { message = it.userMessage() }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("← Back", color = TextDim, fontSize = 13.sp, modifier = Modifier.clickable { onBack() })
            Text("Skill Loadout", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }

        message?.let {
            Notice(it, BannerTone.Warning, modifier = Modifier.padding(horizontal = 16.dp)) { message = null }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── 4 equipped slots ──
            item {
                SectionHeader("Equipped Skills", "4 slots")
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    loadout.toList().forEachIndexed { idx, skill ->
                        EquippedSlotCard(
                            slot = idx,
                            skill = skill,
                            active = editingSlot == idx,
                            onTap = { editingSlot = if (editingSlot == idx) null else idx },
                            onUnequip = {
                                if (editingSlot == idx) editingSlot = null
                                send { Repository.unequipSkill(idx) }
                            },
                        )
                    }
                }
            }

            // ── Inline picker: shown when a slot is tapped ──
            if (editingSlot != null) {
                item {
                    val slot = editingSlot!!
                    val equipped = loadout.equippedIds()
                    val available = pool.filter { it.unlocked && it.id !in equipped }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0A0A14))
                            .border(0.5.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Pick a skill for Slot ${slot + 1}",
                            color = TextDim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (available.isEmpty()) {
                            Text("All unlocked skills are already equipped.", color = TextFaint, fontSize = 12.sp)
                        } else {
                            available.forEach { sk ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF111122))
                                        .clickable(enabled = !busy) {
                                            editingSlot = null
                                            send { Repository.equipSkill(slot, sk.id) }
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(sk.name.ifEmpty { "Skill" }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        if (sk.effect.isNotEmpty()) Text(sk.effect, color = TextDim, fontSize = 11.sp)
                                    }
                                    sk.mpCost?.let {
                                        Text("$it MP", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Full skill pool ──
            item {
                SectionHeader("All Skills", "${pool.size} unlocked")
            }

            if (pool.isEmpty()) {
                item { EmptyNote("No skills unlocked yet") }
            } else {
                val equipped = loadout.equippedIds()
                items(pool) { sk ->
                    SkillPoolRow(skill = sk, isEquipped = sk.id in equipped)
                }
            }
        }
    }
}

/* ── One equipped slot card ── */
@Composable
private fun EquippedSlotCard(
    slot: Int,
    skill: Skill?,
    active: Boolean,
    onTap: () -> Unit,
    onUnequip: () -> Unit,
) {
    val borderColor = when {
        active -> Primary
        skill != null -> CardBorder
        else -> Color(0x14FFFFFF)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Color(0xFF0A0A18) else Color(0xFF060606))
            .border(0.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onTap() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Slot number badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (skill != null) Primary.copy(alpha = 0.18f) else Color(0xFF111111))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("${slot + 1}", color = if (skill != null) Primary else TextFaint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        if (skill == null) {
            Text("Tap to equip a skill", color = TextFaint, fontSize = 13.sp, modifier = Modifier.weight(1f))
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(skill.name.ifEmpty { "Skill" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (skill.effect.isNotEmpty()) Text(skill.effect, color = TextDim, fontSize = 11.sp)
            }
            skill.mpCost?.let {
                Text("$it MP", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "✕",
                color = TextFaint,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onUnequip),
            )
        }
    }
}

/* ── One row in the skill pool ── */
@Composable
private fun SkillPoolRow(skill: Skill, isEquipped: Boolean) {
    Row(
        modifier = cardModifier()
            .padding(12.dp)
            .run { if (isEquipped) this else this },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    skill.name.ifEmpty { "Skill" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (isEquipped) TextDim else Color.White,
                )
                if (isEquipped) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Primary.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("Equipped", color = Primary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (skill.effect.isNotEmpty()) {
                Text(skill.effect, color = TextFaint, fontSize = 11.sp)
            }
        }
        skill.mpCost?.let {
            Text(
                "$it MP",
                color = if (isEquipped) TextFaint else Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
