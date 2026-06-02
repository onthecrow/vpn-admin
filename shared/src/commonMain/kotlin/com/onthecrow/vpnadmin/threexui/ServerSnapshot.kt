package com.onthecrow.vpnadmin.threexui

import kotlinx.serialization.Serializable

/** Subset of `GET /panel/api/server/status` we care about for the chip + detail dialog. */
@Serializable
data class ServerSnapshot(
    val cpuPercent: Double,
    val memUsedBytes: Long,
    val memTotalBytes: Long,
    val swapUsedBytes: Long,
    val swapTotalBytes: Long,
    val diskUsedBytes: Long,
    val diskTotalBytes: Long,
    val netUpBytes: Long,
    val netDownBytes: Long,
    val xrayState: String,
    val xrayVersion: String,
    val tcpCount: Long,
    val load1: Double,
    val load5: Double,
    val load15: Double,
) {
    val memPercent: Double get() = if (memTotalBytes > 0) memUsedBytes * 100.0 / memTotalBytes else 0.0
    val diskPercent: Double get() = if (diskTotalBytes > 0) diskUsedBytes * 100.0 / diskTotalBytes else 0.0
}
