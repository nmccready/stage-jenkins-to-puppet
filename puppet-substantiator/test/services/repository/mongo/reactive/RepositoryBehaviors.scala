package services.repository.mongo.reactive

import org.scalatest.{BeforeAndAfter, WordSpec}
import org.scalatest.matchers.ShouldMatchers
import play.api.libs.iteratee.Iteratee
import reactivemongo.bson._
import reactivemongo.bson.DefaultBSONHandlers._
import models.IModel
import services.repository._
import concurrent._
import concurrent.ExecutionContext.Implicits.global
import concurrent.duration._

trait RepositoryBehaviors[TModel <: IModel[BSONObjectID]] {
  this: WordSpec with BeforeAndAfter with ShouldMatchers with TestMongoDbProvider with IMongoRepositoryProvider[TModel] =>

  def collectionName: String

  def createEntities(numberOfEntities: Int): Future[Int]

  def createEntity: TModel

  def baseModelRepository(implicit reader: BSONDocumentReader[TModel], writer: BSONDocumentWriter[TModel]) = {

    "create" should {
      "return the entity after saving" in {
        val entity = createEntity
        val either = Await.result(repository.create(entity), 10 seconds)
        val model = either match{
          case Left(model) => model
          case Right(ex) => None
        }
        model should be('defined)
      }
    }

    "update" should {
      "return the entity after saving" in {
        val entity = createEntity
        val futureResult = for {
          _ <- repository.create(entity)
          updated <- repository.update(entity)
        } yield updated

        val either = Await.result(futureResult, 10 seconds)
        val model = either match{
          case Left(model) => model
          case Right(ex) => None
        }
        model should be('defined)

      }
    }

    "save" should {
      "return the entity after saving VIA CREATION" in {
        val entity = createEntity
        val either = Await.result(repository.save(entity), 10 seconds)
        either match {
          case Left(model) => model should be('defined)
          case Right(ex) => throw ex
        }
      }
      "return the entity after saving VIA UPDATE" in {
        val entity = createEntity
        val futureResult = for {
          _ <- repository.create(entity)
          updated <- repository.save(entity)
        } yield updated

        val either = Await.result(futureResult, 10 seconds)
        val model = either match{
          case Left(model) => model
          case Right(ex) => None
        }
        model should be('defined)
      }
    }

    "remove" should {
      "return true when the entity is successfully removed" in {
        val entity = createEntity
        val futureResult = for {
          _ <- repository.create(entity)
          result <- repository.remove(entity.id.get)
        } yield result

        Await.result(futureResult, 10 seconds) should be(true)
      }
    }

    "get" should {
      "return the entity with the given id" in {
        val entity = createEntity
        val futureResult = for {
          either <- repository.create(entity)
          found <- {
            val returned = either match {
              case Left(model) => model
              case Right(ex) => None
            }
            repository.get(returned.get.id.get)
          }
        } yield found

        val model = Await.result(futureResult, 10 seconds) match {
          case Left(model) => model
          case Right(ex) => None
        }
        model should be('defined)
      }

      "return None when an entity with the given id does not exist" in {
        Await.result(
          repository.get(BSONObjectID.generate),
          10 seconds
        ).left.get should be(None)
      }
    }

    "getAll" should {
      "return all entities" in {
        val futureResult = for {
          _ <- createEntities(20)
          results <- repository.getAll.run(Iteratee.getChunks)
        } yield results

        val res = Await.result(futureResult, 10 seconds)
        res should have(length(20))
      }
    }

    "search" should {
      "return the first 10 entities with a result count of 20" in {
        val futureResult = for {
          _ <- createEntities(20)
          result <- repository.search(MongoSearchCriteria(BSONDocument(), None, Some(Paging(0, 10))))
          list <- result.results.run(Iteratee.getChunks)
        } yield (result, list)


        val result = Await.result(futureResult, 10 seconds)
        result._1.count should be(20)
        result._2 should have(length(10))
      }

      "return the second 10 entities with a result count of 20" in {
        val futureResult = for {
          _ <- createEntities(20)
          result <- repository.search(MongoSearchCriteria(BSONDocument(), None, Some(Paging(10, 10))))
          list <- result.results.run(Iteratee.getChunks)
        } yield (result, list)


        val result = Await.result(futureResult, 10 seconds)
        result._1.count should be(20)
        result._2 should have(length(10))
      }
    }
  }
}
