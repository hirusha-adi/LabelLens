package app.labellens.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.labellens.data.Outcome
import app.labellens.data.Plan
import app.labellens.data.Screen
import app.labellens.data.Store
import app.labellens.data.Tab
import app.labellens.ui.BottomBar
import app.labellens.ui.Choice
import app.labellens.ui.ChoiceGroup
import app.labellens.ui.FilledButton
import app.labellens.ui.Links
import app.labellens.ui.LoadingShape
import app.labellens.ui.LocalStore
import app.labellens.ui.Page
import app.labellens.ui.PlainIconButton
import app.labellens.ui.SegList
import app.labellens.ui.SegRow
import app.labellens.ui.Sym
import app.labellens.ui.T
import app.labellens.ui.TextAction
import app.labellens.ui.TonalIconButton
import app.labellens.ui.TopRow
import app.labellens.ui.bold
import app.labellens.ui.cs
import app.labellens.ui.openUrl
import app.labellens.ui.theme.ExpressiveShapes
import app.labellens.ui.theme.gab
import app.labellens.ui.theme.hg
import kotlinx.coroutines.launch

/** Purchase state shared by the plans and trial screens. */
private class Checkout(val store: Store, val launch: (suspend () -> Unit) -> Unit) {
    var busy by mutableStateOf(false)
    var failure by mutableStateOf<Outcome.Failure?>(null)
    private var last: Pair<String, Boolean>? = null

    fun buy(planId: String, yearly: Boolean) {
        if (store.isGuest) {
            // A subscription needs an account to attach to.
            store.snack("Create a free account first, then start your trial.")
            store.nav.push(Screen.CreateAccount)
            return
        }
        last = planId to yearly
        busy = true
        failure = null
        launch {
            val outcome = store.purchase(planId, yearly)
            busy = false
            when (outcome) {
                is Outcome.Success -> {
                    store.nav.popTo(Screen.Main)
                    store.nav.push(Screen.PurchaseSuccess)
                }
                is Outcome.Failure -> failure = outcome
            }
        }
    }

    fun retry() = last?.let { buy(it.first, it.second) }
}

@Composable
private fun rememberCheckout(): Checkout {
    val store = LocalStore.current
    val scope = rememberCoroutineScope()
    return remember { Checkout(store) { block -> scope.launch { block() } } }
}

/** P06: the purchase did not go through. */
@Composable
private fun PurchaseFailedDialog(checkout: Checkout) {
    val failure = checkout.failure ?: return
    val context = LocalContext.current
    Dialog(onDismissRequest = { checkout.failure = null }) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHigh).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Sym(if (failure.offline) "cloud_off" else "credit_card_off", Modifier.align(Alignment.CenterHorizontally), size = 28.dp, tint = cs.error)
            T(
                if (failure.offline) "You're offline" else "Payment didn't go through",
                gab(700, 24, 32), Modifier.fillMaxWidth(), color = cs.onSurface, align = TextAlign.Center,
            )
            T(
                if (failure.offline) {
                    "Purchases need a connection. You haven't been charged. Check your connection, then try again."
                } else {
                    "Google Play couldn't charge your payment method. You haven't been charged. Update your payment details in Google Play, then try again."
                },
                hg(400, 14, 20), color = cs.onSurfaceVariant,
            )
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextAction("Not now", { checkout.failure = null }, height = 40.dp)
                    FilledButton("Try again", { checkout.retry() }, height = 40.dp, style = hg(700, 14), horizontalPadding = 16.dp)
                }
                if (!failure.offline) TextAction("Open Google Play", { context.openUrl(Links.PLAY_SUBSCRIPTIONS) }, height = 40.dp)
            }
        }
    }
}

@Composable
private fun LoadingPlans() {
    val store = LocalStore.current
    Column(
        Modifier.fillMaxWidth().padding(top = 96.dp, start = 36.dp, end = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (store.offline) {
            Sym("cloud_off", size = 40.dp, tint = cs.onSurfaceVariant)
            T("Plans need a connection", gab(800, 22, 28), align = TextAlign.Center)
            T("Prices come from Google Play. Check your connection and try again.", hg(400, 15, 22), color = cs.onSurfaceVariant, align = TextAlign.Center)
            TextAction("Try again", { store.refresh() })
        } else {
            LoadingShape(48.dp, cs.primary)
            T("Loading plans…", hg(500, 13), color = cs.onSurfaceVariant)
        }
    }
}

/** P01. */
@Composable
fun PlansScreen() {
    val store = LocalStore.current
    val checkout = rememberCheckout()
    val plans = store.plans
    var yearly by rememberSaveable { mutableStateOf(true) }
    var chosen by rememberSaveable { mutableStateOf("plus") }
    LaunchedEffect(Unit) { if (plans == null) store.refresh() }
    val currentPlan = store.user?.plan ?: "free"
    val chosenPlan = plans?.plans?.firstOrNull { it.id == chosen }
    val price = chosenPlan?.price(yearly)

    Page(
        bottomBar = if (plans == null || price == null) null else ({
            BottomBar {
                val owned = currentPlan == chosen
                FilledButton(
                    when {
                        owned -> "This is your plan"
                        store.hasPlus -> "Switch to ${chosenPlan.name}"
                        else -> "Start ${plans.trialDays}-day free trial"
                    },
                    { checkout.buy(chosen, yearly) }, Modifier.fillMaxWidth(), enabled = !owned, loading = checkout.busy, style = hg(800, 16),
                )
                T(
                    if (store.hasPlus) "${price.full}, billed through Google Play. ${plans.storeNote}"
                    else "Free until ${plans.trialEndsLabel}, then ${price.full}. Cancel before then and you won't be charged.",
                    hg(400, 12, 16), Modifier.fillMaxWidth(), color = cs.onSurfaceVariant, align = TextAlign.Center,
                )
            }
        }),
    ) {
        TopRow(onBack = { store.nav.pop() }, backIcon = "close", backLabel = "Close") {
            TextAction("Restore", { store.restorePurchases() })
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("Choose your plan", gab(900, 32, 40))
            T(plans?.storeNote ?: "Cancel any time in Google Play.", hg(400, 15, 22), color = cs.onSurfaceVariant)
        }
        if (plans == null) {
            LoadingPlans()
            return@Page
        }
        ChoiceGroup(
            listOf(Choice("monthly", "Monthly"), Choice("yearly", "Yearly · save ${plans.yearlySavePercent}%")),
            if (yearly) "yearly" else "monthly", { yearly = it == "yearly" },
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 22.dp), height = 40.dp, fill = false, checkOnSelected = true,
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            plans.plans.forEach { plan -> PlanCard(plan, yearly, selected = plan.id == chosen, current = plan.id == currentPlan) { if (plan.price(yearly) != null) chosen = plan.id } }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            TextAction("Compare all features", { store.nav.push(Screen.Compare) }, trailingIcon = "expand_more")
        }
    }
    PurchaseFailedDialog(checkout)
}

@Composable
private fun PlanCard(plan: Plan, yearly: Boolean, selected: Boolean, current: Boolean, onClick: () -> Unit) {
    val price = plan.price(yearly)
    val shape = RoundedCornerShape(28.dp)
    val content = if (selected) cs.onPrimaryContainer else cs.onSurface
    val muted = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
    Box {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(if (selected) cs.primaryContainer else cs.background)
                .border(if (selected) BorderStroke(3.dp, cs.primary) else BorderStroke(1.dp, cs.outlineVariant), shape)
                .clickable(enabled = price != null, role = Role.RadioButton, onClick = onClick).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val big = plan.recommended
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = if (big) Alignment.Top else Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T(plan.name, if (big) gab(900, 26, 32) else gab(900, 22, 28), color = content)
                    T(plan.tagline, hg(500, 14, 20), color = muted)
                }
                if (price != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            T(price.price, if (big) gab(900, 26, 32) else gab(900, 22, 28), color = content)
                            T(price.per, hg(600, 14, 26), color = content)
                        }
                        T(
                            buildAnnotatedString {
                                if (price.was != null) {
                                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(price.was) }
                                    append(" · ")
                                }
                                append(price.note)
                            },
                            hg(500, 13, 18), color = muted,
                        )
                    }
                } else if (current) {
                    T("Current plan", hg(700, 13), color = cs.onSurfaceVariant)
                }
            }
            if (plan.features.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    plan.features.forEach { feature ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Sym("check", size = 18.dp, tint = content)
                            T(feature, hg(500, 14, 20), color = content)
                        }
                    }
                }
            }
        }
        if (plan.recommended) {
            Box(
                Modifier.offset(x = 18.dp, y = (-12).dp).height(24.dp).background(cs.primary, CircleShape).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { T("RECOMMENDED", hg(800, 11, letterSpacing = 0.66), color = cs.onPrimary) }
        }
    }
}

/** P02. */
@Composable
fun CompareScreen() {
    val store = LocalStore.current
    val checkout = rememberCheckout()
    val plans = store.plans
    Page(
        bottomBar = if (plans == null || store.hasPlus) null else ({
            BottomBar {
                FilledButton("Start ${plans.trialDays}-day free trial", { checkout.buy("plus", true) }, Modifier.fillMaxWidth(), loading = checkout.busy, style = hg(800, 16))
            }
        }),
    ) {
        Row(
            Modifier.fillMaxWidth().background(cs.surfaceContainer).height(64.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlainIconButton("close", "Close", { store.nav.pop() }, tint = cs.onSurface)
            T("Compare plans", gab(700, 22, 28), Modifier.weight(1f))
        }
        if (plans == null) {
            LoadingPlans()
            return@Page
        }
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Spacer(Modifier.weight(1f))
            T("Free", gab(800, 14), Modifier.width(64.dp), align = TextAlign.Center)
            T("Plus", gab(800, 14), Modifier.width(64.dp), color = cs.primary, align = TextAlign.Center)
            T("Family", gab(800, 14), Modifier.width(64.dp), align = TextAlign.Center)
        }
        SegList {
            plans.comparison.forEachIndexed { i, row ->
                SegRow(i, plans.comparison.size, gap = 0.dp, padding = PaddingValues(start = 16.dp, top = 6.dp, bottom = 6.dp)) {
                    T(row.name, hg(600, 14, 19), Modifier.weight(1f))
                    T(row.free, hg(700, 13), Modifier.width(64.dp), color = cs.onSurfaceVariant, align = TextAlign.Center)
                    T(row.plus, hg(700, 13), Modifier.width(64.dp), color = cs.primary, align = TextAlign.Center)
                    T(row.family, hg(700, 13), Modifier.width(64.dp), align = TextAlign.Center)
                }
            }
        }
        T(plans.comparisonNote, hg(400, 12, 16), Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = cs.onSurfaceVariant)
    }
    PurchaseFailedDialog(checkout)
}

/** P03. */
@Composable
fun TrialScreen() {
    val store = LocalStore.current
    val checkout = rememberCheckout()
    val plans = store.plans
    LaunchedEffect(Unit) { if (plans == null) store.refresh() }
    // Members have nothing to try; send them to the full list of plans.
    LaunchedEffect(store.hasPlus) { if (store.hasPlus && !checkout.busy) store.nav.replace(Screen.Plans) }
    val plus = plans?.plans?.firstOrNull { it.id == "plus" }
    Page(
        background = cs.secondaryContainer,
        contentColor = cs.onSecondaryContainer,
        bottomBar = {
            BottomBar(color = cs.secondaryContainer) {
                FilledButton(
                    "Start free trial", { checkout.buy("plus", true) }, Modifier.fillMaxWidth(), enabled = plans != null, loading = checkout.busy,
                    container = cs.secondary, content = cs.onSecondary, style = hg(800, 16),
                )
                TextAction("See all plans", { store.nav.replace(Screen.Plans) }, Modifier.fillMaxWidth(), color = cs.onSecondaryContainer)
            }
        },
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TonalIconButton("close", "Close", { store.nav.pop() }, container = cs.background)
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                Modifier.size(150.dp).background(cs.secondary, ExpressiveShapes.Burst12),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                T("${plans?.trialDays ?: 7}", gab(900, 52, 52), color = cs.onSecondary)
                T("days free", gab(800, 14), color = cs.onSecondary)
            }
            T("Try Plus free for a week", gab(900, 32, 40), align = TextAlign.Center)
            T("See right away whether a product suits your allergies and diet.", hg(400, 16, 24), align = TextAlign.Center)
        }
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.background).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val steps = listOf(
                Triple("lock_open", "Today", "Unlimited scans and personal alerts"),
                Triple("notifications", "Day ${(plans?.trialDays ?: 7) - 2}", "We remind you before the trial ends"),
                Triple("event", plans?.trialEndsLabel ?: "After the trial", "${plus?.yearly?.full ?: "Your plan"} starts, unless you cancel"),
            )
            steps.forEachIndexed { i, (icon, title, text) ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(32.dp).background(if (i == 0) cs.primaryContainer else cs.surfaceContainerHighest, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Sym(icon, size = 18.dp, tint = if (i == 0) cs.onPrimaryContainer else cs.onSurface) }
                    Column {
                        T(title, hg(700, 15, 20), color = cs.onSurface)
                        T(text, hg(400, 14, 20), color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
    PurchaseFailedDialog(checkout)
}

/** P05. */
@Composable
fun PurchaseSuccessScreen() {
    val store = LocalStore.current
    val planName = if (store.isFamily) "Family" else "Plus"
    val done = {
        store.tab = Tab.HOME
        store.nav.popTo(Screen.Main)
    }
    Page(
        bottomBar = {
            BottomBar(color = cs.background) { FilledButton("Start scanning", done, Modifier.fillMaxWidth()) }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(170.dp).background(cs.secondaryContainer, ExpressiveShapes.Burst12), contentAlignment = Alignment.Center) {
                Box(Modifier.size(122.dp).background(cs.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Sym("check", size = 64.dp, tint = cs.onPrimary)
                }
            }
            T("Welcome to $planName", gab(900, 32, 40), align = TextAlign.Center)
            T(
                (if (store.trialEndsLabel.isNotBlank()) "Your trial runs until ${store.trialEndsLabel}. " else "") +
                    "Personal alerts now appear on every scan and additive.",
                hg(400, 16, 24), color = cs.onSurfaceVariant, align = TextAlign.Center,
            )
        }
        SegList(Modifier.padding(top = 28.dp)) {
            SegRow(0, 2, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), onClick = {
                store.draft = store.preferences
                done()
                store.nav.push(Screen.PrefsStep(0))
            }) {
                Sym("tune", tint = cs.primary)
                T("Set up your preferences", hg(700, 15), Modifier.weight(1f))
                Sym("chevron_right", tint = cs.onSurfaceVariant)
            }
            SegRow(1, 2, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Sym("history", tint = cs.primary)
                T("Your next scans are saved to History", hg(700, 15), Modifier.weight(1f))
            }
        }
    }
}

/** P07. */
@Composable
fun ManageSubscriptionScreen() {
    val store = LocalStore.current
    val context = LocalContext.current
    val subscription = store.subscription
    Page {
        TopRow(onBack = { store.nav.pop() })
        T("Subscription", gab(900, 36, 44), Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp))
        if (subscription == null) {
            T(
                "You're on the Free plan: 3 scans a day, search and basic additive info.",
                hg(400, 15, 22), Modifier.padding(horizontal = 24.dp), color = cs.onSurfaceVariant,
            )
            FilledButton("See plans", { store.nav.replace(Screen.Plans) }, Modifier.padding(start = 24.dp, top = 20.dp))
            return@Page
        }
        Column(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(cs.primaryContainer).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(subscription.title, gab(900, 28, 34), Modifier.weight(1f), color = cs.onPrimaryContainer)
                Box(Modifier.height(28.dp).background(cs.primary, CircleShape).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    T(subscription.status, hg(700, 12), color = cs.onPrimary)
                }
            }
            T(
                bold("Renews on **${subscription.renewsOn}** for ${subscription.price}.\nBilled through Google Play to ${subscription.billedTo}"),
                hg(500, 15, 22), color = cs.onPrimaryContainer,
            )
        }
        SegList(Modifier.padding(top = 16.dp)) {
            SegRow(0, 4, minHeight = 64.dp, onClick = { store.nav.push(Screen.Plans) }) {
                Sym("swap_vert", tint = cs.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    T("Change plan", hg(700, 16, 22))
                    T("Switch to Family or Monthly", hg(400, 14, 20), color = cs.onSurfaceVariant)
                }
                Sym("chevron_right", tint = cs.onSurfaceVariant)
            }
            SegRow(1, 4, minHeight = 64.dp, onClick = { store.restorePurchases() }) {
                Sym("restore", tint = cs.onSurfaceVariant)
                T("Restore purchases", hg(700, 16), Modifier.weight(1f))
            }
            SegRow(2, 4, minHeight = 64.dp, onClick = { context.openUrl(Links.PLAY_ORDER_HISTORY) }) {
                Sym("receipt_long", tint = cs.onSurfaceVariant)
                T("Payment history", hg(700, 16), Modifier.weight(1f))
                Sym("open_in_new", size = 20.dp, tint = cs.onSurfaceVariant)
            }
            SegRow(3, 4, minHeight = 64.dp, onClick = { context.openUrl(subscription.manageUrl.ifBlank { Links.PLAY_SUBSCRIPTIONS }) }) {
                Sym("cancel", tint = cs.error)
                Column(Modifier.weight(1f)) {
                    T("Cancel subscription", hg(700, 16, 22), color = cs.error)
                    T("You keep ${subscription.title.substringBefore(" ·")} until ${subscription.renewsOn}", hg(400, 14, 20), color = cs.onSurfaceVariant)
                }
                Sym("open_in_new", size = 20.dp, tint = cs.onSurfaceVariant)
            }
        }
        T(subscription.note, hg(400, 13, 18), Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp), color = cs.onSurfaceVariant)
    }
}
