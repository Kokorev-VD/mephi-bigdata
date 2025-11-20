package dev.kokorev.mephibigdata.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "nats")
data class NatsProperties(
    var url: String = "nats://localhost:4222",
    var stream: StreamConfig = StreamConfig(),
    var subject: String = "metrics.system.snapshot"
) {
    data class StreamConfig(
        var name: String = "METRICS",
        var subjects: List<String> = listOf("metrics.system.*")
    )
}
