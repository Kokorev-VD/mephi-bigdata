package dev.kokorev.mephibigdata.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["metrics.enabled"], havingValue = "true", matchIfMissing = false)
class MetricsScheduler(
    private val metricsCollectorService: MetricsCollectorService,
    private val natsProducerService: NatsProducerService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(javaClass)
    }

    @Scheduled(fixedDelayString = "\${metrics.collection-interval-ms}")
    fun collectAndLogMetrics() {
        val snapshot = metricsCollectorService.collectMetrics()
        val metricsString = snapshot.toString()

        logger.info(metricsString)

        natsProducerService.publishMetrics(snapshot)
    }
}