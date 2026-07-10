package dev.sadr.atlas.dto

import dev.sadr.atlas.dto.entities.ProfileItem

data class LastWorkingConfig(
    val profile: ProfileItem,
    val host: String,
    val port: Int,
    val lastVerified: Long,
    val latencyMs: Long,
    var failCount: Int = 0
)
