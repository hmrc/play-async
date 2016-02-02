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

import uk.gov.hmrc.play.asyncmvc.example.connectors.StockConnector
import play.api.mvc._
import uk.gov.hmrc.play.http.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global

// Example of a normal controller, the content is ONLY returned once the Future completes. This means if the future could take a long time to
// complete, the user could re-submit the request.
trait ExampleNormalController extends Controller {
  lazy val stockConnector: StockConnector = ???
  def normalAction = Action.async {
    implicit request =>
      implicit val hc=HeaderCarrier.fromHeadersAndSession(request.headers)
      stockConnector.getStock(1L).map { response =>
        Ok(uk.gov.hmrc.play.asyncmvc.example.views.html.complete(response))
      }
  }
}

object ExampleNormalController extends ExampleNormalController {
  override lazy val stockConnector = StockConnector
}
