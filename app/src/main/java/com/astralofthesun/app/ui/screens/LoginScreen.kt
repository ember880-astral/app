package com.astralofthesun.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.astralofthesun.app.network.ApiConfig
import com.astralofthesun.app.network.AuthState
import com.astralofthesun.app.network.Repository
import com.astralofthesun.app.network.isNotLive
import com.astralofthesun.app.network.userMessage
import com.astralofthesun.app.ui.theme.Gold
import com.astralofthesun.app.ui.theme.Danger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class LoginStep { Identifier, Confirm, Code }

// Same palette as the rest of the app (ui/theme/Theme.kt): pure black
// background, near-black cards, 10% white hairlines, gold accents.
private val FormColor = Color(0xFF060606)
private val FieldColor = Color.Black
private val Hairline = Color(0x1AFFFFFF)
private val MutedText = Color(0x99FFFFFF)

/** Login flow (find account → confirm → code), styled with the app's black/gold palette. */
@Composable
fun LoginFlow() {
    var step by rememberSaveable { mutableStateOf(LoginStep.Identifier) }
    var identifier by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableStateOf("") }
    // Codes deliberately remain in memory only, not saved instance state.
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var resendSeconds by rememberSaveable { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(resendSeconds) {
        if (resendSeconds > 0) { delay(1000); resendSeconds-- }
    }

    fun changeAccount() {
        step = LoginStep.Identifier
        code = ""
        name = ""
        avatar = ""
        error = null
        notice = null
        AuthState.pendingIdentifier.value = null
    }

    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        notice = null
        scope.launch { try { action() } finally { busy = false } }
    }

    fun findAccount() = perform {
        Repository.lookup(identifier.trim()).fold(
            onSuccess = {
                val profile = Repository.lookupProfile(it)
                identifier = identifier.trim()
                name = profile.name.ifBlank { identifier }
                avatar = profile.avatar
                step = LoginStep.Confirm
            },
            onFailure = { e ->
                // The bot answers unknown accounts with "Not found." (as a 404
                // or an ok:false body) — turn that into a readable message.
                val msg = e.userMessage()
                error = if (e.isNotLive || msg.contains("not found", ignoreCase = true) ||
                    msg.contains("no account", ignoreCase = true)
                ) "No account found with that username. Check the spelling and try again."
                else msg
            },
        )
    }

    fun sendCode() = perform {
        Repository.requestOtp(identifier).fold(
            onSuccess = {
                code = ""
                step = LoginStep.Code
                resendSeconds = 30
                notice = "Code sent. Check your Discord DMs from the bot for the login code."
            },
            onFailure = { e ->
                val msg = e.userMessage()
                error = if (e.isNotLive || msg.contains("not found", ignoreCase = true))
                    "No account found with that username. Check the spelling and try again."
                else "Couldn’t send the code: $msg"
            },
        )
    }

    fun verifyCode() = perform {
        Repository.verifyOtp(code.trim(), identifier).onFailure { e ->
            error = e.userMessage()
        }
    }

    BackHandler(enabled = step != LoginStep.Identifier) { if (!busy) changeAccount() }

    Surface(color = Color.Black, contentColor = Color.White) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 400.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(25.dp))
                    .background(FormColor)
                    .border(1.dp, Hairline, RoundedCornerShape(25.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(12.dp))
                Text("ASTRAL OF THE SUN", style = MaterialTheme.typography.labelMedium, color = Gold)
                Text(
                    when (step) {
                        LoginStep.Identifier -> "Log in"
                        LoginStep.Confirm -> "Is this you?"
                        LoginStep.Code -> "Enter your code"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when (step) {
                        LoginStep.Identifier -> "Find your account to get started."
                        LoginStep.Confirm -> "Confirm your account, then send a login code."
                        LoginStep.Code -> "Enter the login code the bot sent you."
                    },
                    color = MutedText, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))

                if (step != LoginStep.Identifier) {
                    Box(
                        Modifier.size(76.dp).clip(CircleShape)
                            .background(FieldColor)
                            .border(1.dp, Hairline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Person, "Profile picture placeholder", Modifier.size(36.dp), tint = MutedText)
                        if (avatar.isNotBlank()) {
                            val url = if (avatar.startsWith("https://") || avatar.startsWith("http://")) avatar
                                else ApiConfig.BASE_URL.trimEnd('/') + "/" + avatar.trimStart('/')
                            AsyncImage(url, "Profile picture for $name", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                    Text(name, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (name != identifier) Text(identifier, color = MutedText, textAlign = TextAlign.Center)
                }

                if (step == LoginStep.Identifier) {
                    LoginField(identifier, "Username", busy, false,
                        onChange = { identifier = it; error = null },
                        onSubmit = { if (identifier.isNotBlank() && !busy) findAccount() })
                } else if (step == LoginStep.Code) {
                    LoginField(code, "Login code", busy, true,
                        onChange = { code = it; error = null },
                        onSubmit = { if (code.isNotBlank() && !busy) verifyCode() })
                }

                error?.let {
                    Text(it, color = Danger, textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                notice?.let {
                    Text(it, color = MutedText, textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        when (step) {
                            LoginStep.Identifier -> findAccount()
                            LoginStep.Confirm -> sendCode()
                            LoginStep.Code -> verifyCode()
                        }
                    },
                    enabled = !busy && when (step) {
                        LoginStep.Identifier -> identifier.isNotBlank()
                        LoginStep.Confirm -> true
                        LoginStep.Code -> code.isNotBlank()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold, contentColor = Color.Black,
                        disabledContainerColor = Gold.copy(alpha = 0.35f),
                        disabledContentColor = Color(0x99FFFFFF),
                    ),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    else Text(when (step) {
                        LoginStep.Identifier -> "Find my account"
                        LoginStep.Confirm -> "Send code"
                        LoginStep.Code -> "Log in"
                    })
                }
                if (step == LoginStep.Code) {
                    TextButton(onClick = { sendCode() }, enabled = !busy && resendSeconds == 0) {
                        Text(if (resendSeconds > 0) "Resend code in ${resendSeconds}s" else "Resend code", color = MutedText)
                    }
                }
                if (step != LoginStep.Identifier) {
                    TextButton(onClick = { changeAccount() }, enabled = !busy) {
                        Text("Use another account", color = MutedText)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun LoginField(
    value: String,
    label: String,
    busy: Boolean,
    isCode: Boolean,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    TextField(
        value = value, onValueChange = onChange, enabled = !busy, singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(if (isCode) Icons.Default.Lock else Icons.Default.Person, null) },
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
            .background(FieldColor)
            .border(1.dp, Hairline, RoundedCornerShape(25.dp)),
        shape = RoundedCornerShape(25.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedLabelColor = Gold, unfocusedLabelColor = MutedText,
            focusedLeadingIconColor = Gold, unfocusedLeadingIconColor = MutedText,
            cursorColor = Gold,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isCode) KeyboardType.Ascii else KeyboardType.Text,
            autoCorrect = false, imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
    )
}
