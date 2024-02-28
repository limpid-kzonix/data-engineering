package demo.flink

import demo.flink.CollectingDailyKeyedProcessFunction.Out
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.api.common.state.{
  ListState,
  ListStateDescriptor,
  ValueState,
  ValueStateDescriptor
}
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.configuration.{Configuration, MemorySize}
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.{
  CheckpointConfig,
  StreamExecutionEnvironment
}
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction
import org.apache.flink.streaming.api.functions.sink.filesystem.{
  BucketAssigner,
  RollingPolicy,
  StreamingFileSink
}
import org.apache.flink.api.common.serialization.{Encoder, SimpleStringEncoder}
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend
import org.apache.flink.core.fs.Path
import org.apache.flink.core.io.SimpleVersionedSerializer
import org.apache.flink.streaming.api.{CheckpointingMode, TimeCharacteristic}
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.SimpleVersionedStringSerializer
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.{
  DefaultRollingPolicy,
  OnCheckpointRollingPolicy
}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector
import org.apache.kafka.clients.consumer.OffsetResetStrategy

import java.io.{OutputStream, PrintStream}
import java.time.format.DateTimeFormatter
import java.time.{Duration, Instant, ZoneId}
object Main {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment()

    val params = ParameterTool.fromArgs(args)
    // Use event time from the source record
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.enableCheckpointing(15000, CheckpointingMode.EXACTLY_ONCE)
    env.setStateBackend(new EmbeddedRocksDBStateBackend())
    // Inject parameters to be globally available
    env.getConfig.setGlobalJobParameters(params)

    val kafkaSource = KafkaSource
      .builder[DataWrapper]
      .setBootstrapServers("kafka:29092")
      .setTopics("test_topic")
      .setGroupId("test-group")
      .setStartingOffsets(
        OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST)
      )
      .setValueOnlyDeserializer(new DataExampleDeserializer())
      // .setBounded(OffsetsInitializer.latest())
      .build()

    val kafkaStream =
      env
        .fromSource(
          kafkaSource,
          WatermarkStrategy.forMonotonousTimestamps(),
          "Kafka Source"
        )
        .setParallelism(2)
        .name("Kafka Source")
        .rebalance()

    val sink = StreamingFileSink
      .forRowFormat(
        new Path("s3://testbucket/data/"),
        new Encoder[Out]() {
          override def encode(element: Out, stream: OutputStream): Unit = {
            val out = new PrintStream(stream)
            out.println(element.value)
          }
        }
      )
      .withRollingPolicy(
        DefaultRollingPolicy
          .builder()
          .withMaxPartSize(MemorySize.parse("128MB"))
          .withRolloverInterval(Duration.ofDays(1))
          .withInactivityInterval(Duration.ofDays(1))
          .build()
      )
      .withBucketAssigner(new BucketAssigner[Out, String] {

        val serialVersionUID = 1L
        val DefaultDateFormatString = "yyyy/MM/dd/HH"
        @transient var dateFmt: DateTimeFormatter =
          java.time.format.DateTimeFormatter.ofPattern(DefaultDateFormatString)

        override def getBucketId(
            element: Out,
            context: BucketAssigner.Context
        ): String = {
          if (dateFmt == null) {
            dateFmt = java.time.format.DateTimeFormatter
              .ofPattern(DefaultDateFormatString)
              .withZone(ZoneId.systemDefault())
          }
          s"/customer360/id_value=${element.key}/${dateFmt
            .format(Instant.ofEpochMilli(context.currentProcessingTime))}"
        }

        override def getSerializer: SimpleVersionedSerializer[String] =
          SimpleVersionedStringSerializer.INSTANCE
      })
      .build()

    kafkaStream
      .filter((t: DataWrapper) => t.idType == 2L)
      .setParallelism(2)
      .name("Filter")
      .rebalance()
      .keyBy((t: DataWrapper) => t.idValue)
      .process(new CollectingDailyKeyedProcessFunction())
      .setParallelism(5)
      .name("20 minutes flush to Sink")
      .addSink(sink)
      .setParallelism(10)
      .name("S3 Sink")

    env.execute("Kafka -> S3 Example (Customer 360)")

  }
}
