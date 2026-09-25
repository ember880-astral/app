package com.astralofthesun.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import com.astralofthesun.app.ui.components.Coin
import com.astralofthesun.app.ui.components.EmptyNote
import com.astralofthesun.app.ui.components.Gem
import com.astralofthesun.app.ui.components.SectionHeader
import com.astralofthesun.app.ui.theme.Primary
import com.astralofthesun.app.ui.theme.TextDim

@Composable
fun HomeScreen(goTopUp: (String) -> Unit, goDungeon: () -> Unit) {
    val player by Astral.player
    val stats by Astral.stats
    val shop = Astral.shop
    val roster = Astral.roster
    val dungeons = Astral.dungeons
    val friends = Astral.friends

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // profile header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111111))
                        .border(0.5.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(player.name.ifEmpty { "Player" }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(player.sub.ifEmpty { "—" }, color = TextDim, fontSize = 12.sp)
                }
            }
        }

        // stats row
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Level", stats.level?.toString() ?: "—", null)
                StatCard("Solars", stats.solars?.toString() ?: "0", "coin")
                StatCard("Gems", stats.gems?.toString() ?: "0", "gem")
            }
        }

        // hero banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0A0A0A))
                    .border(0.5.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp)),
            )
        }

        // top-up buttons (deep-link with currency preselected)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Solars & Gems Top-up", "Top-up")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { goTopUp("solars") }, modifier = Modifier.weight(1f)) {
                        Text("Solars Top-up")
                    }
                    Button(
                        onClick = { goTopUp("gems") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) { Text("Gems Top-up") }
                }
            }
        }

        // shop highlights
        item { SectionHeader("Shop", "${shop.size} items") }
        item {
            if (shop.isEmpty()) EmptyNote("No items in the shop")
            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shop.forEach { Text(it.name, fontSize = 13.sp) }
            }
        }

        // roster
        item { SectionHeader("Pokémon Roster", "${roster.size} ready") }
        item { if (roster.isEmpty()) EmptyNote("No Pokémon ready") }

        // dungeons
        item { SectionHeader("Dungeons & Runs", "${dungeons.size} available") }
        item {
            if (dungeons.isEmpty()) {
                EmptyNote("No dungeons available")
            } else {
                Button(
                    onClick = goDungeon,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) { Text("Prepare for Dungeon") }
            }
        }

        // friends
        item { SectionHeader("Friends", "${friends.size}") }
        item { if (friends.isEmpty()) EmptyNote("No friends yet") }
    }
}

@Composable
private fun RowScope.StatCard(label: String, value: String, icon: String?) {
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF060606))
            .border(0.5.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column {
            Text(label, color = TextDim, fontSize = 10.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (icon) {
                    "coin" -> Coin(12)
                    "gem" -> Gem(12)
                }
                Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
