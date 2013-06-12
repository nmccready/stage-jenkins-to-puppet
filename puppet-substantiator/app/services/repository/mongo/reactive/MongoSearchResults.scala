package services.repository.mongo.reactive

import play.api.libs.iteratee.Enumerator
import models.IModel
import reactivemongo.bson.BSONObjectID
import services.repository.ISearchResults

case class MongoSearchResults[TModel <: IModel[BSONObjectID]]
(override val count: Int,
 override val results: Enumerator[TModel])
  extends ISearchResults[TModel]
