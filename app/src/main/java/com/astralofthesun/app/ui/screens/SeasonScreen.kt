package com.astralofthesun.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astralofthesun.app.data.Astral
import com.astralofthesun.app.ui.components.EmptyNote
import com.astralofthesun.app.ui.components.SectionHeader
import com.astralofthesun.app.ui.components.cardModifier
import com.astralofthesun.app.ui.theme.Gold
import com.astralofthesun.app.ui.theme.TextDim

@Composable
fun SeasonScreen() {
    val season by Astral.season
    var premium by remember { mutableStateOf(false) }

    val tiers = season.tiers.filter { if (premium) it.side == "premium" else it.side != "premium" }
    val xpPct = if (season.xpNeeded > 0) (season.xpCurrent.toFloat() / season.xpNeeded).coerceIn(0f, 1f) else 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("Season", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("${season.title} ${season.duration}".trim(), color = TextDim, fontSize = 12.sp)
            }
        }

        // banner placeholder
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0A0A0A))
                    .border(0.5.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp)),
            )
        }

        // battle pass card
        item {
            Column(modifier = cardModifier().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tier ${season.tierIndex} / ${season.tierCount}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    Text("${season.xpCurrent} / ${season.xpNeeded} XP", color = TextDim, fontSize = 11.sp)
                }
                // xp bar
                Box(
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                        .clip(RoundedCornerShape(999.dp)).background(Color(0x14FFFFFF))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(fraction = xpPct).height(8.dp)
                            .clip(RoundedCornerShape(999.dp)).background(Gold)
                    )
                }
                // free / premium tabs
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabChip("Free", !premium) { premium = false }
                    TabChip("Premium", premium) { premium = true }
                }
                // track
                if (tiers.isEmpty()) EmptyNote("No rewards yet")
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiers.forEach { t ->
                        Text(
                            "${if (t.side == "premium") "PREMIUM" else "FREE"} · ${t.tier ?: ""} ${t.title}",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        // reward strip
        item { SectionHeader("Season Rewards", "${season.rewards.size} rewards") }
        item { if (season.rewards.isEmpty()) EmptyNote("No rewards yet") }

        // characters
        item { SectionHeader("Season Characters", "${season.characters.size} available") }
        item { if (season.characters.isEmpty()) EmptyNote("No season characters") }
    }
}

@Composable
private fun RowScope.TabChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Color.White else Color.Transparent)
            .border(0.5.dp, if (active) Color.White else Color(0x1FFFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color(0xFF0A0A0A) else Color(0x80FFFFFF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
