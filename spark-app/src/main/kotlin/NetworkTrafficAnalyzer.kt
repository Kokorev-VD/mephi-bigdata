import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.ConnectionFactory
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.mapreduce.TableInputFormat
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.api.java.JavaPairRDD
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.RowFactory
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.avg
import org.apache.spark.sql.functions.coalesce
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.functions.count
import org.apache.spark.sql.functions.lit
import org.apache.spark.sql.functions.sum
import org.apache.spark.sql.functions.window
import org.apache.spark.sql.types.DataTypes
import org.apache.spark.sql.types.Metadata
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val ZOOKEEPER_QUORUM = "mephi-hbase:2181"
const val SOURCE_TABLE = "system_metrics"
const val TARGET_TABLE = "network_traffic_analysis"
const val COLUMN_FAMILY_INFO = "info"
const val COLUMN_FAMILY_METRICS = "metrics"

object NetworkTrafficAnalyzer {
    private val logger = LoggerFactory.getLogger(NetworkTrafficAnalyzer::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        run()
    }

    private fun run() {
        val spark = SparkSession.builder()
            .appName("Network Traffic Analyzer")
            .getOrCreate()

        try {
            logger.info("Network Traffic Analyzer started")

            val data = readFromHBase(spark)
            val recordCount = data.count()
            logger.info("Read {} records from HBase table: {}", recordCount, SOURCE_TABLE)

            if (recordCount == 0L) {
                logger.warn("No data found in HBase. Exiting.")
                return
            }

            logger.info("Started processing data with 10-minute window aggregation")
            val aggregated = data
                .withColumn("window", window(col("timestamp"), "10 minutes"))
                .groupBy("window")
                .agg(
                    avg(col("received").divide(coalesce(col("sent"), lit(1L)))).alias("avg_ratio"),
                    sum("received").alias("total_received"),
                    sum("sent").alias("total_sent"),
                    count("*").alias("record_count")
                )
                .select(
                    col("window.start").alias("window_start"),
                    col("avg_ratio"),
                    col("total_received"),
                    col("total_sent"),
                    col("record_count")
                )

            val aggregatedCount = aggregated.count()
            logger.info("Aggregation complete. Generated {} aggregated records", aggregatedCount)

            writeToHBase(aggregated)
            logger.info("Results written to HBase table: {}", TARGET_TABLE)

            logger.info("Network Traffic Analyzer completed successfully")
        } finally {
            spark.stop()
        }
    }

    private fun readFromHBase(spark: SparkSession): Dataset<Row> {
        logger.info("Setting up HBase configuration for parallel reading")

        val conf = HBaseConfiguration.create()
        conf.set("hbase.zookeeper.quorum", ZOOKEEPER_QUORUM)
        conf.set(TableInputFormat.INPUT_TABLE, SOURCE_TABLE)
        conf.set(TableInputFormat.SCAN_COLUMNS, "$COLUMN_FAMILY_INFO:networkMetrics $COLUMN_FAMILY_INFO:timestamp")

        // Use Spark's parallelism for reading from HBase
        val hbaseRDD: JavaPairRDD<ImmutableBytesWritable, Result> = spark.sparkContext()
            .newAPIHadoopRDD(
                conf,
                TableInputFormat::class.java,
                ImmutableBytesWritable::class.java,
                Result::class.java
            )
            .toJavaRDD()
            .mapToPair { tuple -> tuple }

        val rows = hbaseRDD.mapPartitions { partition ->
            val rowsList = mutableListOf<Row>()
            var recordsInPartition = 0

            partition.forEach { pair ->
                val result = pair._2()

                val networkMetricsCell = result.getColumnLatestCell(
                    Bytes.toBytes(COLUMN_FAMILY_INFO),
                    Bytes.toBytes("networkMetrics")
                )
                val timestampCell = result.getColumnLatestCell(
                    Bytes.toBytes(COLUMN_FAMILY_INFO),
                    Bytes.toBytes("timestamp")
                )

                if (networkMetricsCell != null && timestampCell != null) {
                    val networkMetricsJson = Bytes.toString(
                        networkMetricsCell.valueArray,
                        networkMetricsCell.valueOffset,
                        networkMetricsCell.valueLength
                    )

                    val bytesReceived = extractJsonLong(networkMetricsJson, "bytesReceived")
                    val bytesSent = extractJsonLong(networkMetricsJson, "bytesSent")

                    if (bytesReceived != null && bytesSent != null) {
                        val timestampStr = Bytes.toString(
                            timestampCell.valueArray,
                            timestampCell.valueOffset,
                            timestampCell.valueLength
                        )
                        val timestamp = parseTimestampArray(timestampStr)

                        if (timestamp != null) {
                            rowsList.add(RowFactory.create(timestamp, bytesReceived, bytesSent))
                            recordsInPartition++
                        }
                    }
                }
            }

            if (recordsInPartition > 0) {
                logger.info("Partition processed {} records", recordsInPartition)
            }

            rowsList.iterator()
        }

        val schema = StructType(
            arrayOf(
                StructField("timestamp", DataTypes.TimestampType, false, Metadata.empty()),
                StructField("received", DataTypes.LongType, false, Metadata.empty()),
                StructField("sent", DataTypes.LongType, false, Metadata.empty())
            )
        )

        return spark.createDataFrame(rows.rdd(), schema)
    }

    private fun parseTimestampArray(timestampStr: String): java.sql.Timestamp? {
        return try {
            val parts = timestampStr.trim('[', ']').split(",").map { it.trim().toInt() }
            if (parts.size < 6) return null

            val year = parts[0]
            val month = parts[1]
            val day = parts[2]
            val hour = parts[3]
            val minute = parts[4]
            val second = parts[5]

            val localDateTime = LocalDateTime.of(year, month, day, hour, minute, second)
            java.sql.Timestamp.valueOf(localDateTime)
        } catch (e: Exception) {
            logger.warn("Failed to parse timestamp array: {}", timestampStr)
            null
        }
    }

    private fun extractJsonLong(json: String, key: String): Long? {
        return try {
            val pattern = """"$key"\s*:\s*(\d+)""".toRegex()
            val matchResult = pattern.find(json)
            matchResult?.groupValues?.get(1)?.toLong()
        } catch (e: Exception) {
            logger.warn("Failed to extract {} from JSON", key)
            null
        }
    }

    private fun writeToHBase(data: Dataset<Row>) {
        logger.info("Starting write to HBase")

        data.foreachPartition { partition: Iterator<Row> ->
            val conf = HBaseConfiguration.create()
            conf.set("hbase.zookeeper.quorum", ZOOKEEPER_QUORUM)

            var connection: org.apache.hadoop.hbase.client.Connection? = null
            var table: org.apache.hadoop.hbase.client.Table? = null

            try {
                connection = ConnectionFactory.createConnection(conf)
                table = connection.getTable(TableName.valueOf(TARGET_TABLE))

                var recordsWritten = 0

                partition.forEach { row ->
                    val windowStart = row.getTimestamp(0)
                    val rowKey = windowStart.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                    val put = Put(Bytes.toBytes(rowKey))

                    // Use String format for all values to make them readable in HBase shell
                    val avgRatio = row.getDouble(1)
                    put.addColumn(
                        Bytes.toBytes(COLUMN_FAMILY_METRICS),
                        Bytes.toBytes("avg_ratio"),
                        Bytes.toBytes(String.format("%.6f", avgRatio))
                    )

                    val totalReceived = row.getLong(2)
                    put.addColumn(
                        Bytes.toBytes(COLUMN_FAMILY_METRICS),
                        Bytes.toBytes("total_received"),
                        Bytes.toBytes(totalReceived.toString())
                    )

                    val totalSent = row.getLong(3)
                    put.addColumn(
                        Bytes.toBytes(COLUMN_FAMILY_METRICS),
                        Bytes.toBytes("total_sent"),
                        Bytes.toBytes(totalSent.toString())
                    )

                    val recordCount = row.getLong(4)
                    put.addColumn(
                        Bytes.toBytes(COLUMN_FAMILY_METRICS),
                        Bytes.toBytes("record_count"),
                        Bytes.toBytes(recordCount.toString())
                    )

                    table.put(put)
                    recordsWritten++
                }

                if (recordsWritten > 0) {
                    logger.info("Partition wrote {} records to HBase", recordsWritten)
                }
            } finally {
                try {
                    table?.close()
                } catch (e: Exception) {
                    logger.error("Error closing HBase table", e)
                }
                try {
                    connection?.close()
                } catch (e: Exception) {
                    logger.error("Error closing HBase connection", e)
                }
            }
        }
    }
}
