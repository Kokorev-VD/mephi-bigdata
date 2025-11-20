package dev.kokorev.mephibigdata.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.nats.client.Connection
import io.nats.client.Nats
import io.nats.client.Options
import io.nats.client.api.StorageType
import io.nats.client.api.StreamConfiguration
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class NatsConfiguration(
    private val natsProperties: NatsProperties
) {
    companion object {
        private val logger = LoggerFactory.getLogger(javaClass)
    }

    private var natsConnection: Connection? = null

    @Bean
    fun natsConnection(): Connection {
        logger.info("Connecting to NATS at ${natsProperties.url}")

        val options = Options.Builder()
            .server(natsProperties.url)
            .maxReconnects(-1)
            .connectionTimeout(java.time.Duration.ofSeconds(10))
            .reconnectWait(java.time.Duration.ofSeconds(2))
            .build()

        var connection: Connection? = null
        var attempts = 0
        val maxAttempts = 30

        while (connection == null && attempts < maxAttempts) {
            try {
                attempts++
                logger.info("Attempting to connect to NATS (attempt $attempts/$maxAttempts)...")
                connection = Nats.connect(options)
                logger.info("Connected to NATS successfully")
            } catch (e: Exception) {
                if (attempts >= maxAttempts) {
                    logger.error("Failed to connect to NATS after $maxAttempts attempts", e)
                    throw e
                }
                logger.warn("Failed to connect to NATS (attempt $attempts/$maxAttempts): ${e.message}. Retrying in 2 seconds...")
                Thread.sleep(2000)
            }
        }

        natsConnection = connection!!

        createStreamIfNotExists(connection)

        return connection
    }

    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
    }

    private fun createStreamIfNotExists(connection: Connection) {
        try {
            val jsm = connection.jetStreamManagement()

            val existingStreams = jsm.streamNames.toList()

            if (!existingStreams.contains(natsProperties.stream.name)) {
                logger.info("Creating JetStream stream: ${natsProperties.stream.name}")

                val streamConfig = StreamConfiguration.builder()
                    .name(natsProperties.stream.name)
                    .subjects(natsProperties.stream.subjects)
                    .storageType(StorageType.File)
                    .build()

                jsm.addStream(streamConfig)
                logger.info("JetStream stream created: ${natsProperties.stream.name}")
            } else {
                logger.info("JetStream stream already exists: ${natsProperties.stream.name}")
            }
        } catch (e: Exception) {
            logger.error("Failed to create JetStream stream", e)
            throw e
        }
    }

    @PreDestroy
    fun cleanup() {
        natsConnection?.close()
        logger.info("NATS connection closed")
    }
}
