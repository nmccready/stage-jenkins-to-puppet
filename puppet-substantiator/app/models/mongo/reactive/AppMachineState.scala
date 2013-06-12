package models.mongo.reactive

import play.api.libs.json._
import reactivemongo.bson._
import reactivemongo.bson.DefaultBSONHandlers._
import models.Model._
import models.json.{IWritesExtended, IReadsExtended}

/*
Hybrid Mongo Schema of Embedded to Normalized
- normalizing machines for dealing with updating independent
      - machine state - ie machine isAlive.
- non normalized this class being the actual current state of an application on a machine!
 */
case class AppMachineState(machineName: String, actual: Option[String] = None)

object AppMachineState {

  implicit object AppMachineBSONReader extends IBSONReaderExtended[AppMachineState] {
    override def read(document: BSONDocument) = {
      val doc = document

      AppMachineState(
        doc.getAs[BSONString]("machineName").map(_.value).getOrElse(throw errorFrom("BSONRead", "machineName")),
        doc.getAs[BSONString]("actual").map(_.value)
      )
    }
  }

  implicit object AppMachineStateBSONWriter extends IBSONWriterExtended[AppMachineState] {
    override def write(entity: AppMachineState) = {
      val doc = BSONDocument(
        "machineName" -> entity.machineName)
      entity.actual match {
        case Some(act) => doc ++ ("actual" -> act)
        case None => doc
      }
    }
  }

  implicit object AppMachineJSONReader extends IReadsExtended[AppMachineState] {
    def reads(json: JsValue) =
      JsSuccess(new AppMachineState(
        (json \ "machineName").as[String],
        (json \ "actual").asOpt[String]
      ))
  }

  implicit object AppMachineJSONWriter extends IWritesExtended[AppMachineState] {
    def writes(entity: AppMachineState): JsValue =
      JsObject({
        val seq = Seq("machineName" -> JsString(entity.machineName))
        entity.actual match {
          case Some(act) =>
            seq ++ Seq("actual" -> JsString(act))
          case None =>
            seq
        }
      })
  }

}
