package models.mongo.reactive

import reactivemongo.bson.{BSONDocument, BSONArray}
import reactivemongo.bson.{BSONDocumentReader, BSONDocumentWriter}

trait IBSONWriterExtended[Model] extends BSONDocumentWriter[Model] {
  def writes(many: List[Model]): BSONArray = {
    BSONArray.apply(many.map(write))
  }
}

trait IBSONReaderExtended[Model] extends BSONDocumentReader[Model] {
  def reads(bsonArray: BSONArray): List[Model] =
    bsonArray.values.toList.map(bson => read(bson.asInstanceOf[BSONDocument]))
}