package dev.kokorev.mephibigdata.service

import com.fasterxml.jackson.databind.ObjectMapper
import dev.kokorev.mephibigdata.config.NatsProperties
import dev.kokorev.mephibigdata.model.MetricsSnapshot
import io.nats.client.Connection
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NatsProducerService(
    private val natsConnection: Connection,
    private val natsProperties: NatsProperties,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private val logger = LoggerFactory.getLogger(javaClass)
    }

    private val jetStream by lazy {
        natsConnection.jetStream()
    }

    fun publishMetrics(metrics: MetricsSnapshot) {
        try {
            val jsonPayload = objectMapper.writeValueAsString(metrics)

            jetStream.publish(
                natsProperties.subject,
                jsonPayload.toByteArray()
            )

            logger.debug(
                "Produced message to JetStream. $jsonPayload"
            )
        } catch (e: Exception) {
            logger.error("Error while producing message to JetStream", e)
        }
    }
}
