package dev.sadr.atlas.dto.entities

data class ServerAffiliationInfo(var testDelayMillis: Long = 0L) {
    fun getTestDelayString(): String {
        return if (testDelayMillis == 0L) {
            "..."
        } else if (testDelayMillis == UNSUPPORTED) {
            "N/A"
        } else if (testDelayMillis < 0L) {
            "timeout"
        } else {
            testDelayMillis.toString() + "ms"
        }
    }

    companion object {
        /** Sentinel test-delay value: the profile can't run on the active engine (e.g. an
         *  xray-only protocol/transport under the sing-box engine). Rendered as "N/A". */
        const val UNSUPPORTED = -3L
    }
}