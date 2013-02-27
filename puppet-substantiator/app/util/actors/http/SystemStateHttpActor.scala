package util.actors.http

import akka.actor.Actor
import spray.util.SprayActorLogging
import spray.http._
import HttpMethods._
import spray.http._
import globals._
import spray.can.client.{DefaultHttpClient, HttpDialog}
import spray.http.HttpRequest
import models.mongo.reactive.ActorState
import models.mongo.reactive.ActorStateDomain._
import spray.json.{BasicFormats}
import concurrent.ExecutionContext.Implicits.global

class SystemStateHttpActor(provider: IActorsProvider, serviceUrl: String)
  extends Actor
  with SprayActorLogging with IActorNames with BasicFormats {

  import util.actors.fsm.CancellableMapFSMDomainProvider.domain._

  val httpClient = DefaultHttpClient(provider.actors().system)

  def receive = {
    case HttpRequest(GET, "/actorHook/routes", _, _, _) =>
      sender ! HttpResponse.apply(status = StatusCodes.Accepted,
        entity = HttpBody(MediaTypes.`text/html`,
          <html>
            <body>
              <h1>Actor Hook hit
                <i>spray-can</i>
                !</h1>
              <p>Defined resources:</p>
              <ul>
                <li>
                  <a href="/actorHook/routes">/actorHook/routes</a>
                </li>
                <li>
                  <a href="/actorHook/scheduled">/actorHook/scheduled</a>
                </li>
              </ul>
            </body>
          </html>.toString
        ))
    case HttpRequest(GET, "/actorHook/scheduled", _, _, _) =>
      provider.actors().getActor(scheduleName) ! Status
      sender ! HttpResponse.apply(status = StatusCodes.Accepted,
        entity = HttpBody(MediaTypes.`text/html`,
          <html>
            <body>
              <h1>Actor Hook hit from
                <i>spray-can</i>
                !</h1>
            </body>
          </html>.toString
        ))
    case Batch(map) =>
      //send state to actors repository service
      val state = stringBoolMapFormat.write(map.map(m => (m._1 -> m._2.isCancelled)))
        .asJsObject("unable to convert!").compactPrint
      val ent = HttpEntity {
        Some {
          HttpBody(ContentType.`application/json`,
            jsonSprayFormat.write(
              ActorState("scheduler",
                true,
                state)
            ).compactPrint)
        }
      }
      val host = serviceUrl.split(':')(0)
      val port = serviceUrl.split(':')(1).toInt

      val responseF =
        HttpDialog(httpClient, host, port)
          .send(
          HttpRequest(method = HttpMethods.POST,
            uri = "/actors",
            headers =
              HttpHeaders.`Content-Type`(ContentType.`application/json`) :: Nil,
            entity = ent)
        )
          .end
      for {
        response <- responseF
      }
      yield {
        log.info(
          """|Result from host:
            |status : {}
            |headers: {}
            |body   : {}""".stripMargin,
          response.status, response.headers.mkString("\n  ", "\n  ", ""), response.entity.asString
        )
      }
    case _ =>
      sender ! HttpResponse.apply(status = StatusCodes.Accepted,
        entity = HttpBody(MediaTypes.`text/html`,
          <html>
            <body>
              <h1>Actor Hook hit
                <i>spray-can</i>
                !</h1>
              <p>Resource Not FOUND!!</p>
            </body>
          </html>.toString
        ))
  }
}