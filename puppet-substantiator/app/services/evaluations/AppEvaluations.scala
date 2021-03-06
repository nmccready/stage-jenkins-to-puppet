package services.evaluations

import _root_.util.PlaySettings
import util.evaluations._
import util.FutureHelper._
import util.LogAndConsole
import models.mongo.reactive._
import play.api.libs.ws.WS
import services.repository.mongo.reactive.impls.IAppsRepository
import play.api.Logger._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json


object AppEvaluateHelpers extends IEvaluateDomain[App] {


  def filterOutImmediateForwardSlash(testUrl: String): String = {
    testUrl.startsWith("/") match {
      case true =>
        testUrl.replaceFirst("/", "")
      case false =>
        testUrl
    }
  }

  def futOneBoolToPassFail(oneFutBool: Future[Boolean], app: App): Future[IEvaluated[App]] = {
    for {
      bool <- oneFutBool
    } yield {
      if (bool)
        Pass(app)
      else
        Fail(app)
    }
  }

  def testApp(app: App) = app.actualCluster.map {
    implicit val log = logger
    machine =>
      app.port match {
        case Some(realPort) =>
          val url = "http://%s:%s/%s".format(machine.machineName, realPort, filterOutImmediateForwardSlash(app.testUrl))
          LogAndConsole.info("Testing Machine at %s".format(url))
          testMachine(app, machine.machineName, WS.url(url))
        case None =>
          val url = "http://%s/%s".format(machine.machineName, filterOutImmediateForwardSlash(app.testUrl))
          LogAndConsole.info("Testing Machine at %s".format(url))
          testMachine(app, machine.machineName, WS.url(url))
      }
  }

  def testMachine(appToUpdate: App, machineName: String, request: WS.WSRequestHolder): Future[Option[AppMachineState]] = {
    implicit val log = logger
    val optFutResponse = request.get()
      .map(Some(_))
      .recover {
      case _ => None
    }
    for {
      optResponse <- optFutResponse
    } yield {
      optResponse match {
        case Some(result) =>
          val logStr = "---- Machine: %s got the following response: %s ----".format(machineName, result.body)
          LogAndConsole.info(logStr)
          Some(AppMachineState(machineName, Some(result.body)))
        case None =>
          val logStr = "No Response from machine %s".format(machineName)
          LogAndConsole.info(logStr)
          None
      }
    }
  }
}

trait AbstractAppEvaluate extends IEvaluate[App] {
  implicit val implicitLogger = logger

  def handleFuturePassFail(futFailPass: Future[PassFail]) = {
    for {
      failPass <- futFailPass
    } yield {
      failPass match {
        case Fail(someApp) =>
          failAction(someApp)
        case Pass(someApp) =>
          passAction(someApp)
        case _ =>
      }
    }
  }
}

case class AppEvaluate(app: App, repo: IAppsRepository) extends AbstractAppEvaluate with IAppReadersWriters {
  def evaluate()(implicit context: ExecutionContext): Future[IEvaluated[App]] = {
    val futFailPass = app.id match {
      case Some(id) =>
        for {
          eitherAppOrException <- repo.get(id)
        } yield {
          eitherAppOrException match {
            case Left(optApp) =>
              optApp match {
                case Some(upApp) =>
                  if (upApp.actualCluster.forall(appMachine =>
                    appMachine.actual match {
                      case Some(actualState) =>
                        val result = actualState.contains(app.expected)
                        result match {
                          case true =>
                            LogAndConsole.info("Version Check for machine %s in %s application PASSED for %s version! Actual value is %s !".format(appMachine.machineName, app.name, app.expected, actualState))
                          case false =>
                            LogAndConsole.info("Version Check for machine %s in %s application FAILED for %s version! Actual value is %s !".format(appMachine.machineName, app.name, app.expected, actualState))
                        }
                        result
                      case None =>
                        false
                    }
                  ))
                    Pass(upApp)
                  else {
                    Fail(upApp)
                  }
                case None =>
                  Fail(app)
              }
            case Right(ex) =>
              LogAndConsole.info("Getting app failed! Failing at AppEvaluate!")
              Fail(app)
          }
        }
      case None =>
        future(Fail(app))
    }
    handleFuturePassFail(futFailPass)
    futFailPass
  }

  lazy val rollBackUrl = "http://" + PlaySettings.absUrl + "/rollback"

  def failAction(result: App) = {
    LogAndConsole.info("--- Application (%s) FAILED! Rollback should occur. ----".format(result.name))
    WS.url(rollBackUrl).post(Json.toJson(result))
  }


  def passAction(result: App) {
    LogAndConsole.info("--- Application (%s) PASSED! No rollback should occur. ----".format(result.name))
  }

  def name = app.name + "Validate"

}

case class QueryMachinesUpdateAppEvaluate(app: App, repo: IAppsRepository) extends AbstractAppEvaluate {

  import AppEvaluateHelpers._

  def evaluate()(implicit context: ExecutionContext): Future[IEvaluated[App]] = {
    app.id match {
      case Some(id) =>
        for {
          listOfOptionAppMachines <- Future.sequence(query)
          latestSomeApp <- futureEitherOfOptionExceptionToOption(repo.get(id))
          optApp <- latestSomeApp match {
            case Some(latestApp) =>
              LogAndConsole.info("Latest App Found")
              val mergedAppMachList = latestApp.mergeActualCluster(listOfOptionAppMachines.flatMap(opt => opt))
              LogAndConsole.info("Updating AppMachineState with: " + mergedAppMachList.map(state => " {machine: " + state.machineName + " actual: " + state.actual + "} ")
                .reduce(_ + _))
              futureEitherOfOptionExceptionToOption(repo.update(latestApp.copy(actualCluster = mergedAppMachList)))
            case None =>
              future(None)
          }
        } yield {
          optApp match {
            case Some(updated) =>
              LogAndConsole.info("Done Iterating AppMachineState Futures, ends in PASS!!")
              Pass(app)
            case None =>
              LogAndConsole.info("Done Iterating AppMachineState Futures, ends in FAIL!!")
              Fail(app)
          }
        }
      case None =>
        future(Fail(app))
    }
  }

  protected def query = testApp(app)

  def failAction(result: App) = {
  }


  def passAction(result: App) {
  }

  def name = app.name + "Query"
}
