package demo.flink

import demo.flink.CollectingDailyKeyedProcessFunction.Out
import org.apache.flink.api.common.state.{
  ListState,
  ListStateDescriptor,
  MapState,
  MapStateDescriptor,
  ValueState,
  ValueStateDescriptor
}
import org.apache.flink.api.common.time.Time
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory

import java.time.Instant
import scala.collection.JavaConverters.asScalaIteratorConverter

class CollectingDailyKeyedProcessFunction
    extends KeyedProcessFunction[String, DataWrapper, Out] {

  val logger = LoggerFactory.getLogger(this.getClass)

  private var tempBuffer: ListState[String] = _
  private var timerState: ValueState[Long] = _
  private var tempBufferSize: ValueState[Long] = _
  private var mapState: MapState[String, List[String]] = _
  private var currentIdx: ValueState[Long] = _

  override def open(parameters: Configuration): Unit = {
    val tempBufferDesc =
      new ListStateDescriptor[String]("tempBuffer", classOf[String])
    val valueStateDesc =
      new ValueStateDescriptor[Long]("timer", classOf[Long])
    val tempBufferSizeDesc =
      new ValueStateDescriptor[Long]("tempBufferSize", classOf[Long])

    this.tempBuffer = getRuntimeContext.getListState(tempBufferDesc)
    this.timerState = getRuntimeContext.getState(valueStateDesc)
    this.tempBufferSize = getRuntimeContext.getState(tempBufferSizeDesc)
  }

  override def processElement(
      value: DataWrapper,
      ctx: KeyedProcessFunction[String, DataWrapper, Out]#Context,
      out: Collector[Out]
  ): Unit = {
    tempBuffer.add(value.raw)
    val tempBufferSizeValue = Option(tempBufferSize.value()).getOrElse(0L)
    tempBufferSize.update(tempBufferSizeValue + value.raw.length)

    //gif(tempBufferSize.value() > )

    val prevTimer = Option(timerState.value())
    // Set timer for midnight
    val currentProcessingTime = ctx.timerService().currentProcessingTime()
    val next: Long = calculateNextTimestamp(currentProcessingTime)
    prevTimer match {
      case Some(prev) =>
        if (next != prev) {
          ctx.timerService().deleteProcessingTimeTimer(prev)
          logger.info("Setting timer for: " + Instant.ofEpochMilli(next))
          ctx.timerService().registerProcessingTimeTimer(next)
        } else {
          ctx.timerService().registerProcessingTimeTimer(next)
        }
      case None => ctx.timerService().registerProcessingTimeTimer(next)
    }
    timerState.update(next)
  }

  override def onTimer(
      timestamp: Long,
      ctx: KeyedProcessFunction[
        String,
        DataWrapper,
        Out
      ]#OnTimerContext,
      out: Collector[Out]
  ): Unit = {
    logger.info("Custom Timer: Timer triggered")
    val allValues = tempBuffer.get()
    val allValuesList = allValues.iterator()
    val key = ctx.getCurrentKey
    while (allValuesList.hasNext) {
      val v = allValuesList.next()
      logger.info(v)
      out.collect(Out(key, v))
    }
    tempBuffer.clear()
    timerState.clear()
  }

  private def calculateNextTimestamp(
      currentProcessingTime: Long
  ): Long = {
    val currentHours: Long = (currentProcessingTime / (60 * 60 * 1000)) + 1;
    // Convert hours back to milliseconds
    val res = currentHours * (60 * 60 * 1000)
    res
  }

}

object CollectingDailyKeyedProcessFunction {
  def apply(): CollectingDailyKeyedProcessFunction =
    new CollectingDailyKeyedProcessFunction()

  case class Out(key: String, value: String)

}
