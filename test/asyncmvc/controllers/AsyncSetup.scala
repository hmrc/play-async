/*
 * Copyright 2016 HM Revenue & Customs
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

package asyncmvc.controllers

import akka.actor.{ActorRef, Props}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.asyncmvc.async.{Cache, TimedEvent}
import uk.gov.hmrc.play.asyncmvc.example.connectors.{Stock, StockConnector}
import uk.gov.hmrc.play.asyncmvc.example.controllers.{ExampleAsyncController, InputForm}
import uk.gov.hmrc.play.asyncmvc.model.TaskCache
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.test.FakeRequest
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AsyncSetup {

  trait Setup {

    val testSessionId="TestId"

    lazy val ident = s"${ExampleAsyncController.id}-${testSessionId}"

    implicit val hc = HeaderCarrier()
    implicit val req = FakeRequest()

    val successStockConnector = new StockConnector {
      lazy val baseUrl: String = ???
      override def getStock(id: Long): Future[Stock] = {
        Future.successful(Stock("SUCCESS", id))
      }
    }

    def dynamicDelayStockConnector(rangeNum:Int, dynamic:Boolean = false) = new StockConnector {
      lazy val baseUrl: String = ???

      val delay = rangeNum
        if (dynamic) {
        scala.util.Random.nextInt(rangeNum)
      } else {
        rangeNum
      }

      override def getStock(id: Long): Future[Stock] = {
        TimedEvent.delayedSuccess(delay, 0).map(a => {
          Stock("TEST DYNAMIC DELAY", id)
        })
      }
    }

    val errorStockConnector = new StockConnector {
      lazy val baseUrl: String = ???
      override def getStock(id: Long) = Future.failed(throw new Exception("FAILURE!"))
    }

    trait ControllerUnderTest extends ExampleAsyncController {
      def getName :String = ???
      override lazy val sessionHttpCache = cache
      override def waitMode = false
      override lazy val asyncActor: ActorRef = Akka.system.actorOf(Props(new AsyncMVCAsyncActor(sessionHttpCache, CLIENT_TIMEOUT)), name = getName)
    }

    val cache = new Cache[TaskCache] {
      var bodyCache:Option[TaskCache] = None

      def put(id:String, value:TaskCache)(implicit hc:HeaderCarrier):Future[Unit] = {
        bodyCache = Some(value)
        Future.successful(Unit)
      }
      
      def get(id:String)(implicit hc:HeaderCarrier):Future[Option[TaskCache]] = {
        Future.successful(bodyCache)
      }
    }

    lazy val controller : ExampleAsyncController = ???
  }

  trait Blocking extends Setup {

    override val testSessionId="TestIdBlock"
    val form = uk.gov.hmrc.play.asyncmvc.example.controllers.InputForm("Example Data", 11)

    override lazy val controller : ExampleAsyncController = new ControllerUnderTest {
      override def getName = testSessionId
      override def buildUniqueId() = testSessionId
      override lazy val stockConnector: StockConnector = successStockConnector
      override def waitMode = true
    }
  }

  trait SetupNonBlocking extends Setup {
    override val testSessionId="TestIdNonBlocking"
    val form = InputForm("Example Data", 11)

    override lazy val controller : ExampleAsyncController = new ControllerUnderTest {
      override def getName = testSessionId
      override lazy val stockConnector: StockConnector = successStockConnector
      override def buildUniqueId() = testSessionId
    }
  }

  trait SetupNonBlockingSafeGuard extends SetupNonBlocking {
    override val testSessionId="SafeGuard"
    override val form = InputForm("Example Data", 12)

  }

  trait SetupNonBlockingTimeout extends Setup {
    val form = InputForm("Example Data", 22)
    override val testSessionId="TestIdNonBlockingTimeout"

    override lazy val controller  : ExampleAsyncController = new ControllerUnderTest {
      override def getName = testSessionId
      override lazy val stockConnector: StockConnector = dynamicDelayStockConnector(1000)
      override def buildUniqueId() = testSessionId
      override def getClientTimeout = 1
      override lazy val asyncActor: ActorRef = Akka.system.actorOf(Props(new AsyncMVCAsyncActor(sessionHttpCache, 1000)), name = getName)
    }
  }

  trait SetupBlockingTimeout extends Setup {
    val form = InputForm("Example Data", 16)
    override val testSessionId="TestIdBlockingTimeout"

    override lazy val controller  : ExampleAsyncController = new ControllerUnderTest {
      override def getName = testSessionId
      override lazy val stockConnector: StockConnector = dynamicDelayStockConnector(1000)
      override def buildUniqueId() = testSessionId
      override def waitMode = true
      override def getClientTimeout = 1
      override def blockingDelayTime = 1
      override lazy val asyncActor: ActorRef = Akka.system.actorOf(Props(new AsyncMVCAsyncActor(sessionHttpCache, 100)), name = getName)
    }
  }

  trait SetupNonBlockingError extends Setup {
    val form = InputForm("Example Data", 33)
    override val testSessionId="TestIdSetupNonBlockingError"

    override lazy val controller  : ExampleAsyncController = new ControllerUnderTest {
      override def getName = testSessionId
      override lazy val stockConnector: StockConnector = errorStockConnector
      override def buildUniqueId() = testSessionId
    }
  }

  trait SetupBlockingError extends SetupNonBlockingError {
    override val testSessionId="TestIdBlockingError"

    override lazy val controller  : ExampleAsyncController = new ControllerUnderTest {
      override def getName = testSessionId
      override lazy val stockConnector = errorStockConnector
      override def buildUniqueId() = testSessionId
      override def waitMode = true
    }
  }

  trait SetupBlockingThrottle extends Setup {
    override val testSessionId="TestIdBlockingThrottle"
    val form = InputForm("Example Data", 44)

    override lazy val controller : ExampleAsyncController = new ExampleAsyncController() {
      override lazy val stockConnector : StockConnector = ???
      override lazy val sessionHttpCache  = ???
      override def buildUniqueId() = testSessionId
      override def waitMode = true
      override def throttleLimit = 0
      override lazy val asyncActor: ActorRef = ???
    }
  }

  trait SetupNonBlockingThrottle extends SetupBlockingThrottle {
    override val testSessionId="TestIdNonBlockingThrottle"

    override lazy val controller : ExampleAsyncController = new ExampleAsyncController() {
      override lazy val stockConnector : StockConnector = ???
      override lazy val sessionHttpCache  = ???
      override def buildUniqueId() = testSessionId
      override def waitMode = false
      override def throttleLimit = 0
      override lazy val asyncActor: ActorRef = ???
    }
  }

  trait SetupBlocking extends Setup {
    override val testSessionId="TestIdBlocking"
    val form = InputForm("Example Data", 55)

    override lazy val controller  : ExampleAsyncController = new ControllerUnderTest {
      override def getName = testSessionId
      override lazy val stockConnector: StockConnector = dynamicDelayStockConnector(1000)
      override def buildUniqueId() = testSessionId
      override def waitMode = true
      override def getClientTimeout = 5000
      override lazy val asyncActor: ActorRef = Akka.system.actorOf(Props(new AsyncMVCAsyncActor(sessionHttpCache, getClientTimeout)), name = getName)
    }
  }

  trait SetupConcurrencyDynamicBlocking extends Setup {
    val form = InputForm("Example Data", 1)

    override lazy val controller  : ExampleAsyncController = new ControllerUnderTest {
      override def getName = testSessionId
      override lazy val stockConnector: StockConnector = successStockConnector //dynamicDelayStockConnector(1000, true)
      override def buildUniqueId() = testSessionId
      override def waitMode = true
      override def getClientTimeout = 80000
      override lazy val asyncActor: ActorRef = Akka.system.actorOf(Props(new AsyncMVCAsyncActor(sessionHttpCache, getClientTimeout)), name = getName)
    }
  }

}
