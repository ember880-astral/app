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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astralofthesun.app.network.Repository
import com.astralofthesun.app.network.bool
import com.astralofthesun.app.network.num
import com.astralofthesun.app.network.numInt
import com.astralofthesun.app.network.obj
import com.astralofthesun.app.network.objs
import com.astralofthesun.app.network.payload
import com.astralofthesun.app.network.str
import com.astralofthesun.app.network.strs
import com.astralofthesun.app.network.userMessage
import com.astralofthesun.app.ui.components.AstralImage
import com.astralofthesun.app.ui.components.Bar
import com.astralofthesun.app.ui.components.BannerTone
import com.astralofthesun.app.ui.components.EmptyNote
import com.astralofthesun.app.ui.components.FactRow
import com.astralofthesun.app.ui.components.Notice
import com.astralofthesun.app.ui.components.Pill
import com.astralofthesun.app.ui.components.ScreenHeader
import com.astralofthesun.app.ui.components.SectionHeader
import com.astralofthesun.app.ui.components.TabRowChips
import com.astralofthesun.app.ui.components.cardModifier
import com.astralofthesun.app.ui.components.fmt
import com.astralofthesun.app.ui.theme.CardBorder
import com.astralofthesun.app.ui.theme.Gold
import com.astralofthesun.app.ui.theme.Primary
import com.astralofthesun.app.ui.theme.TextDim
import com.astralofthesun.app.ui.theme.TextFaint
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/* ── Pokémon ──────────────────────────────────────────────────────────
   Uses the bot's live /api/pokemon/* routes (Pokémon Showdown engine
   server-side). Like every other screen, the app only sends taps and
   draws what comes back: catch rolls, damage, XP, evolutions and prices
   are all decided by the server.

   Tabs: Party · Hunt · Battle · Bag · Shop · Dex · Tower.
   New players see the starter picker first.
   ──────────────────────────────────────────────────────────────────── */

private const val DEFAULT_SPRITES = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites"

/** One Pokémon as the app displays it. Field names are read defensively. */
private data class Mon(
    val id: String,
    val name: String,
    val species: String,
    val dexId: Int?,
    val level: Int?,
    val hp: Int?,
    val maxHp: Int?,
    val types: List<String>,
    val shiny: Boolean,
    val sprite: String,
    val isMain: Boolean,
    val inParty: Boolean,
    val fainted: Boolean,
    val nature: String,
    val held: String,
    val xp: Long?,
    val xpNext: Long?,
)

private fun spriteFor(dexId: Int?, shiny: Boolean, base: String): String =
    if (dexId == null || dexId <= 0) "" else "$base/pokemon/${if (shiny) "shiny/" else ""}$dexId.png"

/** Showdown "condition" strings look like "45/120", "45/120 par" or "0 fnt". */
private fun parseCondition(c: String?): Pair<Int?, Int?> {
    if (c.isNullOrBlank()) return null to null
    val first = c.trim().split(" ").first()
    val parts = first.split("/")
    return parts.getOrNull(0)?.toIntOrNull() to parts.getOrNull(1)?.toIntOrNull()
}

private fun parseMon(o: JsonObject?, spriteBase: String): Mon? {
    if (o == null) return null
    val species = o.str("species", "speciesName", "speciesId") ?: ""
    val dexId = o.numInt("dexId", "dex", "num", "nationalDex")
    val shiny = o.bool("shiny", "isShiny") ?: false
    val (cHp, cMax) = parseCondition(o.str("condition"))
    val hp = o.numInt("hp", "currentHp") ?: cHp
    val maxHp = o.numInt("maxHp", "maxhp", "hpMax") ?: cMax
    return Mon(
        id = o.str("id", "uid", "_id", "monId") ?: "",
        name = o.str("nickname", "name", "displayName") ?: species.ifEmpty { "Pokémon" },
        species = species,
        dexId = dexId,
        level = o.numInt("level", "lvl"),
        hp = hp,
        maxHp = maxHp,
        types = o.strs("types"),
        shiny = shiny,
        sprite = o.str("sprite", "spriteUrl", "image", "imageUrl") ?: spriteFor(dexId, shiny, spriteBase),
        isMain = o.bool("isMain", "main") ?: false,
        inParty = o.bool("inParty", "party", "held") ?: false,
        fainted = o.bool("fainted") ?: (o.str("condition")?.contains("fnt") == true || hp == 0),
        nature = o.str("nature") ?: "",
        held = o.str("heldItem", "item") ?: "",
        xp = o.num("xp", "exp"),
        xpNext = o.num("xpNext", "expNext", "xpToNext"),
    )
}

private val TYPE_COLORS = mapOf(
    "normal" to 0xFFA8A77A, "fire" to 0xFFEE8130, "water" to 0xFF6390F0, "electric" to 0xFFF7D02C,
    "grass" to 0xFF7AC74C, "ice" to 0xFF96D9D6, "fighting" to 0xFFC22E28, "poison" to 0xFFA33EA1,
    "ground" to 0xFFE2BF65, "flying" to 0xFFA98FF3, "psychic" to 0xFFF95587, "bug" to 0xFFA6B91A,
    "rock" to 0xFFB6A136, "ghost" to 0xFF735797, "dragon" to 0xFF6F35FC, "dark" to 0xFF705746,
    "steel" to 0xFFB7B7CE, "fairy" to 0xFFD685AD,
)

private fun typeColor(t: String) = Color(TYPE_COLORS[t.lowercase()] ?: 0xFF888888)

/* ════════════════════════════════════════════════════════════════════ */

@Composable
fun PokemonScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var fatal by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<Pair<String, BannerTone>?>(null) }
    var busy by remember { mutableStateOf(false) }

    var spriteBase by remember { mutableStateOf(DEFAULT_SPRITES) }
    var meta by remember { mutableStateOf<JsonObject?>(null) }
    var overview by remember { mutableStateOf<JsonObject?>(null) }
    var starterInfo by remember { mutableStateOf<JsonObject?>(null) }
    val party = remember { mutableStateListOf<Mon>() }
    val box = remember { mutableStateListOf<Mon>() }

    var tab by remember { mutableStateOf("party") }
    var openMon by remember { mutableStateOf<Mon?>(null) }
    var battle by remember { mutableStateOf<JsonObject?>(null) }
    var encounter by remember { mutableStateOf<JsonObject?>(null) }

    suspend fun reloadMons() {
        Repository.pokemonParty().onSuccess { list ->
            party.clear(); party.addAll(list.mapNotNull { parseMon(it, spriteBase) })
        }
        Repository.pokemonMons().onSuccess { list ->
            box.clear(); box.addAll(list.mapNotNull { parseMon(it, spriteBase) })
        }
        Repository.pokemonOverview().onSuccess { overview = it }
    }

    suspend fun reloadAll() {
        loading = true
        fatal = null
        Repository.pokemonMeta().onSuccess { m ->
            meta = m
            m.obj("sprites")?.str("base")?.let { spriteBase = it.trimEnd('/') }
        }
        val ov = Repository.pokemonOverview()
        ov.onFailure { fatal = it.userMessage() }
        ov.onSuccess { overview = it }
        Repository.pokemonStarter().onSuccess { starterInfo = it }
        reloadMons()
        loading = false
    }

    /** Every button goes through here: send → show the server's message → refresh. */
    fun act(
        successNote: String? = null,
        refresh: Boolean = true,
        onOk: (JsonObject) -> Unit = {},
        call: suspend () -> Result<JsonObject>,
    ) {
        if (busy) return
        busy = true
        scope.launch {
            call()
                .onSuccess { res ->
                    val msg = res.payload().str("message", "msg", "text") ?: successNote
                    if (msg != null) toast = msg to BannerTone.Success
                    onOk(res.payload())
                    if (refresh) reloadMons()
                }
                .onFailure { toast = it.userMessage() to BannerTone.Error }
            busy = false
        }
    }

    fun takeBattle(p: JsonObject) {
        val b = p.obj("battle") ?: p.takeIf { it.str("battleId") != null || it.obj("request") != null }
        if (b != null) {
            battle = b
            tab = "battle"
        }
    }

    LaunchedEffect(Unit) { reloadAll() }

    val mainMon = party.firstOrNull { it.isMain } ?: box.firstOrNull { it.isMain } ?: party.firstOrNull()
    val ownsAny = party.isNotEmpty() || box.isNotEmpty()
    val needsStarter = !ownsAny && !loading && fatal == null && starterInfo?.let { s ->
        s.bool("needsStarter", "canChoose", "available", "eligible")
            ?: s.bool("hasStarter", "chosen")?.not()
    } ?: overview?.bool("needsStarter")
    ?: overview?.bool("hasStarter")?.not()
    ?: true

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Pokémon",
            subtitle = overview?.let { ov ->
                listOfNotNull(
                    ov.numInt("caught", "owned", "total")?.let { "$it caught" },
                    ov.numInt("dexSeen", "seen")?.let { "$it seen" },
                ).joinToString(" · ")
            } ?: "",
            onBack = onBack,
        )

        toast?.let { (msg, tone) ->
            Notice(msg, tone, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { toast = null }
        }

        when {
            loading && party.isEmpty() && box.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }

            fatal != null && party.isEmpty() && box.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Notice(fatal ?: "", BannerTone.Error)
                OutlinedButton(onClick = { scope.launch { reloadAll() } }) { Text("Retry") }
            }

            needsStarter -> StarterPicker(
                starters = (starterInfo?.objs("starters", "options", "choices") ?: emptyList())
                    .ifEmpty { meta?.objs("starters") ?: emptyList() },
                gift = starterInfo?.str("gift", "starterGift") ?: meta?.str("starterGift"),
                spriteBase = spriteBase,
                busy = busy,
                onChoose = { speciesId ->
                    act(onOk = { scope.launch { reloadAll() } }) { Repository.choosePokemonStarter(speciesId) }
                },
            )

            else -> {
                TabRowChips(
                    tabs = listOf(
                        "party" to "Party",
                        "hunt" to "Hunt",
                        "battle" to if (battle != null) "Battle •" else "Battle",
                        "bag" to "Bag",
                        "shop" to "Shop",
                        "dex" to "Pokédex",
                        "tower" to "Tower",
                    ),
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        "party" -> PartyTab(
                            party = party,
                            box = box,
                            partyMax = meta?.numInt("partyMax") ?: 6,
                            busy = busy,
                            onOpen = { openMon = it },
                            onHealAll = { act("Party healed.") { Repository.healPokemon() } },
                        )
                        "hunt" -> HuntTab(
                            encounter = encounter,
                            mainMon = mainMon,
                            spriteBase = spriteBase,
                            busy = busy,
                            onHunt = {
                                act(onOk = { p -> encounter = p; takeBattle(p) }) { Repository.pokemonHunt() }
                            },
                            onFight = { opponentId ->
                                val m = mainMon
                                if (m == null) toast = "Pick a main Pokémon first (Party tab)." to BannerTone.Warning
                                else act(onOk = { p -> encounter = null; takeBattle(p) }) {
                                    Repository.startPokemonBattle(m.id, opponentId)
                                }
                            },
                        )
                        "battle" -> BattleTab(
                            battle = battle,
                            spriteBase = spriteBase,
                            busy = busy,
                            onChoice = { battleId, choice ->
                                act(refresh = false, onOk = { p -> battle = p.obj("battle") ?: p }) {
                                    Repository.pokemonBattleAct(battleId, choice)
                                }
                            },
                            onForfeit = { battleId ->
                                act("You forfeited.", onOk = { battle = null }) { Repository.forfeitPokemonBattle(battleId) }
                            },
                            onClose = {
                                battle = null
                                tab = "party"
                                scope.launch { reloadMons() }
                            },
                        )
                        "bag" -> BagTab(
                            mainMon = mainMon,
                            busy = busy,
                            act = { note, call -> act(note, call = call) },
                        )
                        "shop" -> ShopTab(
                            busy = busy,
                            act = { note, call -> act(note, call = call) },
                        )
                        "dex" -> DexTab(spriteBase = spriteBase, dexTotal = meta?.numInt("dexTotal"))
                        "tower" -> TowerTab(
                            busy = busy,
                            onChallenge = { act(onOk = { takeBattle(it) }) { Repository.challengePokemonTower() } },
                        )
                    }
                }
            }
        }
    }

    openMon?.let { mon ->
        MonSheet(
            mon = mon,
            costs = meta?.obj("costs"),
            busy = busy,
            onDismiss = { openMon = null },
            act = { note, call -> act(note, onOk = { openMon = null }, call = call) },
        )
    }
}

/* ── Starter picker ───────────────────────────────────────────────── */

@Composable
private fun StarterPicker(
    starters: List<JsonObject>,
    gift: String?,
    spriteBase: String,
    busy: Boolean,
    onChoose: (String) -> Unit,
) {
    var pick by remember { mutableStateOf<JsonObject?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Choose your starter", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Text("This is permanent — your first partner.", color = TextDim, fontSize = 12.sp)
        gift?.let { Text("Starter gift: $it", color = Gold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(10.dp))
        if (starters.isEmpty()) {
            EmptyNote("No starters offered right now.")
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(starters) { s ->
                val dex = s.numInt("dexId", "dex", "id")
                val selected = pick == s
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Color(0xFF12122A) else Color(0xFF080808))
                        .border(if (selected) 1.dp else 0.5.dp, if (selected) Primary else CardBorder, RoundedCornerShape(14.dp))
                        .clickable { pick = s }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AstralImage(
                        url = s.str("sprite", "image") ?: spriteFor(dex, false, spriteBase),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(64.dp),
                    )
                    Text(s.str("name") ?: "#$dex", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    TypeRow(s.strs("types"))
                }
            }
        }
    }
    pick?.let { s ->
        val name = s.str("name") ?: "this Pokémon"
        AlertDialog(
            onDismissRequest = { pick = null },
            title = { Text("Choose $name?") },
            text = { Text("You can't change your starter later.") },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    val id = s.str("speciesId", "id", "key") ?: s.numInt("dexId")?.toString() ?: name
                    pick = null
                    onChoose(id)
                }) { Text("Choose") }
            },
            dismissButton = { TextButton(onClick = { pick = null }) { Text("Cancel") } },
        )
    }
}

/* ── Party + box ──────────────────────────────────────────────────── */

@Composable
private fun PartyTab(
    party: List<Mon>,
    box: List<Mon>,
    partyMax: Int,
    busy: Boolean,
    onOpen: (Mon) -> Unit,
    onHealAll: () -> Unit,
) {
    val partyIds = party.map { it.id }.toSet()
    val boxOnly = box.filter { it.id !in partyIds && !it.inParty }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionHeader("Party", "${party.size}/$partyMax") }
                OutlinedButton(onClick = onHealAll, enabled = !busy && party.isNotEmpty()) { Text("Heal party", fontSize = 12.sp) }
            }
        }
        if (party.isEmpty()) item { EmptyNote("Your party is empty — add Pokémon from the box.") }
        items(party, key = { "p" + it.id }) { MonRow(it) { onOpen(it) } }
        item { Spacer(Modifier.height(6.dp)); SectionHeader("Box", "${boxOnly.size}") }
        if (boxOnly.isEmpty()) item { EmptyNote("No Pokémon in the box.") }
        items(boxOnly, key = { "b" + it.id }) { MonRow(it) { onOpen(it) } }
    }
}

@Composable
private fun MonRow(mon: Mon, onTap: () -> Unit) {
    Row(
        modifier = cardModifier().clickable(onClick = onTap).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AstralImage(url = mon.sprite, glyph = "◓", contentScale = ContentScale.Fit, modifier = Modifier.size(52.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(mon.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                if (mon.shiny) Text("✦", color = Gold, fontSize = 12.sp)
                if (mon.isMain) Pill("Main", Gold)
                if (mon.fainted) Pill("Fainted", Color(0xFFE76E6E))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                mon.level?.let { Text("Lv. $it", color = TextDim, fontSize = 11.sp) }
                TypeRow(mon.types)
            }
            HpBar(mon.hp, mon.maxHp)
        }
    }
}

@Composable
private fun HpBar(hp: Int?, max: Int?) {
    if (hp == null || max == null || max <= 0) return
    val f = (hp.toFloat() / max).coerceIn(0f, 1f)
    val c = when {
        f > 0.5f -> Color(0xFF6EE787)
        f > 0.2f -> Color(0xFFE7C56E)
        else -> Color(0xFFE76E6E)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Bar(f, c, modifier = Modifier.weight(1f))
        Text("$hp/$max", color = TextFaint, fontSize = 10.sp)
    }
}

@Composable
private fun TypeRow(types: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        types.forEach { Pill(it.replaceFirstChar { c -> c.uppercase() }, typeColor(it)) }
    }
}

/* ── One Pokémon: detail + actions ────────────────────────────────── */

@Composable
private fun MonSheet(
    mon: Mon,
    costs: JsonObject?,
    busy: Boolean,
    onDismiss: () -> Unit,
    act: (String?, suspend () -> Result<JsonObject>) -> Unit,
) {
    var detail by remember(mon.id) { mutableStateOf<JsonObject?>(null) }
    var evo by remember(mon.id) { mutableStateOf<JsonObject?>(null) }
    var moves by remember(mon.id) { mutableStateOf<List<String>>(emptyList()) }
    var confirmRelease by remember { mutableStateOf(false) }

    LaunchedEffect(mon.id) {
        Repository.pokemonMon(mon.id).onSuccess { detail = it.obj("mon", "pokemon") ?: it }
        Repository.pokemonEvolution(mon.id).onSuccess { evo = it }
        Repository.pokemonMoves(mon.id).onSuccess { r ->
            moves = r.objs("moves", "learned", "known").mapNotNull { it.str("name", "move", "id") }
                .ifEmpty { r.strs("moves") }
        }
    }

    val canEvolve = evo?.bool("canEvolve", "ready", "eligible") == true
    val evoInto = evo?.str("into", "nextName", "evolvesTo") ?: evo?.obj("next")?.str("name")
    val evoNeeds = evo?.str("requirement", "needs", "condition", "hint")
    val feedCost = costs?.numInt("feed")
    val trainCost = costs?.numInt("trainPerLevel")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AstralImage(url = mon.sprite, glyph = "◓", contentScale = ContentScale.Fit, modifier = Modifier.size(56.dp))
                Column {
                    Text(mon.name + if (mon.shiny) " ✦" else "", fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(mon.level?.let { "Lv. $it" }, mon.species.ifEmpty { null }).joinToString(" · "),
                        color = TextDim, fontSize = 12.sp,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeRow(mon.types)
                HpBar(mon.hp, mon.maxHp)
                if (mon.nature.isNotEmpty()) FactRow("Nature", mon.nature)
                (detail?.str("ability") ?: detail?.obj("ability")?.str("name"))?.let { FactRow("Ability", it) }
                if (mon.held.isNotEmpty()) FactRow("Held item", mon.held)
                if (mon.xp != null) FactRow("XP", fmt(mon.xp) + (mon.xpNext?.let { " / ${fmt(it)}" } ?: ""))
                if (moves.isNotEmpty()) FactRow("Moves", moves.joinToString(", "))
                when {
                    canEvolve -> Text("Ready to evolve" + (evoInto?.let { " into $it" } ?: "") + "!", color = Gold, fontSize = 12.sp)
                    evoInto != null -> Text("Evolves into $evoInto" + (evoNeeds?.let { " — $it" } ?: ""), color = TextFaint, fontSize = 11.sp)
                }

                Spacer(Modifier.height(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!mon.isMain) SheetButton("Set main", busy) { act("Main Pokémon set.") { Repository.setMainPokemon(mon.id) } }
                    if (mon.inParty) SheetButton("To box", busy) { act(null) { Repository.unholdPokemon(mon.id) } }
                    else SheetButton("To party", busy) { act(null) { Repository.holdPokemon(mon.id) } }
                    SheetButton("Feed" + (feedCost?.let { " ($it☀)" } ?: ""), busy) { act(null) { Repository.feedPokemon(mon.id) } }
                    SheetButton("Train" + (trainCost?.let { " ($it☀)" } ?: ""), busy) { act(null) { Repository.trainPokemon(mon.id) } }
                    if (canEvolve) SheetButton("Evolve", busy, primary = true) { act(null) { Repository.evolvePokemon(mon.id) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (!mon.isMain) TextButton(onClick = { confirmRelease = true }) { Text("Release", color = Color(0xFFE76E6E)) }
        },
    )

    if (confirmRelease) {
        AlertDialog(
            onDismissRequest = { confirmRelease = false },
            title = { Text("Release ${mon.name}?") },
            text = { Text("It will be gone for good.") },
            confirmButton = {
                Button(
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB03A3A)),
                    onClick = { confirmRelease = false; act("${mon.name} was released.") { Repository.releasePokemon(mon.id) } },
                ) { Text("Release") }
            },
            dismissButton = { TextButton(onClick = { confirmRelease = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun SheetButton(label: String, busy: Boolean, primary: Boolean = false, onClick: () -> Unit) {
    if (primary) {
        Button(onClick = onClick, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
            Text(label, fontSize = 12.sp)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = !busy, contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text(label, fontSize = 12.sp)
        }
    }
}

/* ── Hunt ─────────────────────────────────────────────────────────── */

@Composable
private fun HuntTab(
    encounter: JsonObject?,
    mainMon: Mon?,
    spriteBase: String,
    busy: Boolean,
    onHunt: () -> Unit,
    onFight: (String?) -> Unit,
) {
    val wildObj = encounter?.obj("wild", "encounter", "pokemon", "mon")
    val wild = parseMon(wildObj, spriteBase)
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        mainMon?.let { Text("Lead: ${it.name}" + (it.level?.let { l -> " (Lv. $l)" } ?: ""), color = TextDim, fontSize = 12.sp) }
        if (wild != null) {
            Column(
                modifier = cardModifier().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("A wild Pokémon appeared!", color = TextDim, fontSize = 12.sp)
                AstralImage(url = wild.sprite, contentScale = ContentScale.Fit, modifier = Modifier.size(120.dp))
                Text(wild.name + if (wild.shiny) " ✦" else "", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                wild.level?.let { Text("Lv. $it", color = TextDim, fontSize = 12.sp) }
                TypeRow(wild.types)
                encounter?.str("region", "area", "location")?.let { Text(it, color = TextFaint, fontSize = 11.sp) }
                Button(
                    onClick = { onFight(wildObj?.str("id", "encounterId") ?: encounter?.str("encounterId", "id")) },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Battle & catch") }
            }
        } else if (encounter != null) {
            Text(encounter.str("message", "text") ?: "Nothing turned up this time.", color = TextDim, textAlign = TextAlign.Center)
        } else {
            Spacer(Modifier.height(40.dp))
            Text("◓", fontSize = 56.sp, color = TextFaint)
            Text("Search the tall grass for wild Pokémon.", color = TextDim, textAlign = TextAlign.Center)
        }
        Button(
            onClick = onHunt,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (wild == null) Primary else Color(0xFF1B1B2A)),
        ) { Text(if (wild == null) "Hunt" else "Keep searching", fontWeight = FontWeight.Bold) }
    }
}

/* ── Battle (Showdown) ────────────────────────────────────────────── */

private data class Choice(val label: String, val choice: String, val detail: String = "", val enabled: Boolean = true)

/** Moves / switches / balls the server says are available. Server-provided choices win. */
private fun battleChoices(b: JsonObject): Triple<List<Choice>, List<Choice>, List<Choice>> {
    val explicit = b.objs("actions", "choices", "options").mapNotNull { o ->
        val c = o.str("choice", "action", "id") ?: return@mapNotNull null
        Choice(o.str("label", "name") ?: c, c, o.str("detail", "desc") ?: "", o.bool("disabled")?.not() ?: true)
    }
    if (explicit.isNotEmpty()) return Triple(explicit, emptyList(), emptyList())

    val req = b.obj("request")
    val moveObjs = b.objs("moves").ifEmpty {
        b.obj("you", "player", "active")?.objs("moves")?.takeIf { it.isNotEmpty() }
            ?: req?.objs("active")?.firstOrNull()?.objs("moves") ?: emptyList()
    }
    val moves = moveObjs.mapIndexed { i, m ->
        val name = m.str("name", "move") ?: "Move ${i + 1}"
        val pp = m.numInt("pp")
        val maxPp = m.numInt("maxpp", "maxPp")
        Choice(
            label = name,
            choice = m.str("choice") ?: m.str("id") ?: "move ${i + 1}",
            detail = listOfNotNull(m.str("type"), pp?.let { "PP $it" + (maxPp?.let { x -> "/$x" } ?: "") }).joinToString(" · "),
            enabled = m.bool("disabled") != true && (pp == null || pp > 0),
        )
    }
    val team = b.objs("party", "team").ifEmpty { req?.obj("side")?.objs("pokemon") ?: emptyList() }
    val switches = team.mapIndexedNotNull { i, p ->
        if (p.bool("active") == true) return@mapIndexedNotNull null
        val cond = p.str("condition") ?: ""
        val name = p.str("name", "nickname") ?: p.str("ident")?.substringAfter(": ") ?: "Slot ${i + 1}"
        Choice(name, p.str("choice") ?: "switch ${i + 1}", cond, !cond.contains("fnt") && p.numInt("hp") != 0)
    }
    val balls = b.objs("balls").map { o ->
        val id = o.str("id", "itemId") ?: ""
        Choice(o.str("name") ?: id, o.str("choice") ?: "ball $id", o.numInt("qty", "count")?.let { "×$it" } ?: "", (o.numInt("qty", "count") ?: 1) > 0)
    }
    return Triple(moves, switches, balls)
}

@Composable
private fun BattleTab(
    battle: JsonObject?,
    spriteBase: String,
    busy: Boolean,
    onChoice: (String, String) -> Unit,
    onForfeit: (String) -> Unit,
    onClose: () -> Unit,
) {
    if (battle == null) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            EmptyNote("No battle in progress. Hunt a wild Pokémon or challenge the Tower.")
        }
        return
    }
    val battleId = battle.str("id", "battleId") ?: ""
    val me = parseMon(battle.obj("you", "player", "mine", "self", "ally"), spriteBase)
    val foe = parseMon(battle.obj("opponent", "foe", "enemy", "wild", "target"), spriteBase)
    val log = battle.strs("log", "lines", "messages", "events").takeLast(8)
    val status = battle.str("status", "state")?.lowercase()
    val ended = battle.bool("ended", "over", "finished", "done") == true ||
        (status != null && status in setOf("ended", "finished", "won", "lost", "caught", "fled", "forfeit"))
    val outcome = battle.str("result", "outcome", "winnerText", "message")
    val waiting = battle.bool("waiting") == true
    val (moves, switches, balls) = battleChoices(battle)
    var confirmForfeit by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BattleSide(foe, isFoe = true) }
        item { BattleSide(me, isFoe = false) }
        if (log.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF070707)).padding(10.dp)) {
                log.forEach { Text(it, color = TextDim, fontSize = 11.sp) }
            }
        }
        if (ended) {
            item {
                Column(
                    modifier = cardModifier().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(outcome ?: "Battle over", fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
                    battle.num("xpGained", "xp")?.let { Text("+${fmt(it)} XP", color = Color(0xFF6EE787)) }
                    battle.num("solarsGained", "solars", "reward")?.let { Text("+${fmt(it)} Solars", color = Gold) }
                    Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Done") }
                }
            }
        } else {
            if (waiting) item { Text("Waiting for the opponent…", color = TextDim, fontSize = 12.sp) }
            item { SectionHeader("Moves") }
            item {
                ChoiceGrid(moves, busy || waiting) { onChoice(battleId, it.choice) }
            }
            if (balls.isNotEmpty()) {
                item { SectionHeader("Throw a ball") }
                item { ChoiceGrid(balls, busy || waiting) { onChoice(battleId, it.choice) } }
            }
            if (switches.isNotEmpty()) {
                item { SectionHeader("Switch") }
                item { ChoiceGrid(switches, busy || waiting) { onChoice(battleId, it.choice) } }
            }
            item {
                OutlinedButton(onClick = { confirmForfeit = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Forfeit", color = Color(0xFFE76E6E))
                }
            }
        }
    }

    if (confirmForfeit) {
        AlertDialog(
            onDismissRequest = { confirmForfeit = false },
            title = { Text("Forfeit this battle?") },
            text = { Text("The battle ends as a loss.") },
            confirmButton = {
                Button(onClick = { confirmForfeit = false; onForfeit(battleId) }) { Text("Forfeit") }
            },
            dismissButton = { TextButton(onClick = { confirmForfeit = false }) { Text("Keep fighting") } },
        )
    }
}

@Composable
private fun BattleSide(mon: Mon?, isFoe: Boolean) {
    if (mon == null) return
    Row(
        modifier = cardModifier().padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AstralImage(url = mon.sprite, glyph = "◓", contentScale = ContentScale.Fit, modifier = Modifier.size(64.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text((if (isFoe) "Foe · " else "You · ") + mon.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            mon.level?.let { Text("Lv. $it", color = TextDim, fontSize = 11.sp) }
            HpBar(mon.hp, mon.maxHp)
        }
    }
}

@Composable
private fun ChoiceGrid(choices: List<Choice>, busy: Boolean, onTap: (Choice) -> Unit) {
    if (choices.isEmpty()) {
        Text("No options right now.", color = TextFaint, fontSize = 12.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (c.enabled && !busy) Color(0xFF14142A) else Color(0xFF0A0A0A))
                            .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp))
                            .clickable(enabled = c.enabled && !busy) { onTap(c) }
                            .padding(10.dp),
                    ) {
                        Text(c.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (c.enabled) Color.White else TextFaint)
                        if (c.detail.isNotEmpty()) Text(c.detail, color = TextFaint, fontSize = 10.sp)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/* ── Bag ──────────────────────────────────────────────────────────── */

@Composable
private fun BagTab(
    mainMon: Mon?,
    busy: Boolean,
    act: (String?, suspend () -> Result<JsonObject>) -> Unit,
) {
    val bag = remember { mutableStateListOf<JsonObject>() }
    var error by remember { mutableStateOf<String?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey, busy) {
        if (busy) return@LaunchedEffect
        Repository.pokemonBag()
            .onSuccess { bag.clear(); bag.addAll(it); error = null }
            .onFailure { error = it.userMessage() }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionHeader("Bag", "${bag.size} items") }
                TextButton(onClick = { showImport = true }) { Text("Import code") }
            }
        }
        error?.let { item { Notice(it, BannerTone.Error) } }
        if (bag.isEmpty() && error == null) item { EmptyNote("Your Pokémon bag is empty.") }
        items(bag) {
            val id = it.str("id", "itemId", "key") ?: ""
            val name = it.str("name", "label") ?: id
            val qty = it.numInt("qty", "count", "quantity")
            val usable = it.bool("usable", "canUse") ?: true
            Row(
                modifier = cardModifier().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AstralImage(url = it.str("icon", "iconUrl", "sprite", "image") ?: "", glyph = "🎒", contentScale = ContentScale.Fit, modifier = Modifier.size(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(name + (qty?.let { q -> "  ×$q" } ?: ""), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    it.str("description", "desc", "effect")?.let { d -> Text(d, color = TextDim, fontSize = 11.sp, maxLines = 2) }
                }
                if (usable) OutlinedButton(onClick = {
                    act(null) { Repository.usePokemonBagItem(id, mainMon?.id) }
                }, enabled = !busy, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text(if (mainMon != null) "Use on ${mainMon.name}" else "Use", fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import bag code") },
            text = {
                OutlinedTextField(value = code, onValueChange = { code = it.trim() }, singleLine = true, label = { Text("Code") })
            },
            confirmButton = {
                Button(enabled = code.isNotBlank() && !busy, onClick = {
                    val c = code
                    showImport = false
                    code = ""
                    act("Code redeemed.") { Repository.importPokemonBag(c) }
                    reloadKey++
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } },
        )
    }
}

/* ── Poké Mart ────────────────────────────────────────────────────── */

@Composable
private fun ShopTab(
    busy: Boolean,
    act: (String?, suspend () -> Result<JsonObject>) -> Unit,
) {
    val stock = remember { mutableStateListOf<JsonObject>() }
    var error by remember { mutableStateOf<String?>(null) }
    var buying by remember { mutableStateOf<JsonObject?>(null) }
    var qty by remember { mutableStateOf(1) }

    LaunchedEffect(Unit) {
        Repository.pokemonShop()
            .onSuccess { stock.clear(); stock.addAll(it) }
            .onFailure { error = it.userMessage() }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionHeader("Poké Mart") }
        error?.let { item { Notice(it, BannerTone.Error) } }
        if (stock.isEmpty() && error == null) item { EmptyNote("Nothing for sale right now.") }
        items(stock) {
            val price = it.num("price", "buyPrice", "cost")
            Row(
                modifier = cardModifier().clickable { buying = it; qty = 1 }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AstralImage(url = it.str("icon", "iconUrl", "sprite", "image") ?: "", glyph = "🛒", contentScale = ContentScale.Fit, modifier = Modifier.size(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(it.str("name", "label") ?: "Item", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    it.str("description", "desc", "effect")?.let { d -> Text(d, color = TextDim, fontSize = 11.sp, maxLines = 2) }
                }
                Text(price?.let { p -> "${fmt(p)} ☀" } ?: "—", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }

    buying?.let { item ->
        val id = item.str("id", "itemId", "key") ?: ""
        val price = item.num("price", "buyPrice", "cost")
        AlertDialog(
            onDismissRequest = { buying = null },
            title = { Text(item.str("name", "label") ?: "Buy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item.str("description", "desc", "effect")?.let { Text(it, color = TextDim, fontSize = 12.sp) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { if (qty > 1) qty-- }) { Text("−") }
                        Text("$qty", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        OutlinedButton(onClick = { if (qty < 99) qty++ }) { Text("+") }
                    }
                    price?.let { Text("Total: ${fmt(it * qty)} Solars", color = Gold, fontSize = 13.sp) }
                    Text("The server checks your balance and charges it.", color = TextFaint, fontSize = 10.sp)
                }
            },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    val q = qty
                    buying = null
                    act("Purchased.") { Repository.pokemonShopBuy(id, q) }
                }) { Text("Buy") }
            },
            dismissButton = { TextButton(onClick = { buying = null }) { Text("Cancel") } },
        )
    }
}

/* ── Pokédex ──────────────────────────────────────────────────────── */

@Composable
private fun DexTab(spriteBase: String, dexTotal: Int?) {
    val entries = remember { mutableStateListOf<JsonObject>() }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        Repository.pokemonDex()
            .onSuccess { entries.clear(); entries.addAll(it) }
            .onFailure { error = it.userMessage() }
    }
    val caught = entries.count { it.bool("caught", "owned") == true }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SectionHeader("Pokédex", "$caught caught" + (dexTotal?.let { " / $it" } ?: ""))
        error?.let { Notice(it, BannerTone.Error) }
        if (entries.isEmpty() && error == null) EmptyNote("No entries yet — go catch something!")
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(entries) { e ->
                val dex = e.numInt("dexId", "dex", "id", "num")
                val isCaught = e.bool("caught", "owned") == true
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF080808))
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AstralImage(
                        url = e.str("sprite", "image") ?: spriteFor(dex, false, spriteBase),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(52.dp),
                    )
                    Text(dex?.let { "#$it" } ?: "", color = TextFaint, fontSize = 9.sp)
                    Text(
                        e.str("name", "species") ?: "???",
                        fontSize = 10.sp, maxLines = 1,
                        color = if (isCaught) Color.White else TextDim,
                    )
                }
            }
        }
    }
}

/* ── Battle Tower ─────────────────────────────────────────────────── */

@Composable
private fun TowerTab(busy: Boolean, onChallenge: () -> Unit) {
    var tower by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(busy) {
        if (!busy) Repository.pokemonTower().onSuccess { tower = it.obj("tower") ?: it; error = null }.onFailure { error = it.userMessage() }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Battle Tower")
        error?.let { Notice(it, BannerTone.Error) }
        tower?.let { t ->
            Column(cardModifier().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                t.numInt("floor", "currentFloor", "stage")?.let { FactRow("Current floor", "$it") }
                t.numInt("best", "bestFloor", "highest")?.let { FactRow("Best", "$it") }
                t.numInt("streak", "wins")?.let { FactRow("Win streak", "$it") }
                t.str("next", "nextOpponent")?.let { FactRow("Next opponent", it) }
                    ?: t.obj("next", "opponent")?.str("name")?.let { FactRow("Next opponent", it) }
                t.num("reward", "nextReward")?.let { FactRow("Reward", "${fmt(it)} Solars", Gold) }
                t.str("message", "note")?.let { Text(it, color = TextDim, fontSize = 12.sp) }
            }
        }
        val can = tower?.bool("canChallenge", "available") ?: true
        Button(
            onClick = onChallenge,
            enabled = !busy && can,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) { Text("Challenge", fontWeight = FontWeight.Bold) }
        if (!can) tower?.str("reason", "lockedReason")?.let { Text(it, color = TextFaint, fontSize = 11.sp) }
    }
}
