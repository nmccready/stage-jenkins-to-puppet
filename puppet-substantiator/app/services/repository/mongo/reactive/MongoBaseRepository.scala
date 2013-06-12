package services.repository.mongo.reactive

import play.api.libs.iteratee.{Enumerator, Enumeratee}
import reactivemongo.bson._
import DefaultBSONHandlers._
import reactivemongo.core.commands.Count
import util.IConfigurationProvider
import concurrent.{Future, ExecutionContext}
import models.mongo.reactive.IMongoModel
import services.repository._
import scala.Some
import reactivemongo.api.QueryOpts
import reactivemongo.bson.BSONInteger
import reactivemongo.api.collections.default.BSONCollection

abstract class MongoBaseRepository[TModel <: IMongoModel[TModel]]
  extends IMongoRepository[TModel]
  with IMongoDbProvider
  with IConfigurationProvider {

  implicit val bsonReader: BSONDocumentReader[TModel]
  implicit val bsonWriter: BSONDocumentWriter[TModel]

  protected def collectionName: String

  protected val collection = db.collection[BSONCollection](collectionName)

  protected def projection: Option[BSONDocument] = None

  protected def onCreated(model: TModel)
                         (implicit context: ExecutionContext): Future[Either[Option[TModel], Exception]] = {
    model.id match {
      case Some(id) => get(id)
      case None => Future(Left(None))
    }
  }

  protected def onUpdated(originalModel: TModel, updatedModel: TModel)
                         (implicit context: ExecutionContext): Future[Either[Option[TModel], Exception]] = {
    updatedModel.id match {
      case Some(id) => get(id)
      case None => Future(Left(None))
    }
  }

  protected def onDeleted(either: Either[Option[TModel], Exception])(implicit context: ExecutionContext): Future[Boolean] = {
    either match {
      case Left(model) => Future(true)
      case Right(ex) => Future(false)
    }

  }

  def create(entity: TModel)(implicit context: ExecutionContext): Future[Either[Option[TModel], Exception]] =
    entity.id match {
      case Some(id) =>
        for {
          _ <- collection.insert[TModel](entity)
          inserted <- onCreated(entity)
        } yield (inserted)
      case None =>
        Future(Left(None))
    }

  def update(entity: TModel, doUpsert: Boolean = false)(implicit context: ExecutionContext): Future[Either[Option[TModel], Exception]] = {
    if (!doUpsert)
      get(entity.id.get) flatMap {
        either =>
          val optModel = either match {
            case Left(model) => (model, None)
            case Right(ex) => (None, Some(ex))
          }
          optModel._1 match {
            case Some(originalEntity) =>
              for {
                _ <- collection.update[BSONDocument, TModel](BSONDocument("_id" -> entity.id.get), entity)
                updated <- onUpdated(originalEntity, entity)
              } yield updated

            case None =>
              optModel._2 match {
                case Some(error) => Future(Right(error))
                case None => Future(Left(None))
              }
          }
      }
    else {
      for {
        _ <- collection.update[BSONDocument, TModel](BSONDocument("_id" -> entity.id.get), entity, upsert = doUpsert)
        updated <- onUpdated(entity, entity)
      } yield updated

    }
  }

  def remove(id: BSONObjectID)(implicit context: ExecutionContext): Future[Boolean] = {
    for {
      original <- get(id)
      deleted <- collection.remove[BSONDocument](BSONDocument("_id" -> id)) map {
        _.ok
      }
      successful <- onDeleted(original)
    } yield (deleted && successful)
  }

  def get(id: BSONObjectID)(implicit context: ExecutionContext): Future[Either[Option[TModel], Exception]] = {
    collection.find(BSONDocument("_id" -> id)).cursor[TModel].headOption().map(model => Left(model))
  }

  def getAll(implicit context: ExecutionContext): Enumerator[TModel] = {
    collection.find(BSONDocument()).cursor[TModel].enumerate()
  }

  def search(criteria: ISearchCriteria[BSONDocument])(implicit context: ExecutionContext): Future[ISearchResults[TModel]] = {
    val sortDoc = criteria.sort map {
      sort => BSONDocument(sort.field -> BSONInteger(sort.direction))
    }

    db.command(Count(collectionName, Some(criteria.query))) map {
      count =>
        val enumerator = (criteria.page, sortDoc) match {
          case (Some(paging), Some(sort)) =>
            collection.find(criteria.query)
              .sort(sort)
              .options(QueryOpts(paging.skip))
              .cursor[TModel].enumerate() &> Enumeratee.take(paging.limit)
          case (Some(paging), None) =>
            collection.find(criteria.query)
              .cursor[TModel].enumerate() &> Enumeratee.take(paging.limit)
          case (None, Some(sort)) =>
            collection.find(criteria.query).sort(sort).cursor[TModel].enumerate()
          case (None, None) =>
            collection.find(criteria.query).cursor[TModel].enumerate()
        }
        MongoSearchResults[TModel](count, enumerator)
    }
  }

  def removeAll()(implicit context: ExecutionContext): Future[Boolean] = {
    collection.remove(BSONDocument(), firstMatchOnly = false).map(_.ok)
  }

  def searchSingle(criteria: ISearchCriteria[BSONDocument])(implicit context: ExecutionContext): Future[Option[TModel]] = {
    collection.find(criteria.query).cursor[TModel].headOption()
  }
}
