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

package uk.gov.hmrc.play.asyncmvc.async

import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Span}
import play.api.libs.json.Json
import play.api.mvc.Controller
import uk.gov.hmrc.play.asyncmvc.controllers.FakePlayApplication
import uk.gov.hmrc.play.asyncmvc.example.connectors.Stock
import uk.gov.hmrc.play.asyncmvc.model.{StatusCodes, TaskCache}
import uk.gov.hmrc.play.test.UnitSpec

import uk.gov.hmrc.play.asyncmvc.example.controllers.AsyncMvcIntegration
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http
import uk.gov.hmrc.http.HeaderCarrier


class AsyncTaskSpec extends UnitSpec with ScalaFutures with FakePlayApplication with Eventually {
  final val oneSecond = 1000

  trait Setup {
    implicit val headerCarrier = http.HeaderCarrier()
    val id = "asyncTaskId"

    class CacheStore extends Cache[TaskCache] {
      var store:Option[TaskCache] = None

      override def put(id: String, value: TaskCache)(implicit hc: HeaderCarrier): Future[Unit] = {
        store = Some(value)
        Future.successful(Unit)
      }

      override def get(id: String)(implicit hc: HeaderCarrier): Future[Option[TaskCache]] = {
        Future.successful(store)
      }
    }

    class Example(testId:String) extends Controller with AsyncMvcIntegration {
      val cache = new CacheStore

      override val actorName = testId
      override def taskCache: Cache[TaskCache] = cache

      import AsyncMVCAsyncActor.AsyncMessage

      def sendMessage(futureFunction: HeaderCarrier => Future[Stock], time:Long) = {
        asyncTask.actorRef ! AsyncMessage(id, futureFunction, outputToString, Some(headerCarrier), time)
      }
    }

    val actorName:String
    lazy val asyncTask = new Example(actorName)
    val stock = Stock("id", 2.2)


    def test(futureFunction: HeaderCarrier => Future[Stock], time:Long = DateTimeUtils.now.getMillis)(test: => Unit) = {
      Throttle.up()
      asyncTask.sendMessage(futureFunction, time)
      await(test)
    }
  }

  class Complete extends Setup {
    override val actorName = "complete"
  }

  class Error extends Setup {
    override val actorName = "error"
  }

  class ErrorException extends Setup {
    override val actorName = "error_exception"
  }

  class Timeout extends Setup {
    override val actorName = "timeout"
  }

  "AsyncTask" should {

    "return Complete state when the off-line future completes successfully" in new Complete {
      // Note: Future has 1 second delay time, reason for 2 second delay in eventually.
      def success(hc: HeaderCarrier) = Future.successful(TimedEvent.delayedSuccess(oneSecond, 0).map(_ => stock))

      test(success) {
        eventually(Timeout(Span(oneSecond * 2, Millis))) {
          val result: Option[TaskCache] = asyncTask.taskCache.get("someId").futureValue

          result shouldBe Some(TaskCache("Example", StatusCodes.Complete, Some(Json.toJson(stock).toString()), result.get.start, result.get.complete))
          result.get.complete should be > asyncTask.taskCache.get("id").get.start
          Throttle.current shouldBe 0
        }
      }
    }

    "return Error state when the off-line future returns Future.failed" in new Error {
      def error(hc:HeaderCarrier) = Future.successful(Future.failed(new IllegalArgumentException("controlled explosion!")))

      test(error) {
        eventually(Timeout(Span(oneSecond, Millis))) {
          val result: Option[TaskCache] = asyncTask.taskCache.get("someId").futureValue

          result shouldBe Some(TaskCache("Example", StatusCodes.Error, None, result.get.start, result.get.complete))
          result.get.complete should be > asyncTask.taskCache.get("id").get.start
          Throttle.current shouldBe 0
        }
      }
    }

    "not process off-line future when the client has already timed out" in new Timeout {
      def notInvoked(hc: HeaderCarrier) = Future.successful(Future.failed(new IllegalArgumentException("Controlled Explosion!")))

      test(notInvoked, 0) {
        eventually(Timeout(Span(oneSecond, Millis))) {
          val result: Option[TaskCache] = asyncTask.taskCache.get("someId").futureValue
          result shouldBe None
          Throttle.current shouldBe 0
        }
      }
    }

    "return Error state when the off-line future throws an exception" in new ErrorException {
      def error(hc:HeaderCarrier) = throw new scala.IllegalArgumentException("Controlled Explosion!")

      test(error) {
        eventually(Timeout(Span(oneSecond, Millis))) {
          val result: Option[TaskCache] = asyncTask.taskCache.get("someId").futureValue

          result shouldBe Some(TaskCache("Example", StatusCodes.Error, None, result.get.start, result.get.complete))
          // >= since no delay incurred in processing the future!
          result.get.complete should be >= asyncTask.taskCache.get("id").get.start
          Throttle.current shouldBe 0
        }
      }
    }
  }

}
