package controllers

import play.api.mvc._
import conf._
import play.api.libs.ws.WS
import java.net.URLEncoder
import org.apache.commons.lang.StringEscapeUtils

object Autocomplete extends Controller with Logging with AuthLogging {

  implicit def string2encodings(s: String) = new {
    lazy val urlEncoded = URLEncoder.encode(s, "utf-8")
    lazy val javascriptEscaped = StringEscapeUtils.escapeJavaScript(s)
  }

  def tag(q: String, callback: String) = AuthAction { request =>
    val url = "%s/tags?format=json&page-size=50&api-key=%s&callback=%s&q=%s".format(
      Configuration.api.host,
      Configuration.api.key,
      callback.javascriptEscaped.urlEncoded,
      q.javascriptEscaped.urlEncoded
    )

    log("Proxying API query to: %s" format url, request)

    Async {
      WS.url(url).get().map { response =>
        Ok(response.body).as("application/javascript")
      }
    }
  }
}