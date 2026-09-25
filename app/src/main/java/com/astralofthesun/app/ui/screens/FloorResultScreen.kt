package com.astralofthesun.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astralofthesun.app.data.FloorResult
import com.astralofthesun.app.data.LootItem
import com.astralofthesun.app.data.Skill
import com.astralofthesun.app.ui.components.cardModifier
import com.astralofthesun.app.ui.theme.CardBorder
import com.astralofthesun.app.ui.theme.Gold
import com.astralofthesun.app.ui.theme.Primary
import com.astralofthesun.app.ui.theme.TextDim
import com.astralofthesun.app.ui.theme.TextFaint

/* ── Floor Result Screen ──────────────────────────────────────────────
   Two very different outcomes:

   VICTORY — XP, loot, level-up, new skill. Two buttons:
     • Next Floor   (continues the run, costs 1 Stamina next fight)
     • Leave Dungeon (saves checkpoint, ends the run — run cost already
                      spent on entry, leaving is free)

   DEATH — The gear-loss screen. This is a real setback, not a retry.
     • Shows exactly what was lost
     • Shows if Premium auto-revive or a held item (Totem etc.) saved gear
     • One button: Return to Town. No "try again". The run is over.
   ──────────────────────────────────────────────────────────────────── */
@Composable
fun FloorResultScreen(
    result: FloorResult,
    onNextFloor: () -> Unit,
    onLeaveDungeon: () -> Unit,
    onReturnToTown: () -> Unit,
) {
    if (result.victory) {
        VictoryScreen(result, onNextFloor, onLeaveDungeon)
    } else {
        DeathScreen(result, onReturnToTown)
    }
}

/* ── Victory ── */
@Composable
private fun VictoryScreen(result: FloorResult, onNextFloor: () -> Unit, onLeave: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF000000)),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (result.isLastFloor) "Dungeon Cleared!" else "Floor Cleared",
                    fontSize = if (result.isLastFloor) 26.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (result.isLastFloor) Gold else Color(0xFF6EE787),
                )
                result.floor?.let {
                    Text(
                        if (result.isLastFloor) "All floors complete" else "Floor $it complete",
                        color = TextDim,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // XP + level up
        item {
            Column(
                modifier = cardModifier().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                result.xpGained?.let { xp ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("XP gained", color = TextDim, fontSize = 13.sp)
                        Text("+$xp XP", color = Color(0xFF6EE787), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                if (result.leveledUp && result.newLevel != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Gold.copy(alpha = 0.12f))
                            .border(0.5.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("✦", color = Gold, fontSize = 18.sp)
                            Text(
                                "Level Up! Now Lv. ${result.newLevel}",
                                color = Gold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                            Text("✦", color = Gold, fontSize = 18.sp)
                        }
                    }
                }
            }
        }

        // Loot
        if (result.loot.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Loot", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(result.loot) { LootChip(it) }
                    }
                }
            }
        }

        // New skill
        result.newSkillUnlocked?.let { sk ->
            item { NewSkillCard(sk) }
        }

        // Leave hint
        item {
            Text(
                "Leaving saves your checkpoint for free — the run is already spent.",
                color = TextFaint,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!result.isLastFloor) {
                    Button(
                        onClick = onNextFloor,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text("Next Floor", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                OutlinedButton(
                    onClick = onLeave,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) {
                    Text(
                        if (result.isLastFloor) "Return to Town" else "Leave Dungeon",
                        color = TextDim,
                    )
                }
            }
        }
    }
}

/* ── Death ── */
@Composable
private fun DeathScreen(result: FloorResult, onReturnToTown: () -> Unit) {
    val gearSaved = result.savedByPremiumRevive || result.savedByItem != null
    val partialLoss = gearSaved && result.gearLost.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0000)),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Death header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("☠", fontSize = 48.sp)
                    Text(
                        "You Died",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE76E6E),
                    )
                    result.floor?.let {
                        Text("Fell on Floor $it", color = TextDim, fontSize = 13.sp)
                    }
                }
            }

            // Saved by something?
            if (gearSaved) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0A0D0A))
                            .border(0.5.dp, Color(0xFF6EE787).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🛡", fontSize = 20.sp)
                        Column {
                            when {
                                result.savedByPremiumRevive ->
                                    Text("Premium auto-revive used", color = Color(0xFF6EE787), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                result.savedByItem != null ->
                                    Text("${result.savedByItem} protected you", color = Color(0xFF6EE787), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text("Your equipped gear was not lost.", color = TextDim, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Gear lost
            if (result.gearLost.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF140000))
                            .border(0.5.dp, Color(0xFFE76E6E).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Gear lost", color = Color(0xFFE76E6E), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "This gear has been removed from your inventory.",
                            color = TextFaint,
                            fontSize = 11.sp,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(result.gearLost) { LostGearChip(it) }
                        }
                    }
                }
            }

            // No gear saved, no gear lost — full loss
            if (!gearSaved && result.gearLost.isEmpty()) {
                item {
                    Column(
                        modifier = cardModifier().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Equipped gear lost", color = Color(0xFFE76E6E), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "Your equipped items were lost on death. Gear in your bag is safe.",
                            color = TextDim,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // How to prevent this next time
            item {
                Column(
                    modifier = cardModifier().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Protect yourself next time", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("• Premium members get one free revive per day that saves gear.", color = TextFaint, fontSize = 11.sp)
                    Text("• Hold a Totem or other protective item to prevent gear loss on death.", color = TextFaint, fontSize = 11.sp)
                }
            }
        }

        // Sticky bottom: single button, the only way out
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0000))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Your run ends here. There is no retry.",
                color = TextFaint,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onReturnToTown,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A0A0A)),
            ) {
                Text("Return to Town", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }
        }
    }
}

/* ── Sub-components ── */

@Composable
private fun LootChip(item: LootItem) {
    Column(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A0A0A))
            .border(0.5.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF111111)),
        )
        Text(item.name.ifEmpty { "Item" }, fontSize = 9.sp, maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LostGearChip(item: LootItem) {
    Column(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A0505))
            .border(0.5.dp, Color(0xFFE76E6E).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A0A0A)),
            contentAlignment = Alignment.Center,
        ) { Text("🗡", fontSize = 14.sp) }
        Text(item.name.ifEmpty { "Gear" }, fontSize = 9.sp, maxLines = 2, textAlign = TextAlign.Center, color = Color(0xFFE76E6E))
    }
}

@Composable
private fun NewSkillCard(skill: Skill) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A0A1A))
            .border(0.5.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✦", color = Primary, fontSize = 20.sp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("New Skill Unlocked!", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(skill.name.ifEmpty { "Skill" }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (skill.effect.isNotEmpty()) Text(skill.effect, color = TextDim, fontSize = 11.sp)
        }
        skill.mpCost?.let {
            Text("$it MP", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
