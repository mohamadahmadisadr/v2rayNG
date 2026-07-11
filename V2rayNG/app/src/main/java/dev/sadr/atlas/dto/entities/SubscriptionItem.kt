package dev.sadr.atlas.dto.entities

data class SubscriptionItem(
    var remarks: String = "",
    var url: String = "",
    var enabled: Boolean = true,
    val addedTime: Long = System.currentTimeMillis(),
    var lastUpdated: Long = -1,
    var autoUpdate: Boolean = false,
    var updateInterval: Long = 1440, // in minutes, default to 24 hours
    var prevProfile: String? = null,
    var nextProfile: String? = null,
    var filter: String? = null,
    var allowInsecureUrl: Boolean = false,
    var userAgent: String? = null,
    // Usage/quota reported by the server's `subscription-userinfo` header
    // (SIP008 / Clash convention). All in bytes except [expire] (unix seconds).
    // 0 for total/expire means unlimited / never; -1 means "not reported".
    var upload: Long = -1,
    var download: Long = -1,
    var total: Long = -1,
    var expire: Long = -1,
)

