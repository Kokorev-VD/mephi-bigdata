import io.nats.client.Connection
import io.nats.client.JetStreamSubscription
import io.nats.client.Nats
import io.nats.client.PullSubscribeOptions
import io.nats.client.api.ConsumerConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.apache.storm.spout.SpoutOutputCollector
import org.apache.storm.task.TopologyContext
import org.apache.storm.topology.OutputFieldsDeclarer
import org.apache.storm.topology.base.BaseRichSpout
import org.apache.storm.tuple.Fields
import org.apache.storm.tuple.Values
import java.time.LocalDateTime

class NatsSpout : BaseRichSpout() {
    @Transient private var collector: SpoutOutputCollector? = null
    @Transient private var natsConnection: Connection? = null
    @Transient private var subscription: JetStreamSubscription? = null
    @Transient private var objectMapper: ObjectMapper? = null

    override fun open(conf: MutableMap<String, Any>?, context: TopologyContext?, collector: SpoutOutputCollector?) {
        this.collector = collector

        objectMapper = ObjectMapper().apply {
            registerKotlinModule()
            registerModule(JavaTimeModule())
        }

        natsConnection = Nats.connect("nats://mephi-nats:4222")

        subscription = natsConnection!!.jetStream().subscribe(
            "metrics.system.snapshot",
            PullSubscribeOptions.builder()
                .stream("METRICS")
                .configuration(ConsumerConfiguration.builder().durable("storm-spike-detector").build())
                .build()
        )
    }

    override fun nextTuple() {
        subscription?.fetch(1, java.time.Duration.ofMillis(100))?.forEach { msg ->
            try {
                val metrics = objectMapper!!.readValue<MetricsSnapshot>(String(msg.data))
                collector!!.emit(Values(
                    metrics.timestamp.toString(),
                    metrics.cpuMetrics?.loadPercentage ?: 0.0,
                    metrics.ramMetrics?.usedBytes ?: 0L
                ))
                msg.ack()
            } catch (e: Exception) {
                msg.nak()
            }
        }
    }

    override fun declareOutputFields(declarer: OutputFieldsDeclarer?) {
        declarer?.declare(Fields("timestamp", "cpuLoad", "memoryUsed"))
    }

    override fun close() {
        subscription?.unsubscribe()
        natsConnection?.close()
    }
}

data class MetricsSnapshot(
    val timestamp: LocalDateTime,
    val cpuMetrics: CpuMetrics?,
    val ramMetrics: RamMetrics?
)

data class CpuMetrics(val loadPercentage: Double)
data class RamMetrics(val usedBytes: Long)
