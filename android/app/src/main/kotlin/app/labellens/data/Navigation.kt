package app.labellens.data

import androidx.compose.runtime.mutableStateListOf

sealed interface Screen {
    /** [replay] is true when opened from the menu, so finishing goes back instead of to Welcome. */
    data class Onboarding(val replay: Boolean = false) : Screen
    data object Welcome : Screen
    data object Login : Screen
    data object CreateAccount : Screen
    data object Forgot : Screen
    data class ResetSent(val email: String, val resendInSeconds: Int) : Screen
    data object VerifyEmail : Screen
    data class PrefsStep(val step: Int) : Screen
    data object PrefsReview : Screen
    data object Main : Screen
    data object Search : Screen
    data class Crop(val uri: String) : Screen
    data object Results : Screen
    data class Detail(val code: String) : Screen
    data object Plans : Screen
    data object Compare : Screen
    data object Trial : Screen
    data object PurchaseSuccess : Screen
    data object ManageSubscription : Screen
    data object FamilyProfiles : Screen
    data object Settings : Screen
    data object RegionSettings : Screen
}

enum class Tab(val icon: String) {
    HOME("home"),
    HISTORY("history"),
    SAVED("bookmark"),
    PROFILE("person"),
}

/** A plain back stack. The last entry is the screen on show. */
class Navigator {
    val stack = mutableStateListOf<Screen>()

    val current: Screen? get() = stack.lastOrNull()

    fun push(screen: Screen) {
        if (stack.lastOrNull() != screen) stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Replaces the screen on show, so Back skips it. */
    fun replace(screen: Screen) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack.add(screen)
    }

    fun reset(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }

    /** Pops back to the newest [target] on the stack, or resets to it when it is not there. */
    fun popTo(target: Screen) {
        val index = stack.lastIndexOf(target)
        if (index < 0) reset(target) else while (stack.lastIndex > index) stack.removeAt(stack.lastIndex)
    }
}
