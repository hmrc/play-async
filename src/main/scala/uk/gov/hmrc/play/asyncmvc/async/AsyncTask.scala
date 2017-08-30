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

import akka.actor.Actor
import uk.gov.hmrc.play.asyncmvc.model.{TaskCache, StatusCodes}
import play.api.Logger
import uk.gov.hmrc.time.DateTimeUtils
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

/**
 * The AsyncTask is responsible for processing an AsyncMVCAsyncActor message, where is message encapsulates a Future to be processed.
 * @tparam OUTPUT - The Type the Task will return.
 */
trait AsyncTask[OUTPUT] extends LogWrapper {

  /**                               
   * Object encapsulates the message handling and the definition of the Akka message.
   */
  object AsyncMVCAsyncActor {

    case class AsyncMessage(id: String, asyncFunction: HeaderCarrier => Future[OUTPUT], jsonToString: OUTPUT => String,
                       headerCarrier: Option[HeaderCarrier], startTime: Long) {

      def invokeAsyncFunction(startTime:Long)(implicit hc: HeaderCarrier, sessionCache: Cache[TaskCache]): Future[Unit] = {

        asyncFunction(hc).flatMap(jsonResult => {
          // Attempt to save the JSON result from the Future to cache. Indicates the background Future has completed processing.
          val task = TaskCache(id, StatusCodes.Complete, Some(jsonToString(jsonResult)), startTime, DateTimeUtils.now.getMillis)
          sessionCache.put(id, task).map { dataMap =>
            Logger.info(wrap(s"The async task Id [${this.id}] is complete and cache updated."))
          }.recover {
            case e: Exception =>
              Logger.error(wrap(s"Failed to update cache for task Id [${this.id}].")) // The client will timeout waiting!
          }
        })
      }
    }
  }

  /**
   * Defines an Actor which is responsible for processing the AsyncMVCAsyncActor.AsyncMessage message.
   * @param sessionCache - The cache used to update the status of the task associated with the message.
   * @param clientTimeout - The maximum amount of time a client will wait for the message to be processed.
   */
  class AsyncMVCAsyncActor(sessionCache:Cache[TaskCache], clientTimeout:Long) extends Actor {

    import AsyncMVCAsyncActor.AsyncMessage

    def receive = {

      case asyncTask @ AsyncMessage(id, _, _, hc, _) => processMessage(asyncTask)(hc.getOrElse(throw new Exception("No HeaderCarrier found in message!")), sessionCache)

      case unknown @ _  => Logger.info(wrap(s"Unknown message received! $unknown"))
    }

    private def processMessage(asyncTask:AsyncMessage)(implicit headerCarrier: HeaderCarrier, sessionCache: Cache[TaskCache]): Unit = {
      Logger.info(wrap(s"Picked up a new async task with Id [${asyncTask.id}]"))

      val timeout = DateTimeUtils.now.getMillis > (asyncTask.startTime + clientTimeout)
      // Check if the client has timed out already waiting for the task, no point starting if no client!
      val status = if (timeout) StatusCodes.Timeout else StatusCodes.Running
      val task = TaskCache(asyncTask.id, status, None, asyncTask.startTime, if (timeout) DateTimeUtils.now.getMillis else 0)
      invokeAsyncTaskFuture(asyncTask, task)
    }

    private def invokeAsyncTaskFuture(asyncMessage:AsyncMessage, task:TaskCache)(implicit hc:HeaderCarrier, sessionCache: Cache[TaskCache]) : Unit = {
      Logger.info(wrap(s"Invoking Future for Id [${asyncMessage.id}]"))

      val time = DateTimeUtils.now.getMillis
      decreaseThrottle(asyncMessage, task) {
        asyncMessage.invokeAsyncFunction(time)
      }
    }

    private def saveError(asyncTask:TaskCache, e:Exception)(implicit hc:HeaderCarrier) = {
      Logger.error(wrap(s"Task failed to process and error status recorded for Id [${asyncTask.id}]. Error [$e]"))
      // Note: Do not wait for future!
      sessionCache.put(asyncTask.id, asyncTask.copy(status=StatusCodes.Error, complete=DateTimeUtils.now.getMillis)).recover {
        case e: Exception =>
          Logger.error(wrap(s"saveError: Failed to save the task error status to cache! Task Id [${asyncTask.id}]. Exception $e"))
          throw new Exception("Failed to save to cache!")
      }
    }

    private def decreaseThrottle(asyncMessage:AsyncMessage, task:TaskCache)(action: => Future[Unit])(implicit hc:HeaderCarrier, sessionCache: Cache[TaskCache]) {
      val time = DateTimeUtils.now.getMillis

      def decreaseThrottle = Throttle.down()

      try {
        if (task.status != StatusCodes.Timeout) {
          action.map(_ => {
            decreaseThrottle
            Logger.info(wrap(s"Future completed processing. Time spent processing future for Id [${asyncMessage.id}] is ${DateTimeUtils.now.getMillis - time}"))
          }).recover {
            case e: Exception =>
              decreaseThrottle
              saveError(task, e)

            case _ => // Leave for akka to handle.
          }
        } else {
          decreaseThrottle
          Logger.error(wrap(s"Client has timed out waiting to start task! Id [${asyncMessage.id}]"))
        }
      } catch {
        // Note: Function could throw an exception before Future is successfully running!
        case e: Exception =>
          decreaseThrottle
          saveError(task, e)
      }
    }
  }
}

trait LogWrapper {
  def wrap(message:String) = s"play-async - $message"
}
