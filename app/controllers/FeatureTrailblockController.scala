package controllers

import play.api.mvc._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import tools.S3
import play.api.libs.json.{JsValue, Json}
import play.api.libs.json.Json.toJson
import conf._

object FeatureTrailblockController extends Controller with Logging with AuthLogging {

  def edit() = AuthAction{ request =>
    log("loaded config", request)
    val promiseOfConfig = Akka.future(S3.getConfig)

    Async{
      promiseOfConfig.map(config => Ok(views.html.edit(config.getOrElse("{}"), Configuration.stage)))
    }
  }

  def save() = AuthAction{ request =>
    log("saved config", request)
    request.body.asJson match {
      case Some(json) =>
        val promiseOfSavedConfig = Akka.future{ saveConfigOrError(json) }
        Async{ promiseOfSavedConfig.map( result => result) }
      case None => BadRequest(toJson(Map("status" -> "Invalid Json")))
    }
  }

  private def saveConfigOrError(json: JsValue) = try {
    S3.putConfig(Json.stringify(json))
    log.info("config successfully updated")
    ConfigUpdateCounter.recordCount(1)
    Ok(toJson(Map("status" -> "Configuration updated")))
  } catch { case e =>
      log.error("exception saving config", e)
      ConfigUpdateErrorCounter.recordCount(1)
      InternalServerError(toJson(Map("status" -> e.getMessage)))
  }
}
