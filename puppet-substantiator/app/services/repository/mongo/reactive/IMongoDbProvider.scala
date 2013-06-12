package services.repository.mongo.reactive

import reactivemongo.api.{DefaultDB, MongoDriver}
import services.repository.IDbProvider
import util.ConfigurationProvider
import collection.JavaConversions._
import concurrent.ExecutionContext.Implicits.global

trait IMongoDbProvider extends IDbProvider[DefaultDB] {
  def db: DefaultDB = GlobalReactiveMongoDb.db
}

object GlobalReactiveMongoDb extends ConfigurationProvider {
  lazy val driver = new MongoDriver()
  lazy val db = {
    val servers = configuration.getStringList("mongodb.servers").get.toList
    val database = configuration.getString("mongodb.db").get
    driver.connection(servers)(database)
  }
}
