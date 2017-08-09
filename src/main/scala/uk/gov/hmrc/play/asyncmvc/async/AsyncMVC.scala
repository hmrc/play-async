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

import akka.actor.ActorRef
import uk.gov.hmrc.play.asyncmvc.model._
import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.time.DateTimeUtils
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

/**
 * The AsyncMVC trait supports two features detailed below...
 *
 * 1) Action wrapper (asyncWrapper) to define the code-block to be processed off-line in a background queue. The client is responsible for polling the framework
 * to understand when the task completes.
 *
 * 2) Async action wrapper (asyncActionWrapper). The wrapper is used to stop the user from jumping to different pages (within a specific web journey) while an async
 * task is being processed for the user. If a task is currently running, the user will be returned to the polling page.
 *
 * @tparam OUTPUT - The type of the response from the Future being off-lined.
 */
trait AsyncMVC[OUTPUT] extends AsyncTask[OUTPUT] with SessionHandler with AsyncValidation with LogWrapper {

  Self:Controller =>

  def actorRef           : ActorRef                // Actor which is responsible for processing the Future function off-line.
  def waitForAsync       : Call                    // Wait for async route. Used when waitMode is true.
  def throttleLimit      : Long                    // Throttle limit.
  def blockingDelayTime  : Long                    // Wait mode - Amount of time to block waiting for a background task to complete, before either returning the result or the poll page.
  def taskCache          : Cache[TaskCache]        // Cache used to store state of the async task.
  def outputToString(value:OUTPUT) : String        // Convert [OUTPUT] type to string.
  def convertToJSONType(value:String) : OUTPUT     // Convert String to [OUTPUT] type.

  def waitMode = false  // Controls the nature of how the client is blocked. If the value is false, the library will respond to the client with the poll
                        // page once the Future is sent off-line. If the value is true, the user will block for a configurable period of time and then
                        // check if the task has completed. This mode is used to not display the poll page if the Future completes within the configurable time.

  type callbackSuccessType = OUTPUT => String => Future[Result]
  type callbackWithStatusType = Int => Option[String] => Future[Result]

  // Function wrapper for request actions which need to be aware of an executing async task. Used to route the user back to the poll view if an async task is currently executing.
  // NOTE: The action wrapper is lightweight and only session is check for the existence of a running task!
  class AsyncActionWrapper {

    def async(callbackWithStatus:callbackWithStatusType)(view:Int=>Future[Result])(implicit request:Request[AnyContent]): Future[Result] = {
      withCorrespondingAsyncIdMatch {
        case Some(task) => callbackWithStatus(ViewCodes.Polling)(Some(task.id))

        case _ => view(AsyncActionWrapperCodes.NoASyncTaskFound)
      }
    }
  }

  def asyncActionWrapper = new AsyncActionWrapper

  /**
   * Function used to safe-guard the calling of the correct associated async controller function. If the session async Id does not match the Id of the controller receiving the async request,
   * the caller will be re-directed to the associated poll URL linked to the async Id.
   * @param func - The function to invoke if the Id's match.
   * @param request - The request.
   * @return - Future[Result]
   */
  def withCorrespondingAsyncIdMatch(func: Option[AsyncMvcSession] => Future[Result])(implicit request:Request[AnyContent]): Future[Result] = {

    getSessionObject match {
      case Some(task) =>
        if (task.asyncId != id) {
          // If the async task Id does not match the controller receiving the async request, then redirect to the correct polling URL.
          Logger.warn(wrap(s"withCorrespondingAsyncIdMatch - Ids do not match. Task Id [${task.asyncId}] Controller Id [$id]"))
          Future.successful(Redirect(getAsyncPollPathFromId(task.asyncId).path))
        } else {
          func(Some(task))
        }

      case None => func(None)
    }
  }

  /**
   * Function used to verify if the async task has completed, if the async task has not completed the callbackStatus function will be invoked with a processing status value.
   *
   * @param route - Route if no async task is running.  User could have deep-linked to a specific async controller action.
   * @param callback  - Invoked when the off-line Future completes.
   * @param callbackStatus  - Invoked when the client is responsible for rendering the display based on a status. This could be timeout, error...
   * @return - Future[Result]
   */
  def pollTask(route:Call, callback:OUTPUT => String => Future[Result], callbackStatus:callbackWithStatusType)(implicit request:Request[AnyContent], hc:HeaderCarrier) = checkTaskStatus(route, callback, callbackStatus)

  /**
   * Function used in async blocking mode to not display the polling page when async task completes within expected wait period.
   *
   * The client is redirected (http) to this function once the task has been submitted to the queue, the request will block
   * for a short period of time before checking if the async task has completed. If the task has completed the callback function is invoked,
   * otherwise callbackStatus is invoked to generate the poll page.
   *
   * @param route - Route to if no async task is running.  User could have deep-linked to a specific async controller action.
   * @param callback  - Invoked when the off-line futureFunction completes.
   * @param callbackStatus  - Invoked when the client is responsible for rendering the display based on a status.
   * @return - Future[Result]
   *
   */
  def waitForAsyncTask(route:Call, callback:callbackSuccessType, callbackStatus:callbackWithStatusType)(implicit request:Request[AnyContent], hc:HeaderCarrier) = {

    withCorrespondingAsyncIdMatch {
      case Some(session) =>
        // In wait mode, wait a small duration of time (blocking the client) and check if the task has completed before returning either the success result or the poll page.
        TimedEvent.delayedSuccess(blockingDelayTime, 0).map(a =>
          checkTaskStatus(route, callback, callbackStatus)
        ).flatMap(response => response)
      case _ =>
        Logger.error(wrap(s"waitForAsyncTask invoked but no session exists! Routing to $route."))
        Future.successful(Redirect(route))
    }
  }

  /**
   * Wrap the Future for off-line processing.
   *
   * @param futureFunction - The Future function to invoke off-line.
   * @param callbackWithStatus  - Invoked when the client is responsible for rendering the display based on a status.
   * @return - Future[Result]
   *
   * Important Note: The callbacks defined by the caller could include implicit parameters. This allows contexts (like the AuthContext)
   * to be passed back to the caller transparently from this library!
   */
  def asyncWrapper(callbackWithStatus:callbackWithStatusType)
                  (futureFunction: HeaderCarrier => Future[OUTPUT])
                  (implicit headerCarrier: HeaderCarrier, request:Request[AnyContent]): Future[Result] = {

    import AsyncMVCAsyncActor.AsyncMessage

    asyncActionWrapper.async(callbackWithStatus) { notFound =>

      throttleUp(callbackWithStatus) {
        val uniqueId = buildUniqueId()
        val session: Session = request.session
        val ident = s"$id-$uniqueId"

        def createAsyncTask(): Future[Result] = {
          Logger.info(wrap(s"New task generated [$ident]."))
          val task = TaskCache(ident, StatusCodes.Running, None, DateTimeUtils.now.getMillis, 0)

          def sendMessage = {
            actorRef ! AsyncMessage(ident, futureFunction, outputToString, Some(headerCarrier), DateTimeUtils.now.getMillis)
            Future.successful(Unit)
          }

          for {
            _ <- taskCache.put(ident, task)
            _ <- sendMessage
            res <- callbackWithStatus(ViewCodes.Polling)(Some(ident))
          } yield { res.withSession(res.session.copy(data = buildSessionWithMVCSessionId(id, uniqueId, res.session.data))) }
        }

        val result: Future[Result] = createAsyncTask()

        if (waitMode) {
          // Note: The above Future result is ignored in blocking mode.
          // First, user must be redirected in order to force the storing of the Task-Id within the session/Cookie! This is required to stop replays while in wait mode.
          Future.successful(Redirect(waitForAsync).withSession(session.copy(data = buildSessionWithMVCSessionId(id, uniqueId, session.data)(request))))
        } else {
          // Return the poll response.
          result
        }
      }
    }
  }

  /**
   * Check the status of the task and route.
   * @param route - The route to return if no task exists in session.
   * @param callback  - The callback to invoked if the task has completed successfully.
   * @param callbackStatus - The callback to be invoked with a supplied status.
   * @param request - Current request.
   * @param hc - Header carrier.
   * @return - Future[Result]
   */
  private def checkTaskStatus(route:Call, callback:callbackSuccessType, callbackStatus:callbackWithStatusType)(implicit request:Request[AnyContent], hc:HeaderCarrier): Future[Result] = {

    def getTaskAndDecideRoute(id:String) = {
      taskCache.get(id).map {
          case Some(task: TaskCache) =>
            Logger.info(wrap(s"checkTaskStatus: Found task id [${task.id}] in cache. Checking route..."))
            decideUIAction(task, callback, callbackStatus)

          case _ =>
            // No task exists! User could have deep-linked to an async controller action.
            Logger.info(wrap(s"checkTaskStatus: Failed to resolve the task id! No task is running! Redirecting to route $route"))
            Right(Future.successful(Redirect(route)))

      }.recover {
        case e: Exception =>
          Logger.error(wrap(s"checkTaskStatus: Failed to invoke cache for task Id [$id]. Exception is [$e]."))
          Right(removeAsyncKeyFromSession(callbackStatus(ViewCodes.Error)(None)))
      }
    }

    withCorrespondingAsyncIdMatch {
      case Some(session) =>
        getTaskAndDecideRoute(session.id).flatMap {
          case Right(response) => removeAsyncKeyFromSession(response)

          case Left(response) => response
        }

      case None =>
        Logger.info(wrap(s"No session object found! Redirecting to $route"))
        // No task running. Redirect to the requested route.
        Future.successful(Redirect(route))
    }
  }

  /**
   * Decide the UI action based on the status of the task.
   * @param task - The task
   * @param callback - Success callback
   * @param callbackWithStatus - Callback with status.
   * @param request - Http Request.
   * @return - Future[Result]
   */
  private def decideUIAction(task:TaskCache, callback:callbackSuccessType, callbackWithStatus:callbackWithStatusType)(implicit request:Request[AnyContent]): Either[Future[Result], Future[Result]] = {
    task.status match {
      case StatusCodes.Complete =>                // SUCCESS - Task has completed, invoke callback to generate result.
        Logger.info(wrap(s"decideUIAction:The task [${task.id}] has completed successfully."))
        // Success - route to callback and clear async session model.
        Right(removeAsyncKeyFromSession(callback(convertToJSONType(task.jsonResponse.getOrElse(throw new Exception(s"No JSON response found for async task! Task ${task.id}"))))(task.id)))

      case StatusCodes.Error =>                   // ERROR - Route to the error page.
        Logger.info(wrap(s"decideUIAction:The task [${task.id}] failed!"))
        Right(removeAsyncKeyFromSession(callbackWithStatus(ViewCodes.Error)(Some(task.id))))

      case _ =>
        if (isTimeout(task.start)) {              // TIMEOUT - task timed out, route to timeout page.
          Logger.error(wrap(s"decideUIAction:Timed out waiting for the task Id [${task.id}] to complete."))
          Right(removeAsyncKeyFromSession(callbackWithStatus(ViewCodes.Timeout)(Some(task.id))))
        } else {                                  // STILL PROCESSING...
          Logger.info(wrap(s"decideUIAction:The task [${task.id}] is still processing!"))
          Left(callbackWithStatus(ViewCodes.Polling)(Some(task.id)))
        }
    }
  }

  /**
   * Remove the async key from the session once the result completes.
   * @param result - Future result to map over.
   * @param request - Current request.
   * @return - Future[Result]
   */
  private def removeAsyncKeyFromSession(result:Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {
    result.map(res => res.withSession(res.session.copy(data = clearASyncIdFromSession(res.session.data))))
  }

  private def throttleUp(callbackWithStatus:callbackWithStatusType)(func: => Future[Result])(implicit request:Request[AnyContent]): Future[Result] = {
    
    if (Throttle.current != -1 && Throttle.current >= throttleLimit)  {
      Logger.warn(wrap(s"The throttle limit has been reached! Current limit [$throttleLimit] Current [${Throttle.current}]"))
      removeAsyncKeyFromSession(callbackWithStatus(ViewCodes.ThrottleReached)(None))
    } else {
      Throttle.up()
      Logger.info(wrap(s"Current Throttle limit [$throttleLimit] - Current Throttle [${Throttle.current}]"))
      val outcome = func.map(resp => Some(resp)).recover {
        case ex: Exception =>
          Logger.error(wrap(s"Failed to offline the task and failure is $ex"))
          None
      }

      outcome.flatMap {
        case Some(response) => Future.successful(response)
        case _ =>
          // Failure saving the task to cache. Decrease throttle and return error status.
          Throttle.down()
          callbackWithStatus(ViewCodes.Error)(None)
      }
    }
  }

  private def isTimeout(start:Long) = {
    DateTimeUtils.now.getMillis > (start.toLong+getClientTimeout)
  }

}

/**
 * Cache interface to store the state of task outcome.
 * @tparam T - The type of the Cache.
 */
trait Cache[T] {
  def put(id:String, value:TaskCache)(implicit hc:HeaderCarrier) : Future[Unit]
  def get(id:String)(implicit hc:HeaderCarrier) : Future[Option[T]]
}

/**
 * Validate the async request against the associated controller action. Since each controller would be associated to an output type, only the controller
 * associated with the type should process the off-line response.
 *
 * When defining multiple async actions with different response (OUTPUT) types, a new controller should be defined for each response type.
 * Alternatively, a single generic controller could be defined for all actions, using a common (OUTPUT) type!
 */
trait AsyncValidation {
  def id : String                  // Unique Id for the async controller extending this trait. Each controller must have a unique Id.
  def asyncPaths(implicit request:Request[AnyContent]): Seq[AsyncPaths]         // Sequence of async paths associated with the application.

  def getAsyncPollPathFromId(id:String)(implicit request:Request[AnyContent]) = asyncPaths.find(async => async.id == id).getOrElse(throw new IllegalArgumentException(s"Unknown async Id [$id]"))
}
case class AsyncPaths(id:String, path:String)
