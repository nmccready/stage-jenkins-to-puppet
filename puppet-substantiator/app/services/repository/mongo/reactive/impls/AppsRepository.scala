package services.repository.mongo.reactive.impls

import models.mongo.reactive._
import util.ConfigurationProvider
import services.repository.mongo.reactive._
import concurrent._

trait IAppsRepository extends MongoBaseRepository[App] with IMongoUniqueCheckRepository[App] {
  protected def machineRepo: IMachinesRepository with IMongoDbProvider

}

abstract class AppsRepository
  extends IAppsRepository with IAppReadersWriters {

  override protected def collectionName = "apps"

  def ifMachinesExistExecute(entity: App,
                             func: App => Future[Either[Option[App], Exception]])
                            (implicit context: ExecutionContext): Future[Either[Option[App], Exception]] = {
    //Put machineRepo on this context! Make sure!
    val futMap = machineRepo.machinesExistByNames(entity.actualCluster.map(_.machineName))
    for {
      nameMap <- futMap
      allNamesExist <- future(nameMap.forall(_._2 == true))
      optionalApp <- {
        if (allNamesExist)
          func.apply(entity)
        else
          future(Right(new AppRepositoryException("Machines do not exist for App, create machines first")))
      }
    } yield (optionalApp)
  }

  override def create(entity: App)(implicit context: ExecutionContext): Future[Either[Option[App], Exception]] =
    ifMachinesExistExecute(entity,
      (ent: App) =>
        super.create(ent)(context)
    )(context)

  override def update(entity: App, doCreateIfNonExistent: Boolean = false)(implicit context: ExecutionContext): Future[Either[Option[App], Exception]] = {
    ifMachinesExistExecute(entity,
      (ent: App) => super.update(ent, doCreateIfNonExistent)(context)
    )(context)
  }
}

trait IAppsRepositoryProvider
  extends IMongoRepositoryProvider[App]
  with IAppReadersWriters {

  def repository: IAppsRepository
}

trait AppRepositoryProvider
  extends IAppsRepositoryProvider {

  override val repository = AppsRepository
}

object AppsRepository
  extends AppsRepository
  with IMongoDbProvider
  with ConfigurationProvider {
  protected def machineRepo = MachinesRepository
}

class AppRepositoryException(msg: String) extends Exception(msg)