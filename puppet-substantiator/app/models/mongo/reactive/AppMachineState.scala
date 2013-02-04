package models.mongo.reactive

import reactivemongo.bson.handlers._
import play.api.libs.json._
import reactivemongo.bson._
import models.Model._
import models.json.{IWritesExtended, IReadsExtended}

/*
Hybrid Mongo Schema of Embedded to Normalized , normalizing machines for deal with updating independent
machine state - ie machine isAlive.

If machine is dead or has undesired state and application will remove that machine (AppMachineState) from its cluster
 */
case class AppMachineState(val machineName: String, val actual: String = "EMPTY")

object AppMachineState {

  implicit object AppMachineBSONReader extends IBSONReaderExtended[AppMachineState] {
    def fromBSON(document: BSONDocument) = {
      val doc = document.toTraversable

      AppMachineState(
        doc.getAs[BSONString]("machineName").map(_.value).getOrElse(throw errorFrom("BSONRead", "machineName")),
        doc.getAs[BSONString]("actual").map(_.value).getOrElse(throw errorFrom("BSONRead", "name"))
      )
    }
  }

  implicit object AppMachineStateBSONWriter extends IBSONWriterExtended[AppMachineState] {
    def toBSON(entity: AppMachineState) =
      BSONDocument(
        "machineName" -> BSONString(entity.machineName),
        "actual" -> BSONString(entity.actual)
      )
  }

  implicit object AppMachineJSONReader extends IReadsExtended[AppMachineState] {
    def reads(json: JsValue) =
      JsSuccess(new AppMachineState(
        (json \ "machineName").as[String],
        (json \ "actual").as[String]
      ))
    override def readsArray(array: JsArray): List[AppMachineState] = array.value.flatMap(reads(_).asOpt).toList
  }

  implicit object AppMachineJSONWriter extends IWritesExtended[AppMachineState] {
    def writes(entity: AppMachineState): JsValue =
      JsObject(Seq(
        "machineName" -> JsString(entity.machineName),
        "actual" -> JsString(entity.actual)
      ))
    override def writesArray(objs: List[AppMachineState]): JsArray = JsArray(objs.map(writes))
  }

}
