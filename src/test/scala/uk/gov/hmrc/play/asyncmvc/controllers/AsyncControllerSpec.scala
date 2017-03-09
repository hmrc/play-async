/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.play.asyncmvc.controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.Suite
import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.{FakeApplication, FakeRequest}
import uk.gov.hmrc.play.asyncmvc.async.TimedEvent
import uk.gov.hmrc.play.asyncmvc.example.controllers.{AsyncMap, ExampleAsyncController, InputForm}
import uk.gov.hmrc.play.asyncmvc.model.AsyncMvcSession
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FakePlayApplication extends WithFakeApplication {
  this: Suite =>

  override lazy val fakeApplication = FakeApplication()
}

class AsyncControllerSpec extends AsyncSetup with UnitSpec with FakePlayApplication with ScalaFutures with Eventually {

  // Blocking mode will block the client, waiting for off-line task to be processed. This mode basically saves the Polling page from being presented to the user!
  // The Polling page does not necessarily need to be returned in this mode if the server side request completes within the expected time window!
  "in async blocking mode, disconnect the HTTP client from the server side request and wait for the task to complete without returning the poll page" should {

    "poll for the server side request to complete successfully and the success page returned - SUBMIT -> REDIRECT -> WAIT -> COMPLETE-PAGE" in new Blocking {

      invokeTestBlockAction(form, controller, ident,
        s"""<p>The task has completed successfully. Id of the form value originally submitted is ${form.id}.0 </p>""", 0)
    }

    "poll for the server side request to complete within the expected time window, on timeout the timeout page is returned - SUBMIT -> REDIRECT -> WAIT -> TIMEOUT-PAGE" in new SetupBlockingTimeout {

      invokeTestBlockAction(form, controller, ident,
        s"<p>The request has timed out!</p>", 3000, Interval(Span(100, Milliseconds)))
    }

    "poll for the server side request to complete, the server-side request generates an exception, the error page is returned - SUBMIT -> REDIRECT -> WAIT -> ERROR-PAGE " in new SetupBlockingError {

      invokeTestBlockAction(form, controller, ident,
        s"<p>An error occurred processing the request!</p>", 2000)
    }

    "not submit the server side request for async processing when the throttle is exceeded, the throttle limit page is returned - SUBMIT -> THROTTLE-PAGE " in new SetupBlockingThrottle {

      val result: Result = invokeAsyncControllerFunction(form, controller)
      status(result) shouldBe 200
      bodyOf(result) should include regex "<p>The throttle limit has been reached and the request cannot be processed!</p>"
      // Verify the session object does not exist.
      result.session.get(controller.AsyncMVCSessionId) shouldBe None

      result.session.get(controller.testUpdateSessionValue) shouldBe None
    }

  }

  "Invoking the controller to offline the task should " should {

    "return an error status response when the task failed to be stored to the cache" in new SetupNonBlockingCacheError {
      val result = invokeAsyncControllerFunction(form, controller)
      status(result) shouldBe 200
      bodyOf(result) should include regex "<p>An error occurred processing the request!</p>"

      eventually(Timeout(Span(10000, Milliseconds)), Interval(Span(2, Seconds))) {
        uk.gov.hmrc.play.asyncmvc.async.Throttle.current shouldBe 0
      }
    }
  }

  // This mode will not block on the server waiting for the async task to complete, and the polling page will be returned immediately.
  "in async non-blocking mode, disconnect the HTTP client from the server side request and poll for the task to complete " should {

    "poll for the server side request to complete successfully and the success page is returned - SUBMIT -> POLL -> COMPLETE-PAGE" in new SetupNonBlocking {
      invokeTestNonBlockAction(form, controller, ident,
        s"""<p>The task has completed successfully. Id of the form value originally submitted is ${form.id}.0 </p>""", "SUCCESS")
    }

    "poll for the server side request to complete within the expected time window, on timeout the timeout page is returned - SUBMIT -> POLL -> TIMEOUT-PAGE" in new SetupNonBlockingTimeout {
      invokeTestNonBlockAction(form, controller, ident,
        s"""<p>The request has timed out!</p>""", "TIMEOUT")
    }

    "poll for the server side request to complete, the server-side request generates an exception, the error page is returned - SUBMIT -> POLL -> ERROR-PAGE " in new SetupNonBlockingError {

      invokeTestNonBlockAction(form, controller, ident,
        s"""<p>An error occurred processing the request!</p>""", "ERROR")
    }

    "not submit the server side request for async processing when the throttle is exceeded, the throttle limit page is returned - SUBMIT -> THROTTLE-PAGE " in new SetupNonBlockingThrottle {
      val result: Result = invokeAsyncControllerFunction(form, controller)
      status(result) shouldBe 200
      bodyOf(result) should include regex "<p>The throttle limit has been reached and the request cannot be processed!</p>"
      result.session.get(controller.AsyncMVCSessionId) shouldBe None
    }

  }

  "The async action function wrapper " should {

    "display the requested page when no async task is running" in new Blocking {
      val result = await(controller.captureController(req))
      status(result) shouldBe 200
      bodyOf(result) should include regex "Please enter a value for both the message and the Id"
    }

    "not display the requested page and force the user request to the poll page when an async task is running" in new SetupBlocking {
      override val testSessionId="TestIdAsyncWrapper"

      invokeASyncWrapper(form, controller, ident,
        s"""<p>The task has completed successfully. Id of the form value originally submitted is ${form.id}.0 </p>""")
    }

  }

  "Executing concurrent http requests through the async framework with variable connector service delays " should {

      "successfully process all concurrent requests and once all tasks are complete, verify the throttle value is 0" in {
        val time=System.currentTimeMillis()

        val concurrentRequests = (0 until 15).foldLeft(Seq.empty[SetupConcurrencyDynamicBlocking]) {
          (list, counter) => {
            val asyncRequest = new SetupConcurrencyDynamicBlocking {
              override val testSessionId=s"TestIdConcurrent$counter"
              override val form = InputForm("Example Data", counter)
            }
            list ++ Seq(asyncRequest)
          }
        }

        val result = concurrentRequests.map { asyncTestRequest =>

          val delay = scala.util.Random.nextInt(50)
          TimedEvent.delayedSuccess(delay, 0).map(a => {
            implicit val reqImpl = asyncTestRequest.req
            val ident = s"Example-${asyncTestRequest.testSessionId}"

            invokeTestBlockAction(asyncTestRequest.form, asyncTestRequest.controller, ident,
              s"<p>The task has completed successfully. Id of the form value originally submitted is ${asyncTestRequest.form.id}.0 </p>", 90000)
           })
        }

        eventually(Timeout(Span(95000, Milliseconds)), Interval(Span(2, Seconds))) {
          await(Future.sequence(result))
        }

        eventually(Timeout(Span(95000, Milliseconds)), Interval(Span(2, Seconds))) {
          uk.gov.hmrc.play.asyncmvc.async.Throttle.current shouldBe 0
        }
       println("Time spent processing... " + (System.currentTimeMillis()-time))
      }

  }

  "When an application defines multiple async controllers, the async framework safe-guard" should {

    "automatically redirect to the correct poll URL when a poll request is sent to the wrong controller - i.e. book marking poll URLs and hitting the incorrect poll URL when a task is executing" in new SetupNonBlockingSafeGuard {
      invokeTestNonBlockActionAndPollAgainstInvalidController(form, controller, ident, "some-url-for-id-example2")
    }
  }

  def invokeTestNonBlockActionAndPollAgainstInvalidController(form:InputForm, controller:ExampleAsyncController, testSessionId:String, response:String)(implicit request:Request[AnyContent]) = {
    val result1 = invokeAsyncControllerFunction(form, controller)
    status(result1) shouldBe 200
    bodyOf(result1) should include regex s"""<p>Polling...please wait for task to complete.</p>"""

    val session = result1.session.get(controller.AsyncMVCSessionId)
    val jsonSession=Json.parse(session.get).as[AsyncMvcSession]
    jsonSession.id shouldBe testSessionId

    // Note: The session is re-created with an Id that is not associated with ExampleAsyncController.
    val requestWithSession = FakeRequest().withSession(controller.AsyncMVCSessionId -> controller.buildSession(AsyncMap.id2,testSessionId))

    // Invoke the async controller with the modified session.
    val result2: Result = await(controller.poll()(requestWithSession))
    status(result2) shouldBe 303
    result2.header.headers.get("Location") shouldBe Some(response)
  }

  def invokeTestNonBlockAction(form:InputForm, controller:ExampleAsyncController, testSessionId:String, response:String, sessionTestValue:String)(implicit request:Request[AnyContent]) = {
    val result1 = invokeAsyncControllerFunction(form, controller)
    status(result1) shouldBe 200
    bodyOf(result1) should include regex s"""<p>Polling...please wait for task to complete.</p>"""

    val session = result1.session.get(controller.AsyncMVCSessionId)
    val jsonSession=Json.parse(session.get).as[AsyncMvcSession]
    jsonSession.id shouldBe testSessionId

    val requestWithSession = FakeRequest().withSession(controller.AsyncMVCSessionId -> controller.buildSession("Example",testSessionId))

    eventually(Timeout(Span(6, Seconds))) {
      val result2: Result = await(controller.poll()(requestWithSession))
      status(result2) shouldBe 200
      bodyOf(result2) should include regex response

      result2.session.get(controller.AsyncMVCSessionId) shouldBe None

      // Verify the callback can update session.
      result2.session.get(controller.testUpdateSessionValue) shouldBe Some(sessionTestValue)
    }

    eventually(Timeout(Span(6, Seconds))) {
      uk.gov.hmrc.play.asyncmvc.async.Throttle.current shouldBe 0
    }

  }

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  def invokeTestBlockAction(form:InputForm, controller:ExampleAsyncController, testSessionId:String, response:String, timeToWait:Int, interval : Interval = Interval(Span(3, Seconds)))(implicit request:Request[AnyContent]) = {
    val result = invokeAsyncControllerFunction(form, controller)
    status(result) shouldBe 303
    result.header.headers.get("Location") shouldBe Some("/wait")

    val sessionObject = Json.parse(result.session.data.get(controller.AsyncMVCSessionId).get).as[AsyncMvcSession]
    sessionObject.id shouldBe testSessionId

    val requestWithSession = FakeRequest().withSession(controller.AsyncMVCSessionId -> controller.buildSession("Example",testSessionId))

    eventually(Timeout(Span(timeToWait, Milliseconds)), interval) {
      val resultPoll: Result = await(controller.poll()(requestWithSession))
      status(resultPoll) shouldBe 200

      bodyOf(resultPoll) should include regex response
      resultPoll.session.get(controller.AsyncMVCSessionId) shouldBe None
    }

  }

  def invokeASyncWrapper(form:InputForm, controller:ExampleAsyncController, testSessionId:String, response:String)(implicit request:Request[AnyContent]) = {
    val result: Result = invokeAsyncControllerFunction(form, controller)
    status(result) shouldBe 303
    result.header.headers.get("Location") shouldBe Some("/wait")

    val sessionObject = Json.parse(result.session.data.get(controller.AsyncMVCSessionId).get).as[AsyncMvcSession]
    sessionObject.id shouldBe testSessionId

    val requestWithSession = FakeRequest().withSession(controller.AsyncMVCSessionId -> controller.buildSession("Example",testSessionId))
    val resultPoll = await(controller.captureController()(requestWithSession))

    status(resultPoll) shouldBe 200
    bodyOf(resultPoll) should include regex s"""<p>Polling...please wait for task to complete.</p>"""

    eventually(Timeout(Span(7000, Millis))) {
      val resultPollInner = await(controller.poll()(requestWithSession))
      status(resultPollInner) shouldBe 200
      bodyOf(resultPollInner) should include regex response
    }

    eventually(Timeout(Span(7000, Millis))) {
      uk.gov.hmrc.play.asyncmvc.async.Throttle.current shouldBe 0
    }

  }

  def invokeAsyncControllerFunction(form:InputForm, controller:ExampleAsyncController)(implicit request:Request[AnyContent]) = {
    val request = FakeRequest().withFormUrlEncodedBody("message" -> form.message, "id" -> (form.id+""))
    await(controller.submitController()(request))
  }

}
