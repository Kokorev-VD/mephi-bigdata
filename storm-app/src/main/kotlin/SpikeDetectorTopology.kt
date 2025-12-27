import io.nats.client.Connection
import io.nats.client.JetStream
import io.nats.client.Nats
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.storm.task.OutputCollector
import org.apache.storm.task.TopologyContext
import org.apache.storm.topology.OutputFieldsDeclarer
import org.apache.storm.Config
import org.apache.storm.LocalCluster
import org.apache.storm.StormSubmitter
import org.apache.storm.topology.TopologyBuilder
import org.apache.storm.topology.base.BaseRichBolt
import org.apache.storm.tuple.Fields
import org.apache.storm.tuple.Tuple
import org.apache.storm.tuple.Values
import java.time.Duration
import java.time.Instant
import java.util.LinkedList

class NatsOutputBolt : BaseRichBolt() {
    @Transient private var collector: OutputCollector? = null
    @Transient private var natsConnection: Connection? = null
    @Transient private var jetStream: JetStream? = null
    @Transient private var objectMapper: ObjectMapper? = null

    override fun prepare(conf: MutableMap<String, Any>?, context: TopologyContext?, collector: OutputCollector?) {
        this.collector = collector
        objectMapper = ObjectMapper().registerKotlinModule()
        natsConnection = Nats.connect("nats://mephi-nats:4222")
        jetStream = natsConnection!!.jetStream()
    }

    override fun execute(tuple: Tuple) {
        val spikeEvent = tuple.getValueByField("spikeEvent") as Map<*, *>
        val json = objectMapper!!.writeValueAsString(spikeEvent)
        jetStream!!.publish("metrics.spikes", json.toByteArray())
        collector!!.ack(tuple)
    }

    override fun declareOutputFields(declarer: OutputFieldsDeclarer) {}

    override fun cleanup() {
        natsConnection?.close()
    }
}

class SpikeDetectorBolt : BaseRichBolt() {
    @Transient private var collector: OutputCollector? = null
    private val cpuHistory = LinkedList<MetricPoint>()
    private val memoryHistory = LinkedList<MetricPoint>()
    private var cpuSum = 0.0
    private var memorySum = 0.0

    data class MetricPoint(val timestamp: Instant, val value: Double) : java.io.Serializable

    override fun prepare(conf: MutableMap<String, Any>?, context: TopologyContext?, collector: OutputCollector?) {
        this.collector = collector
    }

    override fun execute(tuple: Tuple) {
        val timestamp = Instant.parse(tuple.getStringByField("timestamp"))
        val cpuLoad = tuple.getDoubleByField("cpuLoad")
        val memoryUsed = tuple.getLongByField("memoryUsed")

        cpuHistory.add(MetricPoint(timestamp, cpuLoad))
        cpuSum += cpuLoad
        memoryHistory.add(MetricPoint(timestamp, memoryUsed.toDouble()))
        memorySum += memoryUsed

        val cutoff = timestamp.minus(Duration.ofMinutes(5))
        while (cpuHistory.isNotEmpty() && cpuHistory.first().timestamp.isBefore(cutoff)) {
            cpuSum -= cpuHistory.removeFirst().value
        }
        while (memoryHistory.isNotEmpty() && memoryHistory.first().timestamp.isBefore(cutoff)) {
            memorySum -= memoryHistory.removeFirst().value
        }

        val avgCpu = if (cpuHistory.isEmpty()) 0.0 else cpuSum / cpuHistory.size
        val avgMemory = if (memoryHistory.isEmpty()) 0.0 else memorySum / memoryHistory.size

        val cpuSpike = cpuHistory.size >= 2 && cpuLoad > avgCpu * 1.05
        val memorySpike = memoryHistory.size >= 2 && memoryUsed > avgMemory * 1.05

        if (cpuSpike || memorySpike) {
            collector!!.emit(tuple, Values(mapOf(
                "timestamp" to timestamp.toString(),
                "cpuLoad" to cpuLoad,
                "avgCpu" to avgCpu,
                "cpuSpike" to cpuSpike,
                "memoryUsed" to memoryUsed,
                "avgMemory" to avgMemory,
                "memorySpike" to memorySpike
            )))
        }

        collector!!.ack(tuple)
    }

    override fun declareOutputFields(declarer: OutputFieldsDeclarer) {
        declarer.declare(Fields("spikeEvent"))
    }
}

object SpikeDetectorTopology {
    @JvmStatic
    fun main(args: Array<String>) {
        val builder = TopologyBuilder()
        builder.setSpout("nats-spout", NatsSpout())
        builder.setBolt("spike-detector", SpikeDetectorBolt()).shuffleGrouping("nats-spout")
        builder.setBolt("nats-output", NatsOutputBolt()).shuffleGrouping("spike-detector")

        val config = Config()
        config.setNumWorkers(1)
        config.put(Config.NIMBUS_THRIFT_PORT, 6627)
        config.put(Config.STORM_NIMBUS_RETRY_TIMES, 10)
        config.put(Config.STORM_NIMBUS_RETRY_INTERVAL, 1000)

        if (args.isNotEmpty() && args[0] == "local") {
            LocalCluster().submitTopology("spike-detector", config, builder.createTopology())
        } else {
            StormSubmitter.submitTopology("spike-detector", config, builder.createTopology())
        }
    }
}
