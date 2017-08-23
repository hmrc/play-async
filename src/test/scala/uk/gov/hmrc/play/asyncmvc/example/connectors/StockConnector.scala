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

package uk.gov.hmrc.play.asyncmvc.example.connectors

import play.api.libs.json.Json
import scala.concurrent.Future

case class Stock(name:String, value:Double)
object Stock {
  implicit val format = Json.format[Stock]
}

trait StockConnector {

  def baseUrl: String
  def url(path: String) = s"$baseUrl$path"

  def getStock(id: Long): Future[Stock] = Future.successful(Stock("Some Stock", id))

}
