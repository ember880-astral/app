package com.astralofthesun.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.astralofthesun.app.data.Astral
import com.astralofthesun.app.data.LocationEntry
import com.astralofthesun.app.network.Repository
import com.astralofthesun.app.ui.screens.DungeonBattleScreen
import com.astralofthesun.app.ui.screens.DungeonDetailScreen
import com.astralofthesun.app.ui.screens.DungeonPrepScreen
import com.astralofthesun.app.ui.screens.FloorResultScreen
import com.astralofthesun.app.ui.screens.HomeScreen
import com.astralofthesun.app.ui.screens.ProfileScreen
import com.astralofthesun.app.ui.screens.SeasonScreen
import com.astralofthesun.app.ui.screens.ShopScreen
import com.astralofthesun.app.ui.screens.SkillLoadoutScreen
import com.astralofthesun.app.ui.screens.TopUpScreen
import com.astralofthesun.app.ui.screens.WorldMapScreen
import com.astralofthesun.app.network.AuthState
import com.astralofthesun.app.ui.screens.LoginFlow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import com.astralofthesun.app.ui.theme.Bg
import com.astralofthesun.app.ui.theme.Gold

enum class Screen(val label: String, val icon: ImageVector, val inNav: Boolean = true) {
    Home("Home", Icons.Filled.Home),
    Season("Season", Icons.Filled.Star),
    Shop("Shop", Icons.Filled.ShoppingBag),
    TopUp("Top-up", Icons.Filled.Wallet),
    Profile("Profile", Icons.Filled.Person),

    // ── Dungeon flow (not in bottom nav) ──
    WorldMap("Map", Icons.Filled.Home, inNav = false),        // .travel equivalent
    DungeonDetail("Detail", Icons.Filled.Home, inNav = false), // tap a dungeon → detail
    DungeonPrep("Prep", Icons.Filled.Home, inNav = false),     // legacy prep screen (party etc.)
    SkillLoadout("Skills", Icons.Filled.Home, inNav = false),  // reachable from map or menu
    DungeonBattle("Battle", Icons.Filled.Home, inNav = false), // the fight
    FloorResult("Result", Icons.Filled.Home, inNav = false),   // win or death
}

/* ── Root scaffold ─────────────────────────────────────────────────────
   Navigation is an in-memory enum switch. The dungeon sub-flow:
     WorldMap → DungeonDetail → DungeonBattle → FloorResult
                                              → FloorResult (win/death)
                                              → (loop via NextFloor)
   SkillLoadout is reachable from WorldMap and from any menu but is
   NOT forced into the dungeon entry path.
   ─────────────────────────────────────────────────────────────────── */
@Composable
fun App() {
    var checkingSession by remember { mutableStateOf(true) }
    val loggedIn by AuthState.isLoggedIn
    LaunchedEffect(Unit) {
        try { Repository.bootstrap() } finally { checkingSession = false }
    }
    LaunchedEffect(loggedIn, checkingSession) {
        if (loggedIn && !checkingSession) Repository.refreshAll()
    }
    if (checkingSession) {
        androidx.compose.material3.Surface(color = Color(0xFF101010)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    } else if (!loggedIn) {
        LoginFlow()
    } else {
        LoggedInApp()
    }
}

@Composable
private fun LoggedInApp() {
    var current by remember { mutableStateOf(Screen.Home) }
    var topUpCurrency by rememberSaveable { mutableStateOf("solars") }

    // Hold the selected location so DungeonDetail can read it without touching Astral
    var detailLocation by remember { mutableStateOf<LocationEntry?>(null) }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF050508)) {
                Screen.entries.filter { it.inNav }.forEach { screen ->
                    NavigationBarItem(
                        selected = current == screen,
                        onClick = { current = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = Color.Black,
                            indicatorColor = Color.White,
                            unselectedIconColor = Gold,
                            unselectedTextColor = Color(0x99FFFFFF),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (current) {
                Screen.Home -> HomeScreen(
                    goTopUp = { cur -> topUpCurrency = cur; current = Screen.TopUp },
                    goDungeon = { current = Screen.WorldMap },
                )
                Screen.Season -> SeasonScreen()
                Screen.Shop -> ShopScreen()
                Screen.TopUp -> TopUpScreen(initialCurrency = topUpCurrency)
                Screen.Profile -> ProfileScreen()

                // ── World map — the entry point to everything dungeon ──
                Screen.WorldMap -> WorldMapScreen(
                    onSelectLocation = { loc ->
                        detailLocation = loc
                        current = if (loc.isTown) Screen.Home // towns just return home for now
                                  else Screen.DungeonDetail
                    },
                )

                // ── Dungeon detail — big art, floor map, run cost, Enter button ──
                Screen.DungeonDetail -> {
                    val loc = detailLocation
                    if (loc == null) {
                        current = Screen.WorldMap
                    } else {
                        DungeonDetailScreen(
                            location = loc,
                            onEnter = {
                                // Spending 1 Run happens here — backend hook lives in Astral.dungeonLimits
                                // We go straight to battle (skipping the old Prep screen which the design
                                // no longer forces). Skill loadout is optional, reachable from the map.
                                Astral.battle.reset()
                                current = Screen.DungeonBattle
                            },
                            onBack = { current = Screen.WorldMap },
                        )
                    }
                }

                // ── Skill loadout — optional, reachable from WorldMap header ──
                Screen.SkillLoadout -> SkillLoadoutScreen(
                    onBack = { current = Screen.WorldMap },
                )

                // ── Legacy prep screen (party, old dungeon picker) ──
                Screen.DungeonPrep -> DungeonPrepScreen(
                    onEnterDungeon = { current = Screen.DungeonBattle },
                    onBack = { current = Screen.WorldMap },
                )

                // ── The fight ──
                Screen.DungeonBattle -> DungeonBattleScreen(
                    onFloorEnd = { current = Screen.FloorResult },
                )

                // ── Win or death ──
                Screen.FloorResult -> {
                    val result = Astral.floorResult.value
                    if (result == null) {
                        // No result yet — backend hasn't responded; stay on battle
                        current = Screen.DungeonBattle
                    } else {
                        FloorResultScreen(
                            result = result,
                            onNextFloor = {
                                // Next floor: reset battle state, keep run (already spent), costs 1 stamina
                                Astral.battle.reset()
                                Astral.floorResult.value = null
                                current = Screen.DungeonBattle
                            },
                            onLeaveDungeon = {
                                // Checkpoint saved, run over
                                Astral.battle.reset()
                                Astral.floorResult.value = null
                                current = Screen.WorldMap
                            },
                            onReturnToTown = {
                                // Death — no retry, back to home
                                Astral.battle.reset()
                                Astral.floorResult.value = null
                                current = Screen.Home
                            },
                        )
                    }
                }
            }
        }
    }
}
