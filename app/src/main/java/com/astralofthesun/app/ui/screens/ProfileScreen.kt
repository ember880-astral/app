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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.astralofthesun.app.ui.theme.TextDim

@Composable
fun ProfileScreen() {
    val player by Astral.player
    val stats by Astral.stats
    val wallet by Astral.wallet

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // banner + overlapping pfp
        item {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0A0A0A))
                        .border(0.5.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp)),
                )
                Row(
                    modifier = Modifier.offset(y = (-22).dp).padding(start = 14.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF111111))
                            .border(1.dp, Color(0x33FFFFFF), CircleShape),
                    )
                    Column {
                        Text(player.name.ifEmpty { "Player" }, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text(player.sub.ifEmpty { "—" }, color = TextDim, fontSize = 12.sp)
                    }
                }
            }
        }

        // stats
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Level ${stats.level?.toString() ?: "—"}", fontSize = 13.sp)
            }
        }

        // wallet
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Wallet")
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Coin(14); Text(wallet.solars?.toString() ?: "0", fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Gem(14); Text(wallet.gems?.toString() ?: "0", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // inventory / chest / pvp
        item { SectionHeader("Inventory", "${Astral.inventory.size} items") }
        item { if (Astral.inventory.isEmpty()) EmptyNote("Inventory is empty") }

        item { SectionHeader("Chest", "${Astral.vault.size}") }
        item { if (Astral.vault.isEmpty()) EmptyNote("Chest is empty") }

        item { SectionHeader("PvP Loadout", "${Astral.pvp.size}") }
        item { if (Astral.pvp.isEmpty()) EmptyNote("No PvP loadout") }
    }
}
