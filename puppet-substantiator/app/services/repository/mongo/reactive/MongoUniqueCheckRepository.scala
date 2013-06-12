package services.repository.mongo.reactive

import reactivemongo.core.commands.Count
import concurrent.{Future, ExecutionContext}
import models.mongo.reactive.IMongoModel

import reactivemongo.bson.{BSONObjectID, BSONDocument}
import services.repository.{IUniqueCheck, IUniqueCheckRepository}

trait MongoUniqueCheckRepository[TModel <: IMongoModel[TModel]]
  extends IUniqueCheckRepository[BSONObjectID,IMongoModel[TModel],BSONDocument] {
  this: MongoBaseRepository[TModel] =>

  def uniqueCheck(criteria: IUniqueCheck[BSONObjectID, BSONDocument])(implicit context: ExecutionContext):Future[Boolean] = {
    var doc = criteria.otherCriteria
    criteria.id foreach {
      id => doc = doc ++ ("_id" -> BSONDocument("$ne" -> id))
    }

    db.command(Count(collectionName, Some(doc))).map(_ == 0)
  }
}
