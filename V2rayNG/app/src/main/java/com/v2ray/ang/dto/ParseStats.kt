package com.v2ray.ang.dto

data class ParseStats(
    var total: Int = 0,
    var success: Int = 0,
    var skippedMalformed: Int = 0,
    var skippedDuplicate: Int = 0,
    var skippedTcpFail: Int = 0
) {
    fun getSummary(): String {
        val malformedPercent = if (total > 0) (skippedMalformed.toFloat() / total * 100).toInt() else 0
        return "Parsed $success configs, skipped $skippedMalformed malformed ($malformedPercent%), $skippedDuplicate duplicates, $skippedTcpFail TCP failures. Total processed: $total"
    }
}
