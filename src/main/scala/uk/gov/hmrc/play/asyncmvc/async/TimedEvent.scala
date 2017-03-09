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

import java.util.Timer
import java.util.TimerTask
import scala.concurrent._

object TimedEvent {
  val timer = new Timer

  // Return a Future which completes with the supplied value after 'n' milliseconds.
  def delayedSuccess[T](millis: Long, value: T): Future[T] = {
    val result = Promise[T]()
    timer.schedule(new TimerTask() {
      def run() = {
        result.success(value)
      }
    }, millis)
    result.future
  }
}
