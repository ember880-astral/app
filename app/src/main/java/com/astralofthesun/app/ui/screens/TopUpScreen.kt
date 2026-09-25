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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astralofthesun.app.data.Astral
import com.astralofthesun.app.data.ReqStatus
import com.astralofthesun.app.ui.components.Coin
import com.astralofthesun.app.ui.components.EmptyNote
import com.astralofthesun.app.ui.components.Gem
import com.astralofthesun.app.ui.components.SectionHeader
import com.astralofthesun.app.ui.components.cardModifier
import com.astralofthesun.app.ui.theme.Gold
import com.astralofthesun.app.ui.theme.Primary
import com.astralofthesun.app.ui.theme.TextDim

@Composable
fun TopUpScreen(initialCurrency: String = "solars") {
    var mode by remember { mutableStateOf(if (initialCurrency == "gems") "gems" else "solars") }
    var amount by remember { mutableStateOf("") }
    var flash by remember { mutableStateOf("") }

    val packages = if (mode == "gems") Astral.topUp.packages.gems else Astral.topUp.packages.solars

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("Top-up", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Solars & Gems top-up · Premium · Server offers", color = TextDim, fontSize = 12.sp)
            }
        }

        // currency top-up card
        item {
            Column(modifier = cardModifier().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Top up currency", "Solars & Gems")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CurrencyChip("solars", "Solars", mode) { mode = it }
                    CurrencyChip("gems", "Gems", mode) { mode = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("Amount", color = TextDim) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            val n = amount.toLongOrNull()
                            if (n == null || n < 1) { flash = "Enter an amount first."; return@Button }
                            Astral.topUp.create(mode, amount = n)
                            flash = "Top-up request created — $n ${if (mode == "gems") "Gems" else "Solars"}."
                            amount = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) { Text("Create top-up") }
                }
                if (flash.isNotEmpty()) Text(flash, color = TextDim, fontSize = 11.sp)
                if (packages.isEmpty()) {
                    EmptyNote(if (mode == "gems") "No Gems packages yet — connect a backend" else "No Solars packages yet — connect a backend")
                }
            }
        }

        // premium
        item {
            Column(modifier = cardModifier().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Premium", "Battle pass Premium")
                if (Astral.topUp.premium.isEmpty()) EmptyNote("No Premium packs yet — connect a backend")
                OutlinedButton(onClick = { Astral.topUp.create("premium") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Buy Premium")
                }
            }
        }

        // server offers
        item { SectionHeader("Server Offers", "${Astral.topUp.offers.size} offers") }
        item { if (Astral.topUp.offers.isEmpty()) EmptyNote("No server offers right now") }

        // requests
        item { SectionHeader("Your requests", "${Astral.topUp.requests.size} requests") }
        item {
            if (Astral.topUp.requests.isEmpty()) EmptyNote("No requests yet")
            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Astral.topUp.requests.forEach { req -> RequestRow(req.kind, req.status, req.amount, req.id) }
            }
        }
    }
}

@Composable
private fun RowScope.CurrencyChip(value: String, label: String, current: String, onSelect: (String) -> Unit) {
    val active = current == value
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) Color.White else Color.Transparent)
            .border(0.5.dp, if (active) Color.White else Color(0x29FFFFFF), RoundedCornerShape(999.dp))
            .clickable { onSelect(value) }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) Color.Black else Color(0x8CFFFFFF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RequestRow(kind: String, status: ReqStatus, amount: Long?, id: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A0A0A))
            .border(0.5.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("${kind.replaceFirstChar { it.uppercase() }} top-up", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(buildList {
                add(id)
                if (amount != null) add("amount $amount")
            }.joinToString(" · "), color = TextDim, fontSize = 10.sp)
        }
        val label = when (status) {
            ReqStatus.Fulfilled -> "fulfilled"
            ReqStatus.Failed -> "failed"
            ReqStatus.Processing -> "processing"
            ReqStatus.AwaitingBackend -> "awaiting backend"
            else -> "created"
        }
        Text(label, color = if (status == ReqStatus.Fulfilled) Gold else TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
