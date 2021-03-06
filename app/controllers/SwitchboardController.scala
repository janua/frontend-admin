package controllers

import play.api.mvc._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import tools.S3
import conf._
import io.Source

case class Switch(name: String, isOn: Boolean, description: String)

object SwitchboardController extends Controller with AuthLogging with Logging {

  val SwitchPattern = """([a-z\d-]+)=(on|off)""".r

  //displays in the order they appear in this list
  val switches = Seq(
    //switch names can be letters numbers and hyphens only
    Switch("audience-science", true, "If this switch is on the Audience Science will be enabled."),
    Switch("auto-refresh", true, "Enables auto refresh in pages such as live blogs and live scores. Turn off to help handle exceptional load."),
    Switch("double-cache-times", false, "Doubles the cache time of every endpoint. Turn on to help handle exceptional load."),
    Switch("web-fonts", true, "If this is switched on then the custom Guardian web font will load."),
    Switch("related-content", true, "If this switch is turned on then related content will show. Turn off to help handle exceptional load."),
    Switch("omniture-verification", false, "If this switch is turned on then a separate call to Omniture will be made to help verify our tracking."),
    Switch("google-search", false, "If this switch is turned on then Google search is added to the sections nav."),
    Switch("square-images", false, "If this switch is turned on then square images willl be used for trailblock thumbnails."),
    Switch("network-front-appeal", false, "Switch to show the appeal trailblock on the network front."),
    Switch("witness-video", true, "Switch this switch off to disable witness video embeds."),
    Switch("experiment-story-module-01", false, "Enable storified articles."),
    Switch("story-front-trails", false, "Switch on to enable front trails for latest stories."),
    Switch("social-icons", false, "Enable the social media share icons (facebook, twitter etc.)"),
    Switch("quantcast", false, "Enable the Quantcast audience segment tracking."),
    Switch("integration-test-switch", true, "Switch that is only used while running tests. You never need to change this switch"),
    Switch("story-version-b", false, "Switch to enable version B of story page."),
    Switch("homescreen", false, "If this switch is enabled the add-to-homescreen popup will plague iOS users."),
    Switch("optimizely", false, "If this switch is on Optimizely will be enabled."),
    Switch("image-server", false, "If this switch is on images will be served off i.guim.co.uk (dynamic image host)"),
    Switch("adverts", true, "If this switch is on then OAS adverts will be loaded with JavaScript"),
    Switch("video-adverts", false, "If this switch is on then OAS video adverts will be loaded with JavaScript"),
    Switch("ab-related-content-v2", false, "If this switch is on related content AB test will be enabled."),
    Switch("css-lazy-load", false, "If this switch is on then use a slimmed down, lazy-loaded css setup (css in head with server sepcific files)")
  )

  def render() = AuthAction{ request =>
   log("loaded Switchboard", request)

    val promiseOfSwitches = Akka.future(S3.getSwitches)

    Async{
      promiseOfSwitches.map{ switchesOption =>

        val switchStates = Source.fromString(switchesOption.getOrElse("")).getLines.map{
          case SwitchPattern(key, value) => (key, value == "on")
        }.toMap

        val switchesWithState = switches.map{ switch => switch.copy(isOn = switchStates.get(switch.name).getOrElse(switch.isOn)) }
        Ok(views.html.switchboard(switchesWithState, Configuration.stage))
      }
    }
  }

  def save() = AuthAction{ request =>
    log("saved switchboard", request)

    val switchValues = request.body.asFormUrlEncoded.map{ params =>
      switches.map{ switch => switch.name + "=" + params.get(switch.name).map(v => "on").getOrElse("off") }
    }.get

    val promiseOfSavedSwitches = Akka.future(saveSwitchesOrError(switchValues.mkString("\n")))

    Async{
      promiseOfSavedSwitches.map{ result =>
        result
      }
    }
  }

  private def saveSwitchesOrError(switches: String) = try {
    S3.putSwitches(switches)
    log.info("switches successfully updated")
    SwitchesUpdateCounter.recordCount(1)
    Redirect(routes.SwitchboardController.render())
  } catch { case e =>
      log.error("exception saving switches", e)
      SwitchesUpdateErrorCounter.recordCount(1)
      Redirect(routes.SwitchboardController.render()).flashing("error" -> "Error saving switches '%s'".format(e.getMessage))
  }
}
