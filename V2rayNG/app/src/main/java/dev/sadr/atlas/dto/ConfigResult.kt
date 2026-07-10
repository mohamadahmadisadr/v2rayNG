package dev.sadr.atlas.dto

data class ConfigResult(
    var status: Boolean,
    var guid: String? = null,
    var content: String = "",
    var errorMessage: String = "",
)

