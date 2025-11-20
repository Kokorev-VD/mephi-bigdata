package dev.kokorev.mephibigdata.model

import java.time.LocalDateTime

data class MetricsSnapshot(
    val timestamp: LocalDateTime,
    val cpuMetrics: CpuMetrics?,
    val ramMetrics: RamMetrics?,
    val diskMetrics: DiskMetrics?,
    val networkMetrics: NetworkMetrics?
) {
    override fun toString(): String =
        """
            Timestamp: $timestamp
            CPU: ${cpuMetrics?.loadPercentage}
            RAM: total: ${ramMetrics?.totalBytes} used: ${ramMetrics?.usedBytes} free: ${ramMetrics?.freeBytes}
            DISK: total: ${diskMetrics?.totalBytes} used: ${diskMetrics?.usedBytes} free: ${diskMetrics?.freeBytes}
            NET: send: ${networkMetrics?.bytesSent} rec: ${networkMetrics?.bytesReceived}
        """
}

data class CpuMetrics(
    val loadPercentage: Double,
)

data class RamMetrics(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long
)

data class DiskMetrics(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val diskName: String
)

data class NetworkMetrics(
    val bytesReceived: Long,
    val bytesSent: Long,
)