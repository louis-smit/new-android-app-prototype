package no.solver.solverapp.ui.navigation

import kotlinx.serialization.Serializable

sealed interface NavRoute {
    @Serializable
    data object Splash : NavRoute

    @Serializable
    data object Login : NavRoute

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
}
