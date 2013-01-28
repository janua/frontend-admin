package controllers.event

import play.api.mvc._
import conf._
import controllers.{AuthAction, AuthLogging}
import play.api.libs.json.Json.toJson
import model.{Parent, Event}
import tools.Mongo.Events
import com.mongodb.casbah.Imports._

object EventController extends Controller with Logging with AuthLogging {

  def render() = AuthAction{ request =>
    Ok(views.html.events("{}", Configuration.stage))
  }

  /* API */

  def find() = AuthAction { request =>
    val results = Events.find().sort(Map("startDate" -> -1)).toSeq.map(Event.fromDbObject)
    Ok(Event.toJsonList(results)).as("application/json")
  }

  def create() = AuthAction{ request =>
    request.body.asJson.map(_.toString).map(Event.fromJson).map { event =>

      val parentEvent = event.parent.flatMap(p => Event.mongo.byId(p.id))
      val ancestorId = parentEvent.flatMap(_.ancestor.map(_.id)).orElse(parentEvent.map(_.id))

      val eventWithParent = event.copy(
        parent = parentEvent.map(p => Parent(p.id, Some(p.title))),
        ancestor = ancestorId.map(Parent(_))
      )

      Event.mongo.save(eventWithParent)

      Ok(Event.toJsonString(eventWithParent)).as("application/json")
    }.getOrElse(BadRequest(status("Invalid Json")).as("application/json"))
  }
  
  def read(eventId: String) = AuthAction{ request =>
    // eventId will have leading /
    Event.mongo.byId(eventId.drop(1)).map{ event =>   Ok(Event.toJsonString(event)) }
      .getOrElse(NotFound(status("no event found: " + eventId)).as("application/json"))
  }

  def update(eventId: String) = AuthAction{ request =>
    request.body.asJson.map(_.toString).map(Event.fromJson).map { event =>

      // eventId has a leading /
      val updateOk = Event.mongo.update(event, Some(eventId.drop(1)))
      if (updateOk) {
        Ok(Event.toJsonString(event)).as("application/json")
      } else {
        NotFound(status("no entity with id: " + eventId)).as("application/json")
      }
    }.getOrElse(BadRequest(status("Invalid Json")).as("application/json"))
  }

  def delete(eventId: String) = AuthAction{ request =>
    val deleteOk = Event.mongo.delete(eventId.drop(1))
    if (deleteOk) {
      Ok(status("deleted: " + eventId.drop(1))).as("application/json")
    } else {
      InternalServerError(status("error deleting document: " + eventId)).as("application/json")
    }
  }

  def status(msg: String) = toJson(Map("status" -> msg))
}
