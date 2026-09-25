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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.astralofthesun.app.network.Repository
import com.astralofthesun.app.network.userMessage
import com.astralofthesun.app.ui.components.AstralImage
import com.astralofthesun.app.ui.components.BannerTone
import com.astralofthesun.app.ui.components.Notice
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astralofthesun.app.data.Astral
import com.astralofthesun.app.data.LocationEntry
import com.astralofthesun.app.ui.components.cardModifier
import com.astralofthesun.app.ui.theme.CardBorder
import com.astralofthesun.app.ui.theme.Gold
import com.astralofthesun.app.ui.theme.Primary
import com.astralofthesun.app.ui.theme.TextDim
import com.astralofthesun.app.ui.theme.TextFaint

/* ── Dungeon Detail ───────────────────────────────────────────────────
   Shown after tapping a dungeon on the World Map, before entering.
   Displays full info, floor map with boss markers, checkpoint status,
   and the run cost. The Enter button is the commitment point.
   ──────────────────────────────────────────────────────────────────── */
@Composable
fun DungeonDetailScreen(location: LocationEntry, onEntered: () -> Unit, onBack: () -> Unit) {
    val limits by Astral.dungeonLimits
    val scope = rememberCoroutineScope()
    var entering by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    /** The server spends the Run and opens the fight; we only move on if it said yes. */
    fun enter() {
        if (entering) return
        entering = true
        error = null
        scope.launch {
            Repository.enterDungeon(location.id)
                .onSuccess { onEntered() }
                .onFailure { error = it.userMessage() }
            entering = false
        }
    }

    val runsLeft = if (location.isNewcomer)
        limits.newcomerRunsMax - limits.newcomerRunsUsed
    else
        limits.runsMax - limits.runsUsed
    val canEnter = runsLeft > 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Back
        item {
            Text(
                "← World Map",
                color = TextDim,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(bottom = 4.dp),
            )
        }

        // Big art banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0A0A0A))
                    .border(0.5.dp, CardBorder, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.BottomStart,
            ) {
                // Location art from the server; ⚔ shows until it loads (or if none is set)
                AstralImage(url = location.art, glyph = "⚔", corner = 0, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC000000))
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(location.name.ifEmpty { "Dungeon" }, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        if (location.levelRange.isNotEmpty()) {
                            Text("Recommended: Lv. ${location.levelRange}", color = TextDim, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Description
        if (location.description.isNotEmpty()) {
            item {
                Text(location.description, color = TextDim, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }

        // Floor info + boss markers
        if (location.totalFloors != null) {
            item {
                Column(
                    modifier = cardModifier().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatPair("Floors", "${location.totalFloors}")
                        StatPair("Bosses", "${location.bossFloors.size}")
                        location.checkpointInterval?.let {
                            StatPair("Checkpoint", "Every $it")
                        }
                        location.currentFloor?.let {
                            StatPair("Saved at", "Floor $it", Gold)
                        }
                    }

                    if (location.totalFloors > 0) {
                        Text("Floor map", color = TextDim, fontSize = 11.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed((1..location.totalFloors).toList()) { _, floor ->
                                val isBoss = floor in location.bossFloors
                                val isCheckpoint = floor == location.currentFloor
                                FloorDot(floor, isBoss, isCheckpoint)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            LegendDot(Color(0xFFE76E6E), "Boss floor")
                            LegendDot(Gold, "Your checkpoint")
                            LegendDot(Color(0xFF333333), "Floor")
                        }
                    }
                }
            }
        }

        // Run cost callout
        item {
            Row(
                modifier = cardModifier().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Entry cost", color = TextDim, fontSize = 11.sp)
                    Text("1 Run", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "$runsLeft run${if (runsLeft != 1) "s" else ""} remaining today",
                        color = if (runsLeft == 0) Color(0xFFE76E6E)
                                else if (runsLeft == 1) Color(0xFFE7A56E)
                                else TextFaint,
                        fontSize = 11.sp,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text("Stamina", color = TextDim, fontSize = 11.sp)
                    Text("1 / fight inside", color = TextFaint, fontSize = 11.sp)
                }
            }
        }

        // Checkpoint notice
        if (location.currentFloor != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0D0900))
                        .border(0.5.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("📍", fontSize = 16.sp)
                    Column {
                        Text("Checkpoint saved", color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("You'll resume at Floor ${location.currentFloor}", color = TextDim, fontSize = 11.sp)
                    }
                }
            }
        }

        // Buttons
        item {
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Notice(it, BannerTone.Error) }
                Button(
                    onClick = { enter() },
                    enabled = canEnter && !entering,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    if (entering) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            if (location.currentFloor != null) "Resume at Floor ${location.currentFloor}"
                            else "Enter Dungeon",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
                if (!canEnter) {
                    Text(
                        "No runs left today — come back after the daily reset.",
                        color = Color(0xFFE76E6E),
                        fontSize = 11.sp,
                    )
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) { Text("Back", color = TextDim) }
            }
        }
    }
}

@Composable
private fun StatPair(label: String, value: String, valueColor: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextDim, fontSize = 10.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun FloorDot(floor: Int, isBoss: Boolean, isCheckpoint: Boolean) {
    val color = when {
        isCheckpoint -> Gold
        isBoss -> Color(0xFFE76E6E)
        else -> Color(0xFF2A2A2A)
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .border(0.5.dp, CardBorder, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (isBoss) "B" else floor.toString(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (color == Gold) Color.Black else Color.White,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Text(label, color = TextFaint, fontSize = 9.sp)
    }
}
