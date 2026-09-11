package com.example.pdf_everything.app.router

import androidx.compose.runtime.*

/* ═══════════════════════════════════════════════════════════════════════
 *  Navigation router for PDF Everything.
 *
 *  Per spec §38‑§43, the app has these routes:
 *    - Home       (recent files, quick actions)
 *    - Viewer     (PDF viewing + annotations)
 *    - Editor     (full editing mode)
 *    - FormFill   (form field interaction)
 *    - Settings   (preferences, account)
 *    - About      (app info)
 *
 *  Phase 1 implements Home, Viewer stub, Settings stub.
 *  The router is a simple state holder; Compose navigation library
 *  integration comes later.
 * ═══════════════════════════════════════════════════════════════════════ */

// ── Route definitions ──────────────────────────────────────────────────

sealed class AppRoute {
    data object Home : AppRoute()
    data class Viewer(val documentId: String) : AppRoute()
    data class Editor(val documentId: String) : AppRoute()
    data class FormFill(val documentId: String) : AppRoute()
    data object Settings : AppRoute()
    data object About : AppRoute()
}

// ── Router state ──────────────────────────────────────────────────────

data class RouterState(
    val currentRoute: AppRoute = AppRoute.Home,
    val backStack: List<AppRoute> = emptyList(),
    val isTransitioning: Boolean = false
)

// ── Router ────────────────────────────────────────────────────────────

class AppRouter {
    private val _state = mutableStateOf(RouterState())
    val state: State<RouterState> = _state

    val currentRoute: AppRoute
        get() = _state.value.currentRoute

    fun navigate(route: AppRoute) {
        val current = _state.value.currentRoute
        if (current == route) return  // no-op for same route
        _state.value = _state.value.copy(
            backStack = _state.value.backStack + current,
            currentRoute = route,
            isTransitioning = true
        )
    }

    fun navigateAndReplace(route: AppRoute) {
        _state.value = _state.value.copy(
            currentRoute = route,
            isTransitioning = true
        )
    }

    fun popBackStack(): Boolean {
        val backStack = _state.value.backStack
        if (backStack.isEmpty()) return false
        val previous = backStack.last()
        _state.value = _state.value.copy(
            currentRoute = previous,
            backStack = backStack.dropLast(1),
            isTransitioning = true
        )
        return true
    }

    fun clearBackStack() {
        _state.value = _state.value.copy(
            backStack = emptyList()
        )
    }

    fun finishTransition() {
        _state.value = _state.value.copy(isTransitioning = false)
    }

    /**
     * Navigate back to Home, clearing the entire back stack.
     */
    fun goHome() {
        _state.value = RouterState(
            currentRoute = AppRoute.Home,
            backStack = emptyList()
        )
    }

    val canGoBack: Boolean
        get() = _state.value.backStack.isNotEmpty()
}

// ── Composition locals ─────────────────────────────────────────────────

val LocalAppRouter = staticCompositionLocalOf<AppRouter> {
    error("No AppRouter provided")
}