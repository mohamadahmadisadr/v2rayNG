package dev.sadr.atlas.core.engine

/**
 * Identifies which proxy core technology the app runs on. Persisted (by [id]) so the
 * choice survives restarts and can be A/B tested. XRAY is the shipping default; SINGBOX
 * is wired but not yet backed by a native library.
 */
enum class EngineType(val id: String) {
    XRAY("xray"),
    SINGBOX("singbox");

    companion object {
        fun fromId(id: String?): EngineType = entries.firstOrNull { it.id == id } ?: XRAY
    }
}
