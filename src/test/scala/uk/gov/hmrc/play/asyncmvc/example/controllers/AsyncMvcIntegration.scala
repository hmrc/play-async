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

package uk.gov.hmrc.play.asyncmvc.example.controllers

import akka.actor.{ActorRef, Props}
import uk.gov.hmrc.play.asyncmvc.async.AsyncMVC
import uk.gov.hmrc.play.asyncmvc.example.connectors.Stock
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.json.Json
import play.api.mvc.{Call, AnyContent, Request, Controller}

trait AsyncMvcIntegration extends AsyncMVC[Stock] {

  self:Controller =>

  val actorName = "async_mvc_actor"
  override def id = "Example"
  override def asyncPaths(implicit request:Request[AnyContent]) = AsyncMap.applicationAsyncControllers

  // Convert the stock object to String.
  override def outputToString(in:Stock): String = {
    implicit val format = Json.format[Stock]
    Json.stringify(Json.toJson(in))
  }

  // Convert the String to stock object representation.
  override def convertToJSONType(in:String) : Stock = {
    val json=Json.parse(in)
    val stock=json.asOpt[Stock]
    stock.getOrElse(throw new Exception("Failed to resolve the object!"))
  }

  override def       waitForAsync = Call("GET","/wait")
  override def      throttleLimit = 300
  override def  blockingDelayTime = 3000

  final val CLIENT_TIMEOUT=8000L

  lazy val asyncActor: ActorRef = Akka.system.actorOf(Props(new AsyncMVCAsyncActor(taskCache, CLIENT_TIMEOUT)), actorName)
  override def         actorRef = asyncActor
  override def getClientTimeout = CLIENT_TIMEOUT

}
