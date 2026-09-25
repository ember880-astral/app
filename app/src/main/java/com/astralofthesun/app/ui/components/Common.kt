package com.astralofthesun.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.astralofthesun.app.ui.theme.CardBorder
import com.astralofthesun.app.ui.theme.Gold
import com.astralofthesun.app.ui.theme.GoldDark
import com.astralofthesun.app.ui.theme.TextDim
import com.astralofthesun.app.ui.theme.TextFaint

/* Dashed "nothing here yet" placeholder, matching the web empty-note. */
@Composable
fun EmptyNote(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(16.dp))
            .padding(vertical = 22.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TextFaint, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionHeader(title: String, meta: String = "") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (meta.isNotEmpty()) Text(meta, fontSize = 11.sp, color = TextDim)
    }
}

/* Gold $ coin, matching Astral.coin() on the web. */
@Composable
fun Coin(size: Int = 12) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Gold),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size((size * 0.7).dp)
                .clip(CircleShape)
                .background(GoldDark),
        )
    }
}

/* Gem diamond outline, matching the web gem icon. */
@Composable
fun Gem(size: Int = 12) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, Color(0x807ADEFF), RoundedCornerShape(2.dp))
            .background(Color(0x407ADEFF)),
    )
}

/* Card surface used across screens, matching the web cards. */
fun cardModifier() = Modifier
    .fillMaxWidth()
    .clip(RoundedCornerShape(20.dp))
    .background(Color(0xFF060606))
    .border(0.5.dp, CardBorder, RoundedCornerShape(20.dp))
