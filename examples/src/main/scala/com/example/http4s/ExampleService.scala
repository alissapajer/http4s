package com.example.http4s

import _root_.argonaut.JString

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

import org.http4s.headers.{`Transfer-Encoding`, `Content-Type`}
import org.http4s._
import org.http4s.MediaType._
import org.http4s.dsl._
import org.http4s.argonaut._
import org.http4s.scalaxml._
import org.http4s.server._
import org.http4s.server.middleware.EntityLimiter
import org.http4s.server.middleware.EntityLimiter.EntityTooLarge
import org.http4s.server.middleware.PushSupport._
import org.http4s.twirl._

import scalaz.stream.Process
import scalaz.stream.time
import scalaz.concurrent.Task
import scalaz.concurrent.Strategy.DefaultTimeoutScheduler

import _root_.argonaut._
import Argonaut._

object ExampleService {

  def service(implicit executionContext: ExecutionContext = ExecutionContext.global): HttpService =
    service1(executionContext) orElse service2 orElse ScienceExperiments.service

  def service1(implicit executionContext: ExecutionContext) = HttpService {

    case req @ GET -> Root =>
      // Supports Play Framework template -- see src/main/twirl.
      Ok(html.index())

    case GET -> Root / "ping" =>
      // EntityEncoder allows for easy conversion of types to a response body
      Ok("pong")

    case GET -> Root / "future" =>
      // EntityEncoder allows rendering asynchronous results as well
      Ok(Future("Hello from the future!"))

    case GET -> Root / "streaming" =>
      // Its also easy to stream responses to clients
      Ok(dataStream(100)).withHeaders(`Transfer-Encoding`(TransferCoding.chunked))

    case req @ GET -> Root / "ip" =>
      // Its possible to define an EntityEncoder anywhere so you're not limited to built in types
      val json = jSingleObject("origin", jString(req.remoteAddr.getOrElse("unknown")))
      Ok(json)

    case req @ GET -> Root / "redirect" =>
      // Not every response must be Ok using a EntityEncoder: some have meaning only for specific types
      TemporaryRedirect(uri("/http4s/"))

    case GET -> Root / "content-change" =>
      // EntityEncoder typically deals with appropriate headers, but they can be overridden
      Ok("<h2>This will have an html content type!</h2>")
          .withHeaders(`Content-Type`(`text/html`))

    ///////////////////////////////////////////////////////////////
    //////////////// Dealing with the message body ////////////////
    case req @ POST -> Root / "echo" =>
      // The body can be used in the response
      Ok(req.body)
        .withHeaders(`Content-Type`(`text/plain`), `Transfer-Encoding`(TransferCoding.chunked))

    case req @ GET -> Root / "echo" =>
      Ok(html.submissionForm("echo data"))

    case req @ POST -> Root / "echo2" =>
      // Even more useful, the body can be transformed in the response
      Ok(req.body.map(_.drop(6)))
        .withHeaders(`Content-Type`(`text/plain`))

    case req @ GET -> Root / "echo2" =>
      Ok(html.submissionForm("echo data"))

    case req @ POST -> Root / "sum"  =>
      // EntityDecoders allow turning the body into something useful
      req.decode[UrlForm] { data => 
        data.values.get("sum") match {
          case Some(Seq(s, _*)) =>
            val sum = s.split(' ').filter(_.length > 0).map(_.trim.toInt).sum
            Ok(sum.toString)

          case None => BadRequest(s"Invalid data: " + data)
        }
      } handleWith {    // We can handle errors using Task methods
        case e: NumberFormatException => BadRequest("Not an int: " + e.getMessage)
      }

    case req @ GET -> Root / "sum" =>
      Ok(html.submissionForm("sum"))

    ///////////////////////////////////////////////////////////////
    //////////////// Form encoding example ////////////////////////
    case req @ GET -> Root / "form-encoded" =>
      Ok(html.formEncoded())

    case req @ POST -> Root / "form-encoded" =>
      // EntityDecoders return a Task[A] which is easy to sequence
      req.decode[UrlForm] { m =>
        val s = m.values.mkString("\n")
        Ok(s"Form Encoded Data\n$s")
      }

    ///////////////////////////////////////////////////////////////
    //////////////////////// Server Push //////////////////////////
    case req @ GET -> Root / "push" =>
      // http4s intends to be a forward looking library made with http2.0 in mind
      val data = <html><body><img src="image.jpg"/></body></html>
      Ok(data)
        .withHeaders(`Content-Type`(`text/html`))
        .push("/image.jpg")(req)

    case req @ GET -> Root / "image.jpg" =>
      StaticFile.fromResource("/nasa_blackhole_image.jpg", Some(req))
        .map(Task.now)
        .getOrElse(NotFound())
  }

  // Services don't have to be monolithic, and middleware just transforms a service to a service
  def service2 = EntityLimiter(HttpService {
    case req @ POST -> Root / "short-sum"  =>
      req.decode[UrlForm] { data =>
        data.values.get("short-sum") match {
          case Some(Seq(s, _*)) =>
            val sum = s.split(" ").filter(_.length > 0).map(_.trim.toInt).sum
            Ok(sum.toString)

          case None => BadRequest(s"Invalid data: " + data)
        }
      } handleWith { // We can use Task functions to manage errors
        case EntityTooLarge(max) => PayloadTooLarge(s"Entity too large. Max size: $max")
      }

    case req @ GET -> Root / "short-sum" =>
      Ok(html.submissionForm("short-sum"))
  }, 3)

  // This is a mock data source, but could be a Process representing results from a database
  def dataStream(n: Int): Process[Task, String] = {
    implicit def defaultSecheduler = DefaultTimeoutScheduler
    val interval = 100.millis
    val stream = time.awakeEvery(interval)
      .map(_ => s"Current system time: ${System.currentTimeMillis()} ms\n")
      .take(n)

    Process.emit(s"Starting $interval stream intervals, taking $n results\n\n") ++ stream
  }
}
