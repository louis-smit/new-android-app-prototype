package no.solver.solverappdemo.data.models

import kotlinx.serialization.Serializable

@Serializable
data class ResourceIcon(
    val id: Int,
    val base64Data: String
)
