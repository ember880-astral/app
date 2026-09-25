package com.astralofthesun.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Phone
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

private enum class LoginStep { Identifier, Confirm, Code, Register }

// Same palette as the rest of the app (ui/theme/Theme.kt): pure black
// background, near-black cards, 10% white hairlines, gold accents.
private val FormColor = Color(0xFF060606)
private val FieldColor = Color.Black
private val Hairline = Color(0x1AFFFFFF)
private val MutedText = Color(0x99FFFFFF)

// Character creation options (match the bot's class/race ids).
private val CLASSES = listOf("warrior", "mage", "rogue", "assassin", "knight", "cleric", "samurai", "berserker", "duelist")
private val RACES = listOf("human", "elf", "orc", "dwarf", "halfling")

/**
 * Login flow backed by the bot's WhatsApp-OTP auth:
 * lookup (username/character name) → confirm (masked phone) →
 * code from the bot's WhatsApp DM → JWT. Brand-new phones get a
 * character-creation step (needsRegistration) before entering the app.
 */
@Composable
fun LoginFlow() {
    var step by rememberSaveable { mutableStateOf(LoginStep.Identifier) }
    var identifier by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var maskedPhone by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableStateOf("") }
    var phoneMode by rememberSaveable { mutableStateOf(false) }
    var phone by rememberSaveable { mutableStateOf("") }
    // Codes deliberately remain in memory only, not saved instance state.
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var resendSeconds by rememberSaveable { mutableStateOf(0) }
    // Character creation (needsRegistration path).
    var regName by rememberSaveable { mutableStateOf("") }
    var regClass by rememberSaveable { mutableStateOf("warrior") }
    var regRace by rememberSaveable { mutableStateOf("human") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(resendSeconds) {
        if (resendSeconds > 0) { delay(1000); resendSeconds-- }
    }

    fun changeAccount() {
        step = LoginStep.Identifier
        code = ""
        name = ""
        maskedPhone = ""
        avatar = ""
        phoneMode = false
        error = null
        notice = null
        AuthState.pendingHandle.value = null
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
                maskedPhone = profile.sub
                avatar = profile.avatar
                step = LoginStep.Confirm
            },
            onFailure = { e ->
                // Unknown name (404 "No character goes by that name." or a bare
                // 404) → offer the new-player WhatsApp-number path instead.
                val msg = e.userMessage()
                val notFound = e.isNotLive || msg.contains("no character", ignoreCase = true) ||
                    msg.contains("not found", ignoreCase = true)
                if (notFound) {
                    error = "No character goes by that name."
                    phoneMode = true
                } else {
                    error = msg
                }
            },
        )
    }

    fun sendCode() = perform {
        val handle = AuthState.pendingHandle.value
        if (handle == null) { error = "Look up your account first."; return@perform }
        Repository.requestOtp(handle).fold(
            onSuccess = {
                code = ""
                step = LoginStep.Code
                resendSeconds = 30
                notice = "Code sent. Check your WhatsApp DMs from the Astral bot."
            },
            onFailure = { e -> error = "Couldn’t send the code: ${e.userMessage()}" },
        )
    }

    fun sendCodeForPhone() = perform {
        Repository.requestOtpForPhone(phone.trim()).fold(
            onSuccess = {
                code = ""
                name = ""
                step = LoginStep.Code
                resendSeconds = 30
                notice = "Code sent. Check your WhatsApp DMs from the Astral bot."
            },
            onFailure = { e -> error = "Couldn’t send the code: ${e.userMessage()}" },
        )
    }

    fun verifyCode() = perform {
        Repository.verifyOtp(code.trim()).fold(
            onSuccess = {
                if (AuthState.needsRegistration.value) {
                    // Brand-new phone: create the character, then the app opens.
                    regName = name.ifBlank { identifier }
                    step = LoginStep.Register
                }
                // Otherwise AuthState.isLoggedIn flipped — the app gate opens by itself.
            },
            onFailure = { e -> error = e.userMessage() },
        )
    }

    fun createCharacter() = perform {
        if (regName.isBlank()) { error = "Pick a character name first."; return@perform }
        Repository.register(regName, regClass, regRace).onFailure { e ->
            error = "Couldn’t create your character: ${e.userMessage()}"
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
                        LoginStep.Register -> "Create your character"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when (step) {
                        LoginStep.Identifier -> if (phoneMode)
                            "No character found for that name. Enter your WhatsApp number to get a code and start fresh."
                        else "Enter your username or character name."
                        LoginStep.Confirm -> "Confirm it’s your number, then send a login code."
                        LoginStep.Code -> "Enter the 6-digit code the bot DM’d you on WhatsApp."
                        LoginStep.Register -> "Your number is new here — pick a name, class and race to begin."
                    },
                    color = MutedText, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))

                if (step == LoginStep.Confirm || (step == LoginStep.Code && name.isNotBlank())) {
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
                    if (maskedPhone.isNotBlank()) Text(maskedPhone, color = MutedText, textAlign = TextAlign.Center)
                }

                when (step) {
                    LoginStep.Identifier -> if (phoneMode) {
                        LoginField(phone, "WhatsApp number (e.g. +234…)", busy, isCode = false, isPhone = true,
                            onChange = { phone = it; error = null },
                            onSubmit = { if (phone.isNotBlank() && !busy) sendCodeForPhone() })
                    } else {
                        LoginField(identifier, "Username or character name", busy, isCode = false, isPhone = false,
                            onChange = { identifier = it; error = null },
                            onSubmit = { if (identifier.isNotBlank() && !busy) findAccount() })
                    }
                    LoginStep.Code -> LoginField(code, "6-digit code", busy, isCode = true, isPhone = false,
                        onChange = { code = it; error = null },
                        onSubmit = { if (code.isNotBlank() && !busy) verifyCode() })
                    LoginStep.Register -> LoginField(regName, "Character name", busy, isCode = false, isPhone = false,
                        onChange = { regName = it; error = null },
                        onSubmit = {})
                }

                if (step == LoginStep.Register) {
                    OptionRow("Class", CLASSES, regClass) { regClass = it }
                    OptionRow("Race", RACES, regRace) { regRace = it }
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
                            LoginStep.Identifier -> if (phoneMode) sendCodeForPhone() else findAccount()
                            LoginStep.Confirm -> sendCode()
                            LoginStep.Code -> verifyCode()
                            LoginStep.Register -> createCharacter()
                        }
                    },
                    enabled = !busy && when (step) {
                        LoginStep.Identifier -> if (phoneMode) phone.isNotBlank() else identifier.isNotBlank()
                        LoginStep.Confirm -> true
                        LoginStep.Code -> code.isNotBlank()
                        LoginStep.Register -> regName.isNotBlank()
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
                        LoginStep.Identifier -> if (phoneMode) "Send code" else "Find my account"
                        LoginStep.Confirm -> "Send code"
                        LoginStep.Code -> "Log in"
                        LoginStep.Register -> "Create character"
                    })
                }
                if (step == LoginStep.Code) {
                    TextButton(onClick = { if (phoneMode) sendCodeForPhone() else sendCode() }, enabled = !busy && resendSeconds == 0) {
                        Text(if (resendSeconds > 0) "Resend code in ${resendSeconds}s" else "Resend code", color = MutedText)
                    }
                }
                if (step == LoginStep.Identifier && phoneMode) {
                    TextButton(onClick = { phoneMode = false; error = null }, enabled = !busy) {
                        Text("Back to username lookup", color = MutedText)
                    }
                }
                if (step != LoginStep.Identifier && step != LoginStep.Register) {
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
private fun OptionRow(label: String, options: List<String>, selected: String, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MutedText)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onPick(option) },
                    label = { Text(option.replaceFirstChar { it.uppercase() }) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Gold,
                        selectedLabelColor = Color.Black,
                        containerColor = FieldColor,
                        labelColor = MutedText,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = Hairline,
                        selectedBorderColor = Gold,
                        enabled = true,
                        selected = option == selected,
                    ),
                )
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
    isPhone: Boolean,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    TextField(
        value = value, onValueChange = onChange, enabled = !busy, singleLine = true,
        label = { Text(label) },
        leadingIcon = {
            Icon(when { isCode -> Icons.Default.Lock; isPhone -> Icons.Default.Phone; else -> Icons.Default.Person }, null)
        },
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
            keyboardType = when { isCode -> KeyboardType.NumberPassword; isPhone -> KeyboardType.Phone; else -> KeyboardType.Text },
            autoCorrect = false, imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
    )
}
