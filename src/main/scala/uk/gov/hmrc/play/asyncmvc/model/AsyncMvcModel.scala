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

package uk.gov.hmrc.play.asyncmvc.model

import play.api.libs.json.Json

object StatusCodes {
  val  Running=1
  val Complete=2
  val    Error=3
  val  Timeout=4
  val Throttle=5
}

object ViewCodes {
  val         Timeout=1
  val           Error=2
  val         Polling=3
  val ThrottleReached=4
}

object AsyncActionWrapperCodes {
  val NoASyncTaskFound=1
}

case class AsyncMvcSession(asyncId:String, uniqueId:String, start:Long) {
  def id = s"$asyncId-$uniqueId"
}
object AsyncMvcSession {
  implicit val format = Json.format[AsyncMvcSession]
}

case class TaskCache(id:String, status:Int, jsonResponse:Option[String], start:Long, complete:Long)

object TaskCache {
  implicit val format = Json.format[TaskCache]
}
