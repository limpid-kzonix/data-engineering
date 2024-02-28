package demo.flink
import io.circe.Json._
import io.circe.Json
import io.circe.generic.extras.Configuration
import io.circe.jawn.parseByteArray
case class DataWrapper(
    idValue: String,
    idType: Long,
    epochMillis: Long,
    data: Json,
    raw: String
)

case class DataInterimModel(
    data: DataField
)

case class DataField(
    id_value: String,
    id_type: Long,
    epoch_millis: Long,
    event_type: String
)

object auto extends io.circe.generic.AutoDerivation {
  implicit val conf: Configuration =
    Configuration.default.withSnakeCaseMemberNames.withDefaults
}

import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
class DataExampleDeserializer extends DeserializationSchema[DataWrapper] {

  private val logger = org.slf4j.LoggerFactory.getLogger(this.getClass)
  override def isEndOfStream(data: DataWrapper): Boolean = false

  override def getProducedType: TypeInformation[DataWrapper] =
    TypeInformation.of(classOf[DataWrapper])

  override def deserialize(bytes: Array[Byte]): DataWrapper = {
    import auto._

    val version = Json.fromInt(1)
    parseByteArray(bytes) match {
      case Right(json) =>
        json.as[DataInterimModel] match {
          case Right(value) =>
            DataWrapper(
              value.data.id_value,
              value.data.id_type,
              value.data.epoch_millis,
              json,
              new String(bytes, "UTF-8")
            )
          case Left(error) => {
            logger.error(s"Error deserializing: ${error.getMessage}")
            throw new RuntimeException("Fuck you", error)
          }
        }
      case Left(error) => {
        logger.error(s"Error parsing: ${error.getMessage}")
        throw new RuntimeException("Fuck you", error)
      }
    }
  }
}
