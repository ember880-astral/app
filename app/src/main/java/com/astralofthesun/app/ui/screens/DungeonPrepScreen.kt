package com.astralofthesun.app.ui.screens

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astralofthesun.app.data.Astral
import com.astralofthesun.app.data.DungeonInfo
import com.astralofthesun.app.data.InvItem
import com.astralofthesun.app.data.PartyMember
import com.astralofthesun.app.ui.components.EmptyNote
import com.astralofthesun.app.ui.components.SectionHeader
import com.astralofthesun.app.ui.components.cardModifier
import com.astralofthesun.app.ui.theme.CardBorder
import com.astralofthesun.app.ui.theme.Primary
import com.astralofthesun.app.ui.theme.TextDim
import com.astralofthesun.app.ui.theme.TextFaint

/* Dungeon Prep — shown after picking a dungeon from Home, before the
   battle field. Player reviews readiness, loadout, consumables and
   party, then taps Enter Dungeon to move into DungeonBattleScreen.
   Ships empty-by-design: every section renders an EmptyNote until the
   backend hydrates Astral's dungeon-prep state. */
@Composable
fun DungeonPrepScreen(onEnterDungeon: () -> Unit, onBack: () -> Unit) {
    val stats by Astral.stats
    val player by Astral.player
    val dungeon by Astral.selectedDungeon
    val loadout by Astral.loadout
    val inventory = Astral.inventory
    val party = Astral.party

    val ready = dungeon != null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "← Back",
                    color = TextDim,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { onBack() },
                )
            }
        }

        // dungeon info card
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Dungeon", "")
                DungeonCard(dungeon)
            }
        }

        // player readiness
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Your Readiness", "")
                Row(
                    modifier = cardModifier().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    ReadinessStat("Level", stats.level?.toString() ?: "—")
                    ReadinessStat("Class", player.sub.ifEmpty { "—" })
                    ReadinessStat("HP", "—")
                    ReadinessStat("MP", "—")
                }
            }
        }

        // loadout slots
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Loadout", "")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LoadoutSlot("Weapon", loadout.weapon, Modifier.weight(1f))
                    LoadoutSlot("Armor", loadout.armor, Modifier.weight(1f))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LoadoutSlot("Relic", loadout.relic, Modifier.weight(1f))
                LoadoutSlot("Consumable", loadout.consumable, Modifier.weight(1f))
            }
        }

        // inventory quick-pick
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Inventory", "${inventory.size} items")
                if (inventory.isEmpty()) {
                    EmptyNote("Inventory is empty")
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(inventory) { InventoryChip(it) }
                    }
                }
            }
        }

        // party / raid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Party", "${party.size} joining")
                if (party.isEmpty()) {
                    EmptyNote("Solo run — no party members yet")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        party.forEach { PartyRow(it) }
                    }
                }
            }
        }

        // enter button
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onEnterDungeon,
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text("Enter Dungeon", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                if (!ready) {
                    Text(
                        "Select a dungeon to continue",
                        color = TextFaint,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DungeonCard(dungeon: DungeonInfo?) {
    if (dungeon == null) {
        EmptyNote("No dungeon selected")
        return
    }
    Column(
        modifier = cardModifier().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF111111)),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(dungeon.name.ifEmpty { "Dungeon" }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    listOfNotNull(
                        dungeon.floor?.let { "Floor $it" },
                        dungeon.difficulty.ifEmpty { null },
                    ).joinToString(" · ").ifEmpty { "—" },
                    color = TextDim,
                    fontSize = 12.sp,
                )
            }
        }
        Text(
            "Recommended level: " + (dungeon.recommendedLevel?.toString() ?: "—"),
            color = TextDim,
            fontSize = 12.sp,
        )
        if (dungeon.rewardsPreview.isNotEmpty()) {
            Text(dungeon.rewardsPreview, color = TextFaint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ReadinessStat(label: String, value: String) {
    Column {
        Text(label, color = TextDim, fontSize = 10.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun LoadoutSlot(label: String, item: InvItem?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF060606))
            .border(0.5.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = TextDim, fontSize = 10.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF111111)),
        )
        Text(item?.name?.ifEmpty { null } ?: "Empty", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InventoryChip(item: InvItem) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF060606))
            .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF111111)),
        )
        Text(item.name.ifEmpty { "Item" }, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun PartyRow(member: PartyMember) {
    Row(
        modifier = cardModifier().padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF111111)),
        )
        Column(Modifier.weight(1f)) {
            Text(member.name.ifEmpty { "Player" }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("Lv. " + (member.level?.toString() ?: "—"), color = TextDim, fontSize = 11.sp)
        }
        Text(
            if (member.ready) "Ready" else "Not ready",
            color = if (member.ready) Color(0xFF6EE787) else TextFaint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
