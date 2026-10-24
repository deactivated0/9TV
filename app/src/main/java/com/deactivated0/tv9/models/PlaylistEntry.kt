package com.deactivated0.tv9.models

import java.io.Serializable

data class PlaylistEntry(
    var id: String = "",
    var name: String = "",
    var url: String = "",
    var content: String = "",
    var enabled: Boolean = true,
) : Serializable
