package com.astralofthesun.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
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
import coil.compose.AsyncImage
import com.astralofthesun.app.ui.theme.CardBorder
import com.astralofthesun.app.ui.theme.Danger
import com.astralofthesun.app.ui.theme.Gold
import com.astralofthesun.app.ui.theme.Success
import com.astralofthesun.app.ui.theme.TextDim
import com.astralofthesun.app.ui.theme.TextFaint
import com.astralofthesun.app.ui.theme.Warning
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* ── Formatting ─────────────────────────────────────────────── */

fun fmt(n: Long?): String = if (n == null) "—" else NumberFormat.getIntegerInstance(Locale.US).format(n)
fun fmt(n: Int?): String = fmt(n?.toLong())

fun naira(n: Long?): String = if (n == null) "₦—" else "₦" + fmt(n)

fun fmtDate(epochMs: Long?): String =
    if (epochMs == null) "—" else SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(epochMs))

/** "3d 4h", "4h 12m", "12m 05s", "45s". */
fun fmtDuration(ms: Long): String {
    if (ms <= 0) return "0s"
    val s = ms / 1000
    val d = s / 86_400
    val h = (s % 86_400) / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${sec.toString().padStart(2, '0')}s"
        else -> "${sec}s"
    }
}

/** Next local midnight (the daily Runs/Stamina reset). */
fun nextLocalMidnight(now: Long = System.currentTimeMillis()): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = now
    c.add(Calendar.DAY_OF_YEAR, 1)
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** Ticking clock for countdowns. Recomposes every [periodMs]. */
@Composable
fun rememberNow(periodMs: Long = 1000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs)
        }
    }
    return now
}

fun rarityColor(rarity: String): Color = when (rarity.lowercase()) {
    "common" -> Color(0xFFB0B0B0)
    "uncommon" -> Color(0xFF6EE787)
    "rare" -> Color(0xFF6EA8E7)
    "epic" -> Color(0xFFB36EE7)
    "legendary" -> Gold
    "mythic" -> Color(0xFFFF6FA8)
    "boundless" -> Color(0xFF7ADEFF)
    else -> TextDim
}

/* ── Building blocks ────────────────────────────────────────── */

/** Remote image with a dark placeholder box; empty URL → placeholder + optional glyph. */
@Composable
fun AstralImage(
    url: String,
    modifier: Modifier = Modifier,
    glyph: String = "",
    contentScale: ContentScale = ContentScale.Crop,
    corner: Int = 12,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner.dp))
            .background(Color(0xFF111111)),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph.isNotEmpty()) Text(glyph, fontSize = 18.sp)
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Title row with optional back arrow and trailing content. */
@Composable
fun ScreenHeader(title: String, subtitle: String = "", onBack: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        } else {
            Box(Modifier.padding(start = 8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            if (subtitle.isNotEmpty()) Text(subtitle, color = TextDim, fontSize = 11.sp)
        }
        trailing()
    }
}

enum class BannerTone { Info, Success, Warning, Error }

/** Inline notice used for action results, errors and "not live yet". */
@Composable
fun Notice(text: String, tone: BannerTone = BannerTone.Info, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    val c = when (tone) {
        BannerTone.Info -> Color(0xFF6EA8E7)
        BannerTone.Success -> Success
        BannerTone.Warning -> Warning
        BannerTone.Error -> Danger
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.copy(alpha = 0.10f))
            .border(0.5.dp, c.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = c, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (onDismiss != null) {
            Text("✕", color = c, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(start = 8.dp))
        }
    }
}

/** Standard "this whole section isn't served by the bot yet" state. */
@Composable
fun NotLiveNote(what: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(16.dp))
            .padding(vertical = 22.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Not live yet", color = Warning, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            "$what isn't served by the bot yet. This screen will fill in automatically once it is.",
            color = TextFaint, fontSize = 11.sp, textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun Pill(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Int = 6) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF1A1A1A)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(color),
        )
    }
}

/** Selectable tab chip row used by Shop / Inventory / PvP. */
@Composable
fun TabRowChips(tabs: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tabs.size) { i ->
            val (key, label) = tabs[i]
            val active = key == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) Color.White else Color.Transparent)
                    .border(0.5.dp, if (active) Color.White else Color(0x29FFFFFF), RoundedCornerShape(999.dp))
                    .clickable { onSelect(key) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(label, color = if (active) Color.Black else Color(0x8CFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun CurrencyAmount(amount: String, gems: Boolean, size: Int = 13) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (gems) Gem(size) else Coin(size)
        Text(amount, fontWeight = FontWeight.Bold, fontSize = size.sp)
    }
}

/** Thin bordered row used for key/value facts. */
@Composable
fun FactRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextDim, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun borderedCard() = Modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(16.dp))
    .background(Color(0xFF060606))
    .border(0.5.dp, CardBorder, RoundedCornerShape(16.dp))
