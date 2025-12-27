import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.ConnectionFactory
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.mapreduce.TableInputFormat
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.api.java.JavaPairRDD
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Row
import org.apache.spark.sql.RowFactory
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.*
import org.apache.spark.sql.types.DataTypes
import org.apache.spark.sql.types.Metadata
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.apache.spark.api.java.function.ForeachPartitionFunction

object NetworkTrafficAnalyzer {
    @JvmStatic
    fun main(args: Array<String>) {
        val spark = SparkSession.builder().appName("Network Traffic Analyzer").getOrCreate()

        try {
            val data = readFromHBase(spark)
            val count = data.count()

            if (count == 0L) {
                println("No data found in HBase table 'system_metrics'. Waiting for data...")
                return
            }

            println("Processing $count records from HBase...")

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

            val aggCount = aggregated.count()
            writeToHBase(aggregated)
            println("Successfully wrote $aggCount aggregated records to 'network_traffic_analysis'")
        } finally {
            spark.stop()
        }
    }

    private fun readFromHBase(spark: SparkSession): Dataset<Row> {
        val conf = HBaseConfiguration.create()
        conf.set("hbase.zookeeper.quorum", "mephi-hbase:2181")
        conf.set(TableInputFormat.INPUT_TABLE, "system_metrics")
        conf.set(TableInputFormat.SCAN_COLUMNS, "info:networkMetrics info:timestamp")

        val hbaseRDD: JavaPairRDD<ImmutableBytesWritable, Result> = spark.sparkContext()
            .newAPIHadoopRDD(
                conf,
                TableInputFormat::class.java,
                ImmutableBytesWritable::class.java,
                Result::class.java
            )
            .toJavaRDD()
            .mapToPair { it }

        val rows = hbaseRDD.mapPartitions { partition ->
            val rowsList = mutableListOf<Row>()
            partition.forEach { pair ->
                val result = pair._2()
                val networkMetricsCell = result.getColumnLatestCell(Bytes.toBytes("info"), Bytes.toBytes("networkMetrics"))
                val timestampCell = result.getColumnLatestCell(Bytes.toBytes("info"), Bytes.toBytes("timestamp"))

                if (networkMetricsCell != null && timestampCell != null) {
                    val networkMetricsJson = Bytes.toString(networkMetricsCell.valueArray, networkMetricsCell.valueOffset, networkMetricsCell.valueLength)
                    val bytesReceived = extractJsonLong(networkMetricsJson, "bytesReceived")
                    val bytesSent = extractJsonLong(networkMetricsJson, "bytesSent")

                    if (bytesReceived != null && bytesSent != null) {
                        val timestampStr = Bytes.toString(timestampCell.valueArray, timestampCell.valueOffset, timestampCell.valueLength)
                        val timestamp = parseTimestamp(timestampStr)
                        if (timestamp != null) {
                            rowsList.add(RowFactory.create(timestamp, bytesReceived, bytesSent))
                        }
                    }
                }
            }
            rowsList.iterator()
        }

        val schema = StructType(arrayOf(
            StructField("timestamp", DataTypes.TimestampType, false, Metadata.empty()),
            StructField("received", DataTypes.LongType, false, Metadata.empty()),
            StructField("sent", DataTypes.LongType, false, Metadata.empty())
        ))

        return spark.createDataFrame(rows.rdd(), schema)
    }

    private fun parseTimestamp(str: String): java.sql.Timestamp? {
        return try {
            val parts = str.trim('[', ']').split(",").map { it.trim().toInt() }
            if (parts.size < 6) return null
            val localDateTime = LocalDateTime.of(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
            java.sql.Timestamp.valueOf(localDateTime)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractJsonLong(json: String, key: String): Long? {
        return try {
            val pattern = """"$key"\s*:\s*(\d+)""".toRegex()
            pattern.find(json)?.groupValues?.get(1)?.toLong()
        } catch (e: Exception) {
            null
        }
    }

    private fun writeToHBase(data: Dataset<Row>) {
        data.foreachPartition(ForeachPartitionFunction { partition: Iterator<Row> ->
            val conf = HBaseConfiguration.create()
            conf.set("hbase.zookeeper.quorum", "mephi-hbase:2181")

            val connection = ConnectionFactory.createConnection(conf)
            val table = connection.getTable(TableName.valueOf("network_traffic_analysis"))

            while (partition.hasNext()) {
                val row = partition.next()
                val rowKeyStr = row.getTimestamp(0).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val put = Put(Bytes.toBytes(rowKeyStr))

                val avgRatioStr = String.format("%.6f", row.getDouble(1))
                put.addColumn(Bytes.toBytes("metrics"), Bytes.toBytes("avg_ratio"), Bytes.toBytes(avgRatioStr))
                put.addColumn(Bytes.toBytes("metrics"), Bytes.toBytes("total_received"), Bytes.toBytes(row.getLong(2).toString()))
                put.addColumn(Bytes.toBytes("metrics"), Bytes.toBytes("total_sent"), Bytes.toBytes(row.getLong(3).toString()))
                put.addColumn(Bytes.toBytes("metrics"), Bytes.toBytes("record_count"), Bytes.toBytes(row.getLong(4).toString()))

                table.put(put)
            }

            table.close()
            connection.close()
        })
    }
}
