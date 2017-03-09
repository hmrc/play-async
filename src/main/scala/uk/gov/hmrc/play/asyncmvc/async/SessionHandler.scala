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

import java.util.UUID
import uk.gov.hmrc.play.asyncmvc.model.AsyncMvcSession
import play.api.libs.json.Json
import play.api.mvc.{Session, AnyContent, Request}

import uk.gov.hmrc.time.DateTimeUtils

trait SessionHandler {

  Self:AsyncValidation =>

  final val AsyncMVCSessionId="ASYNC_MVC_ID"

  def getClientTimeout: Long // Return the client timeout in milliseconds. The client can only wait so long for the request before either result or timeout page is displayed.

  def clearASyncIdFromSession(data: Map[String, String]): Map[String, String] = data - AsyncMVCSessionId

  def buildUniqueId() = UUID.randomUUID().toString

  def buildSession(id:String,uniqueId:String) : String = {
    implicit val format = Json.format[AsyncMvcSession]
    val asyncMvcSession=AsyncMvcSession(id,uniqueId,DateTimeUtils.now.getMillis+getClientTimeout)
    Json.stringify(Json.toJson(asyncMvcSession))
  }

  def buildSessionWithMVCSessionId(id:String, uniqueId:String, data:Map[String,String])(implicit request:Request[AnyContent]): Map[String, String] = {
    data - AsyncMVCSessionId + (AsyncMVCSessionId -> buildSession(id, uniqueId))
  }

  def getSessionObject()(implicit request:Request[AnyContent]): Option[AsyncMvcSession] = {
    request.session.data.get(AsyncMVCSessionId) match {
      case Some(e) =>
        implicit val format = Json.format[AsyncMvcSession]
        Json.parse(e).asOpt[AsyncMvcSession]

      case _ => None
    }
  }

}
