package no.solver.solverappdemo.ui.navigation

import kotlinx.serialization.Serializable

sealed interface NavRoute {
    @Serializable
    data object Splash : NavRoute

    @Serializable
    data object Login : NavRoute

    @Serializable
    data object MobileLogin : NavRoute

    @Serializable
    data class AddAccountLogin(val provider: String = "microsoft") : NavRoute

    @Serializable
    data object Main : NavRoute

    @Serializable
    data object Objects : NavRoute

    @Serializable
    data object Find : NavRoute

    @Serializable
    data object Accounts : NavRoute

    @Serializable
    data object More : NavRoute

    @Serializable
    data class ObjectDetail(val objectId: Int) : NavRoute

    // More sub-screens
    @Serializable
    data object Payments : NavRoute

    @Serializable
    data object Visit : NavRoute

    @Serializable
    data object Logs : NavRoute

    @Serializable
    data object Debug : NavRoute

    @Serializable
    data object DanalockDemo : NavRoute

    @Serializable
    data object MasterlockDemo : NavRoute
}
