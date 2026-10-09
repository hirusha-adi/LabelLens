package app.labellens.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.labellens.data.GoogleResult
import app.labellens.data.GoogleSignIn
import app.labellens.data.Outcome
import app.labellens.data.Screen
import app.labellens.data.Store
import app.labellens.data.Tab
import app.labellens.ui.BottomBar
import app.labellens.ui.CheckMark
import app.labellens.ui.Field
import app.labellens.ui.FilledButton
import app.labellens.ui.Links
import app.labellens.ui.LocalStore
import app.labellens.ui.OutlineButton
import app.labellens.ui.Page
import app.labellens.ui.PlainIconButton
import app.labellens.ui.ShapeHero
import app.labellens.ui.Sym
import app.labellens.ui.T
import app.labellens.ui.TextAction
import app.labellens.ui.TopRow
import app.labellens.ui.bold
import app.labellens.ui.cs
import app.labellens.ui.openEmailApp
import app.labellens.ui.openUrl
import app.labellens.ui.statusColors
import app.labellens.ui.theme.ExpressiveShapes
import app.labellens.ui.theme.gab
import app.labellens.ui.theme.hg
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

private fun enterApp(store: Store) {
    store.tab = Tab.HOME
    store.nav.reset(Screen.Main)
}

@Composable
private fun AuthHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T(title, gab(900, 36, 44))
        if (subtitle != null) T(subtitle, hg(400, 16, 24), color = cs.onSurfaceVariant)
    }
}

@Composable
private fun PasswordToggle(shown: Boolean, onToggle: () -> Unit) {
    PlainIconButton(if (shown) "visibility_off" else "visibility", if (shown) "Hide password" else "Show password", onToggle)
}

@Composable
private fun GoogleButton(enabled: Boolean, onClick: () -> Unit) {
    OutlineButton(
        "Continue with Google", onClick, Modifier.fillMaxWidth(), enabled = enabled,
        leading = {
            Box(Modifier.size(22.dp).background(cs.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                T("G", gab(800, 13))
            }
        },
    )
}

@Composable
private fun OrDivider(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(cs.outlineVariant))
        T(text, hg(600, 13), color = cs.onSurfaceVariant)
        Box(Modifier.weight(1f).height(1.dp).background(cs.outlineVariant))
    }
}

@Composable
private fun ErrorBanner(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.errorContainer).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym("error", size = 22.dp, tint = cs.onErrorContainer, fill = true)
        T(text, hg(500, 14, 20), Modifier.weight(1f), color = cs.onErrorContainer)
    }
}

/** A05 to A07. */
@Composable
fun LoginScreen() {
    val store = LocalStore.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var shown by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Outcome.Failure?>(null) }

    fun finish(outcome: Outcome) {
        loading = false
        when (outcome) {
            is Outcome.Success -> enterApp(store)
            is Outcome.Failure -> {
                error = outcome
                // Show the password so the mistake can be seen and fixed.
                if (outcome.code == "invalid_credentials") shown = true
            }
        }
    }

    fun submit() {
        if (loading || email.isBlank() || password.isEmpty()) return
        loading = true
        error = null
        scope.launch { finish(store.login(email.trim(), password)) }
    }

    Page {
        TopRow(onBack = { store.nav.pop() })
        AuthHeader("Welcome back", "Log in to sync your preferences and history.")
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { ErrorBanner(it.message) }
            val wrongPassword = error?.code == "invalid_credentials"
            Field(
                email, { email = it; error = null }, "Email", enabled = !loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            Field(
                password, { password = it; error = null }, "Password", enabled = !loading,
                error = if (wrongPassword) "Incorrect password" else null,
                visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailing = { PasswordToggle(shown) { shown = !shown } },
            )
            Row(Modifier.fillMaxWidth().padding(top = 0.dp), horizontalArrangement = Arrangement.End) {
                TextAction("Forgot password?", { store.nav.push(Screen.Forgot) }, enabled = !loading)
            }
            FilledButton(
                "Log in", { submit() }, Modifier.fillMaxWidth(),
                enabled = loading || (email.isNotBlank() && password.isNotEmpty()), loading = loading,
            )
            OrDivider("or")
            GoogleButton(enabled = !loading) {
                loading = true
                error = null
                scope.launch {
                    when (val google = GoogleSignIn.request(context)) {
                        is GoogleResult.Token -> finish(store.loginWithGoogle(google.idToken, google.nonce))
                        GoogleResult.Cancelled -> loading = false
                        is GoogleResult.Failed -> finish(Outcome.Failure(google.message))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                T("New here?", hg(500, 15), color = cs.onSurfaceVariant)
                TextAction("Create an account", { store.nav.replace(Screen.CreateAccount) }, style = hg(700, 15), enabled = !loading)
            }
        }
    }
}

/** 0 = empty, 1 = weak … 4 = strong. */
private fun passwordStrength(password: String): Int {
    if (password.isEmpty()) return 0
    val kinds = listOf(password.any { it.isLowerCase() }, password.any { it.isUpperCase() }, password.any { it.isDigit() }, password.any { !it.isLetterOrDigit() }).count { it }
    return when {
        password.length < 8 -> 1
        password.length < 10 -> 2
        password.length >= 14 || kinds >= 3 -> 4
        else -> 3
    }
}

/** A08 and A09. */
@Composable
fun CreateAccountScreen() {
    val store = LocalStore.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var shown by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    val strength = passwordStrength(password)
    val emailError = if (submitted && !emailPattern.matches(email.trim())) "Enter a full email, like name@example.com" else null
    val confirmError = if (submitted && confirm != password) "Passwords don't match" else null
    val agreeError = submitted && !agreed
    val weak = strength in 1..1 || (submitted && strength == 0)

    fun finish(outcome: Outcome, verify: Boolean) {
        loading = false
        when (outcome) {
            is Outcome.Success -> when {
                // Email confirmation is on: wait for the link to be opened.
                store.pendingVerification != null -> store.nav.reset(Screen.VerifyEmail)
                verify -> {
                    store.draft = store.preferences
                    enterApp(store)
                    store.nav.push(Screen.PrefsStep(0))
                }
                else -> enterApp(store)
            }
            is Outcome.Failure -> failure = outcome.message
        }
    }

    fun submit() {
        submitted = true
        failure = null
        if (!emailPattern.matches(email.trim()) || strength < 2 || confirm != password || !agreed) return
        loading = true
        scope.launch { finish(store.signUp(name.trim(), email.trim(), password), verify = true) }
    }

    Page(
        bottomBar = {
            BottomBar(color = cs.background, modifier = Modifier.padding(horizontal = 8.dp)) {
                FilledButton("Create account", { submit() }, Modifier.fillMaxWidth(), loading = loading)
                Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    T("Have an account?", hg(500, 14), color = cs.onSurfaceVariant)
                    TextAction("Log in", { store.nav.replace(Screen.Login) }, height = 40.dp, enabled = !loading)
                }
            }
        },
    ) {
        TopRow(onBack = { store.nav.pop() })
        T("Create account", gab(900, 36, 44), Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp))
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            failure?.let { ErrorBanner(it) }
            GoogleButton(enabled = !loading) {
                loading = true
                failure = null
                scope.launch {
                    when (val google = GoogleSignIn.request(context)) {
                        is GoogleResult.Token -> finish(store.loginWithGoogle(google.idToken, google.nonce), verify = false)
                        GoogleResult.Cancelled -> loading = false
                        is GoogleResult.Failed -> finish(Outcome.Failure(google.message), verify = false)
                    }
                }
            }
            OrDivider("or with email")
            Field(name, { name = it }, "Name (optional)", enabled = !loading, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            Field(
                email, { email = it }, "Email", enabled = !loading, error = emailError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(
                    password, { password = it }, "Password", enabled = !loading, isError = submitted && strength < 2,
                    visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    trailing = { PasswordToggle(shown) { shown = !shown } },
                )
                if (password.isNotEmpty() || submitted) {
                    val tone = when {
                        strength <= 1 -> statusColors.avoid.color
                        strength == 2 -> statusColors.caution.color
                        else -> statusColors.safe.color
                    }
                    Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(4) { i ->
                            Box(Modifier.weight(1f).height(6.dp).background(if (i < strength.coerceAtLeast(1)) tone else cs.surfaceContainerHighest, CircleShape))
                        }
                    }
                    Row(Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Sym(if (weak) "block" else "check_circle", size = 16.dp, tint = tone, fill = true)
                        T(
                            when {
                                weak -> "Weak. Use 10+ characters, or a few unrelated words."
                                strength == 2 -> "Fair. A few more characters would help."
                                strength == 3 -> "Good password"
                                else -> "Strong password"
                            },
                            hg(600, 12, 16), color = tone,
                        )
                    }
                }
            }
            Field(
                confirm, { confirm = it }, "Confirm password", enabled = !loading, error = confirmError,
                visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailing = if (confirm.isNotEmpty() && confirm == password) ({ Sym("check_circle", size = 22.dp, tint = statusColors.safe.color, fill = true) }) else null,
            )
            Column {
                Row(Modifier.padding(start = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.padding(start = 0.dp).size(48.dp).clip(CircleShape).clickable(role = Role.Checkbox, enabled = !loading) { agreed = !agreed },
                        contentAlignment = Alignment.Center,
                    ) { CheckMark(agreed, error = agreeError) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("I agree to the", hg(400, 14, 20))
                        TextAction("Terms", { context.openUrl(Links.TERMS) }, Modifier.padding(0.dp), height = 40.dp)
                        T("and", hg(400, 14, 20))
                        TextAction("Privacy Policy", { context.openUrl(Links.PRIVACY) }, height = 40.dp)
                    }
                }
                if (agreeError) T("Please accept to continue", hg(500, 12, 16), Modifier.padding(start = 48.dp), color = cs.error)
            }
        }
    }
}

/** A10. */
@Composable
fun ForgotScreen() {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (!emailPattern.matches(email.trim())) {
            error = "Enter a full email, like name@example.com"
            return
        }
        loading = true
        scope.launch {
            val result = store.forgotPassword(email.trim())
            loading = false
            result.fold(
                onSuccess = { store.nav.replace(Screen.ResetSent(email.trim(), it.resendInSeconds)) },
                onFailure = { error = "Couldn't send the link. Check your connection and try again." },
            )
        }
    }

    Page {
        TopRow(onBack = { store.nav.pop() })
        AuthHeader("Reset your password", "Enter the email you signed up with. We'll send you a link to set a new password.")
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Field(
                email, { email = it; error = null }, "Email", enabled = !loading, error = error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            FilledButton("Send reset link", { submit() }, Modifier.fillMaxWidth(), loading = loading)
        }
    }
}

/** A11. */
@Composable
fun ResetSentScreen(email: String, resendInSeconds: Int) {
    val store = LocalStore.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var remaining by remember { mutableIntStateOf(resendInSeconds) }
    LaunchedEffect(remaining) {
        if (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
    }
    Page(
        bottomBar = {
            BottomBar(color = cs.background, modifier = Modifier.padding(horizontal = 8.dp)) {
                FilledButton("Open email app", { if (!context.openEmailApp()) store.snack("No email app found on this phone.") }, Modifier.fillMaxWidth())
                if (remaining > 0) {
                    T(
                        "Resend in %d:%02d".format(remaining / 60, remaining % 60),
                        hg(700, 14), Modifier.fillMaxWidth().padding(vertical = 14.dp), color = cs.onSurfaceVariant, align = TextAlign.Center,
                    )
                } else {
                    TextAction("Resend email", {
                        scope.launch {
                            store.forgotPassword(email).fold(
                                onSuccess = { remaining = it.resendInSeconds },
                                onFailure = { store.snack("Couldn't resend. Check your connection and try again.") },
                            )
                        }
                    }, Modifier.fillMaxWidth())
                }
            }
        },
    ) {
        TopRow(onBack = { store.nav.pop() }, backIcon = "close", backLabel = "Close")
        Column(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ShapeHero("forward_to_inbox", ExpressiveShapes.Cookie9, size = 140.dp, iconSize = 60.dp)
            T("Check your email", gab(900, 30, 38), align = TextAlign.Center)
            T(
                bold("If an account exists for **$email**, a reset link is on its way. It expires in 1 hour.", cs.onSurface),
                hg(400, 16, 24), color = cs.onSurfaceVariant, align = TextAlign.Center,
            )
        }
    }
}

/** A12: waits for the email to be confirmed, then moves on by itself. */
@Composable
fun VerifyEmailScreen() {
    val store = LocalStore.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val email = store.pendingVerification?.email.orEmpty()

    // Confirmation shows up as a log-in that starts working. Check when the user comes
    // back from their email app, and now and then while the screen stays open. The slow
    // pace is deliberate: sign-in attempts are rate limited.
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    LaunchedEffect(checks) {
        while (store.pendingVerification != null) {
            if (store.checkVerified()) {
                store.draft = store.preferences
                enterApp(store)
                store.nav.push(Screen.PrefsStep(0))
                break
            }
            delay(15_000)
        }
    }

    Page(
        bottomBar = {
            BottomBar(color = cs.background, modifier = Modifier.padding(horizontal = 8.dp)) {
                FilledButton("Open email app", { if (!context.openEmailApp()) store.snack("No email app found on this phone.") }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextAction("Resend email", {
                        scope.launch {
                            store.snack(if (store.resendVerification() is Outcome.Success) "Email sent again" else "Couldn't resend. Check your connection and try again.")
                        }
                    }, Modifier.weight(1f))
                    TextAction("Change email", {
                        store.cancelVerification()
                        store.nav.reset(Screen.Welcome)
                        store.nav.push(Screen.CreateAccount)
                    }, Modifier.weight(1f))
                }
            }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ShapeHero("mark_email_unread", ExpressiveShapes.Sunny8, size = 140.dp, iconSize = 60.dp, container = cs.tertiaryContainer, content = cs.onTertiaryContainer)
            T("Confirm your email", gab(900, 30, 38), align = TextAlign.Center)
            T(
                bold("We sent a link to **$email**. Tap it to finish setting up. This screen updates by itself.", cs.onSurface),
                hg(400, 16, 24), color = cs.onSurfaceVariant, align = TextAlign.Center,
            )
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val pulse by rememberInfiniteTransition(label = "waiting").animateFloat(
                    0.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse",
                )
                Box(Modifier.size(8.dp).alpha(pulse).background(cs.primary, CircleShape))
                T("Waiting for confirmation…", hg(600, 13), color = cs.onSurfaceVariant)
            }
        }
    }
}
