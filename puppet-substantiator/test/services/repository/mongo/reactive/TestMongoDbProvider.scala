
package services.repository.mongo.reactive

import services.repository.IDbProvider
import reactivemongo.api.{MongoDriver, DefaultDB}
import collection.JavaConversions._
import concurrent.ExecutionContext.Implicits.global
import com.typesafe.config.ConfigFactory
import java.io.File

trait TestMongoDbProvider extends IDbProvider[DefaultDB] {
  lazy val driver = new MongoDriver()
  lazy val _config = ConfigFactory.parseFile(new File("conf/test.conf"))
  override lazy val db: DefaultDB = driver.connection(_config.getStringList("mongodb.servers").toList)(_config.getString("mongodb.db"))
}
