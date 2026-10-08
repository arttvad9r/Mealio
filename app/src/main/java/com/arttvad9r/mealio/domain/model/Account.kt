package com.arttvad9r.mealio.domain.model

/** Account / server information shown on the connect and settings screens. */
data class ServerAccount(
    val serverUrl: String,
    val username: String?,
    val fullName: String?,
    val household: String?,
    val mealieVersion: String?,
)
