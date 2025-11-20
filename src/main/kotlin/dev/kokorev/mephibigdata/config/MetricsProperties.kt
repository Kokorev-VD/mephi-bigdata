package dev.kokorev.mephibigdata.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "metrics")
data class MetricsProperties(
    var enabled: Boolean = true,
    var collectionIntervalMs: Long = 5000,
    var enabledMetrics: EnabledMetrics = EnabledMetrics()
)

data class EnabledMetrics(
    var cpu: Boolean = true,
    var ram: Boolean = true,
    var disk: Boolean = true,
    var network: Boolean = true
)
