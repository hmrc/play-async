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

package uk.gov.hmrc.play.asyncmvc.example.controllers

import uk.gov.hmrc.play.asyncmvc.example.connectors.Stock
import play.api.libs.json.Json
import play.api.mvc.Action
import uk.gov.hmrc.play.asyncmvc.async.TimedEvent
import scala.concurrent.ExecutionContext.Implicits.global

object StockController extends scala.AnyRef with play.api.mvc.Controller {

  final val four_seconds = 4000

  def getStock(id:Long) = Action.async {
    implicit request =>

      val stock = Stock(s"Stock Item $id", id)
      // Delay before returning response.
      TimedEvent.delayedSuccess(four_seconds, 0).map(a => {
        Ok(Json.toJson(stock))
      })
  }

}
