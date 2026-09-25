package com.astralofthesun.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
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
import com.astralofthesun.app.data.ShopItem
import com.astralofthesun.app.ui.components.Coin
import com.astralofthesun.app.ui.components.EmptyNote
import com.astralofthesun.app.ui.components.Gem
import com.astralofthesun.app.ui.theme.TextDim

@Composable
fun ShopScreen() {
    val wallet by Astral.wallet
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("") }

    val all = Astral.shop.toList()
    val filtered = all.filter { cat.isEmpty() || it.category == cat }
        .filter { query.isEmpty() || it.name.contains(query, true) || it.desc.contains(query, true) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // total currency bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF060606))
                .border(0.5.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Total", color = TextDim, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(wallet.solars?.toString() ?: "0", fontWeight = FontWeight.Bold)
                Coin(14)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(wallet.gems?.toString() ?: "0", fontWeight = FontWeight.Bold)
                Gem(14)
            }
        }

        // search
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search items…", color = TextDim) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // category chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("", "All", cat) { cat = it }
            FilterChip("weapons", "Weapons", cat) { cat = it }
            FilterChip("gear", "Gear", cat) { cat = it }
            FilterChip("items", "Items", cat) { cat = it }
            FilterChip("pokemon", "Pokémon", cat) { cat = it }
        }

        // items
        if (filtered.isEmpty()) {
            EmptyNote("No items match")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(filtered) { ItemCard(it) }
            }
        }
    }
}

@Composable
private fun FilterChip(value: String, label: String, current: String, onSelect: (String) -> Unit) {
    val active = current == value
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) Color.White else Color.Transparent)
            .border(0.5.dp, if (active) Color.White else Color(0x29FFFFFF), RoundedCornerShape(999.dp))
            .clickable { onSelect(value) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (active) Color.Black else Color(0x8CFFFFFF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ItemCard(item: ShopItem) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF060606))
            .border(0.5.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF111111)),
        )
        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(item.desc, color = TextDim, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (item.currency == "gems") Gem(13) else Coin(13)
            Text(item.price?.toString() ?: "", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
