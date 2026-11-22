package com.jupiter.app.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data object StreamsRoute

@Serializable
data object SettingsRoute

@Serializable
data class PlayerRoute(
    val mediaUri: String,
    val title: String = "",
    val isNetworkStream: Boolean = false,
    val headersJson: String? = null,
)
