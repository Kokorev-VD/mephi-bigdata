package dev.kokorev.mephibigdata.service

import dev.kokorev.mephibigdata.config.MetricsProperties
import dev.kokorev.mephibigdata.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import oshi.SystemInfo
import java.io.File
import java.time.LocalDateTime

@Service
class MetricsCollectorService(
    private val metricsProperties: MetricsProperties
) {
    companion object {
        private val logger = LoggerFactory.getLogger(javaClass)
    }

    private val hal = SystemInfo().hardware
    
    fun collectMetrics(): MetricsSnapshot {
        logger.debug("Collecting system metrics...")

        return MetricsSnapshot(
            timestamp = LocalDateTime.now(),
            cpuMetrics = if (metricsProperties.enabledMetrics.cpu) collectCpuMetrics() else null,
            ramMetrics = if (metricsProperties.enabledMetrics.ram) collectRamMetrics() else null,
            diskMetrics = if (metricsProperties.enabledMetrics.disk) collectDiskMetrics() else null,
            networkMetrics = if (metricsProperties.enabledMetrics.network) collectNetworkMetrics() else null
        )
    }

    private fun collectCpuMetrics(): CpuMetrics {
        val processor = hal.processor
        val cpuLoad = processor.getSystemCpuLoadBetweenTicks(processor.systemCpuLoadTicks) * 100
        return CpuMetrics(loadPercentage = cpuLoad)
    }

    private fun collectRamMetrics(): RamMetrics {
        val memory = hal.memory
        return RamMetrics(
            totalBytes = memory.total,
            usedBytes = memory.total - memory.available,
            freeBytes = memory.available
        )
    }

    private fun collectDiskMetrics(): DiskMetrics {
        val roots = File.listRoots()

        var totalBytes = 0L
        var usedBytes = 0L
        var freeBytes = 0L
        val diskNames = mutableListOf<String>()

        roots.forEach { root ->
            val total = root.totalSpace
            val free = root.freeSpace
            val used = total - free

            if (total > 0) {
                totalBytes += total
                usedBytes += used
                freeBytes += free
                diskNames.add(root.absolutePath)
            }
        }

        return DiskMetrics(
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            freeBytes = freeBytes,
            diskName = diskNames.joinToString(", ")
        )
    }

    private fun collectNetworkMetrics(): NetworkMetrics {
        val networkIFs = hal.networkIFs
        var bytesReceived = 0L
        var bytesSent = 0L

        networkIFs.forEach { netIF ->
            netIF.updateAttributes()
            bytesReceived += netIF.bytesRecv
            bytesSent += netIF.bytesSent
        }

        return NetworkMetrics(
            bytesReceived = bytesReceived,
            bytesSent = bytesSent
        )
    }
}