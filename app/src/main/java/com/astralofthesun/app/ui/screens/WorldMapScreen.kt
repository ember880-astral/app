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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedButton
import com.astralofthesun.app.network.Repository
import com.astralofthesun.app.network.isNotLive
import com.astralofthesun.app.network.userMessage
import com.astralofthesun.app.ui.components.NotLiveNote
import com.astralofthesun.app.ui.components.Notice
import com.astralofthesun.app.ui.components.BannerTone
import com.astralofthesun.app.ui.components.fmtDuration
import com.astralofthesun.app.ui.components.nextLocalMidnight
import com.astralofthesun.app.ui.components.rememberNow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astralofthesun.app.data.Astral
import com.astralofthesun.app.data.DungeonLimits
import com.astralofthesun.app.data.LocationEntry
import com.astralofthesun.app.ui.components.EmptyNote
import com.astralofthesun.app.ui.components.SectionHeader
import com.astralofthesun.app.ui.components.cardModifier
import com.astralofthesun.app.ui.theme.CardBorder
import com.astralofthesun.app.ui.theme.Gold
import com.astralofthesun.app.ui.theme.Primary
import com.astralofthesun.app.ui.theme.TextDim
import com.astralofthesun.app.ui.theme.TextFaint

/* ── World Map ────────────────────────────────────────────────────────
   Shows all locations (towns + dungeons). Tapping an unlocked dungeon
   opens Dungeon Detail. Locked entries show exactly what they require.
   The top bar shows Runs and Stamina separately — they are never the
   same number and must not be merged.
   ──────────────────────────────────────────────────────────────────── */
@Composable
fun WorldMapScreen(onSelectLocation: (LocationEntry) -> Unit, onSkills: () -> Unit = {}) {
    val limits by Astral.dungeonLimits
    val locations = Astral.locations
    val stats by Astral.stats

    // Always re-read the map from the server on open (checkpoints / limits change after every run).
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(Unit) {
        Repository.loadWorld().onFailure { error = it }.onSuccess { error = null }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {

        // ── Header: title + Skill Loadout shortcut ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("World Map", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onSkills, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text("Skills", fontSize = 12.sp)
            }
        }

        // ── Top resource bar ──
        ResourceBar(limits)

        // ── Location list ──
        if (locations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                val e = error
                when {
                    loading -> EmptyNote("Loading the map…")
                    e != null && e.isNotLive -> NotLiveNote("The world map")
                    e != null -> Notice(e.userMessage(), BannerTone.Error)
                    else -> EmptyNote("No locations yet.")
                }
            }
            return@Column
        }

        // Newcomer's Hollow callout for new/low-level players
        val playerLevel = stats.level ?: 0
        val newcomer = locations.firstOrNull { it.isNewcomer && it.unlocked }
        if (newcomer != null && playerLevel < 15) {
            NewcomerCallout(newcomer, limits) { onSelectLocation(it) }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Separate towns and dungeons
            val towns = locations.filter { it.isTown }
            val dungeons = locations.filter { !it.isTown }

            if (towns.isNotEmpty()) {
                item { SectionHeader("Towns", "${towns.size} locations") }
                items(towns) { LocationCard(it, limits, onSelectLocation) }
            }

            if (dungeons.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    SectionHeader("Dungeons", "${dungeons.size} locations")
                }
                items(dungeons) { LocationCard(it, limits, onSelectLocation) }
            }
        }
    }
}

/* ── Resource bar at the top ──
   Runs and Stamina are two different pools and are always shown apart.
   Both reset at local midnight; the countdown ticks live. */
@Composable
private fun ResourceBar(limits: DungeonLimits) {
    val now = rememberNow()
    val resetAt = limits.resetAt ?: nextLocalMidnight(now)
    val resetLabel = "Resets in " + fmtDuration(resetAt - now)
    val runsLeft = (limits.runsMax - limits.runsUsed).coerceAtLeast(0)
    val staminaLeft = (limits.staminaMax - limits.staminaUsed).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF050508))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PoolCard(
                label = "Runs",
                value = "$runsLeft/${limits.runsMax}",
                hint = "1 per dungeon entry" + if (limits.isPremium) " · Premium" else " · 20 with Premium",
                color = if (runsLeft == 0) Color(0xFFE76E6E) else Color.White,
                modifier = Modifier.weight(1f),
            )
            PoolCard(
                label = "Stamina",
                value = "$staminaLeft/${limits.staminaMax}",
                hint = "1 per fight",
                color = when {
                    staminaLeft == 0 -> Color(0xFFE76E6E)
                    staminaLeft < limits.staminaMax * 0.3 -> Color(0xFFE7A56E)
                    else -> Color.White
                },
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Both reset at local midnight", color = TextFaint, fontSize = 10.sp)
            Text(resetLabel, color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PoolCard(label: String, value: String, hint: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B0B10))
            .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Text(label, color = TextDim, fontSize = 10.sp)
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(hint, color = TextFaint, fontSize = 9.sp)
    }
}

/* ── Newcomer's Hollow spotlight card ── */
@Composable
private fun NewcomerCallout(loc: LocationEntry, limits: DungeonLimits, onClick: (LocationEntry) -> Unit) {
    val newcomerRuns = limits.newcomerRunsMax - limits.newcomerRunsUsed
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0C1A))
            .border(0.dp, Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF6EE787)),
            )
            Text("New adventurer? Start here →", color = Color(0xFF6EE787), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(
            modifier = cardModifier()
                .clickable { onClick(loc) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LocationArt(loc)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(loc.name.ifEmpty { "Newcomer's Hollow" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Easy lane for new players · Lv. ${loc.levelRange.ifEmpty { "1–10" }}", color = TextDim, fontSize = 11.sp)
                Text(
                    "Own daily floor allowance: $newcomerRuns/${limits.newcomerRunsMax} left",
                    color = if (newcomerRuns == 0) Color(0xFFE76E6E) else TextFaint,
                    fontSize = 10.sp,
                )
            }
            EnterButton(loc, newcomerRuns > 0, onClick)
        }
    }
}

/* ── One location card in the list ── */
@Composable
private fun LocationCard(loc: LocationEntry, limits: DungeonLimits, onSelect: (LocationEntry) -> Unit) {
    val runsLeft = limits.runsMax - limits.runsUsed

    Row(
        modifier = (if (loc.unlocked) cardModifier().clickable { onSelect(loc) } else cardModifier()).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LocationArt(loc, locked = !loc.unlocked)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    loc.name.ifEmpty { "—" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (loc.unlocked) Color.White else TextDim,
                )
                if (loc.isTown) {
                    Chip("Town", Color(0xFF6EE787))
                }
                if (!loc.unlocked) {
                    Chip("Locked", TextFaint)
                }
            }

            if (loc.levelRange.isNotEmpty()) {
                Text("Lv. ${loc.levelRange}", color = TextDim, fontSize = 11.sp)
            }

            // Checkpoint on a dungeon already started
            if (!loc.isTown && loc.unlocked && loc.currentFloor != null) {
                Text(
                    "Checkpoint: Floor ${loc.currentFloor}" +
                        (loc.totalFloors?.let { " / $it" } ?: ""),
                    color = Gold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Lock reason
            if (!loc.unlocked) {
                val reason = when {
                    loc.prerequisiteName != null -> "Clear ${loc.prerequisiteName} first"
                    loc.prerequisiteId != null -> "Clear the prerequisite dungeon first"
                    loc.prerequisiteLevel != null -> "Reach Lv. ${loc.prerequisiteLevel} to unlock"
                    else -> "Locked"
                }
                Text(reason, color = TextFaint, fontSize = 10.sp)
            }
        }

        if (loc.isTown) {
            // Towns are always free to enter
            Button(
                onClick = { onSelect(loc) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2A1A)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) { Text("Enter", fontSize = 12.sp, color = Color(0xFF6EE787)) }
        } else {
            val canEnter = if (loc.isNewcomer) limits.newcomerRunsMax - limits.newcomerRunsUsed > 0 else runsLeft > 0
            EnterButton(loc, canEnter, onSelect)
        }
    }
}

/* ── Enter / Resume / Locked button ── */
@Composable
private fun EnterButton(loc: LocationEntry, canEnter: Boolean, onClick: (LocationEntry) -> Unit) {
    when {
        !loc.unlocked -> {
            Text(
                loc.prerequisiteLevel?.let { "Lv. $it" } ?: "Locked",
                color = TextFaint,
                fontSize = 11.sp,
            )
        }
        !canEnter -> {
            Text("No runs left", color = Color(0xFFE76E6E), fontSize = 11.sp)
        }
        loc.currentFloor != null -> {
            Button(
                onClick = { onClick(loc) },
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) { Text("Resume", fontSize = 12.sp, color = Color(0xFF000000), fontWeight = FontWeight.Bold) }
        }
        else -> {
            Button(
                onClick = { onClick(loc) },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) { Text("Enter", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/* ── Art placeholder ── */
@Composable
private fun LocationArt(loc: LocationEntry, locked: Boolean = false) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    locked -> Color(0xFF0A0A0A)
                    loc.isTown -> Color(0xFF0D1A0D)
                    loc.isNewcomer -> Color(0xFF0A0D1A)
                    else -> Color(0xFF111111)
                }
            )
            .border(0.5.dp, if (locked) Color(0x14FFFFFF) else CardBorder, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (locked) {
            Text("🔒", fontSize = 18.sp)
        } else if (loc.isTown) {
            Text("🏘", fontSize = 18.sp)
        } else if (loc.isNewcomer) {
            Text("🌿", fontSize = 18.sp)
        } else {
            Text("⚔", fontSize = 18.sp)
        }
    }
}

/* ── Small tag chip ── */
@Composable
private fun Chip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}
