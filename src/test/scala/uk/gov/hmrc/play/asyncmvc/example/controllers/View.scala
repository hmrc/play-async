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

trait View {

  val capture = """
                  |<html>
                  |<body>
                  |<p>Please enter a value for both the message and the Id. The value in the Id field will be passed to the off-line future and returned in the Result page. This is used to
                  |verify the page submitted is associated correctly with the page response.</p>
                  |
                  |<form action="/submit" method="post">
                  |    Message:<br>
                  |    <input type="text" name="message" value="Message">
                  |    <br>
                  |    ID:<br>
                  |    <input type="text" name="id" value="1234">
                  |    <br><br>
                  |    <input type="submit" value="Submit">
                  |</form>
                  |
                  |</body>
                  |</html>""".stripMargin

  def complete(value:String) = s"""
                   |<html>
                   |<head>
                   |    <title>Complete</title>
                   |</head>
                   |<body>
                   |<p>The task has completed successfully. Id of the form value originally submitted is $value </p>
                   |</body>
                   |</html>""".stripMargin

  val error = """<!DOCTYPE html>
                |<html>
                |<head>
                |    <title>Error</title>
                |</head>
                |<body>
                |<p>An error occurred processing the request!</p>
                |</body>
                |</html>""".stripMargin

  val polling = """<!DOCTYPE html>
                  |<html>
                  |<head>
                  |    <title>Polling</title>
                  |    <meta http-equiv="refresh" content="5;URL='/poll'" />
                  |</head>
                  |<body>
                  |<p>Polling...please wait for task to complete.</p>
                  |</body>
                  |</html>""".stripMargin

  val timeout = """<!DOCTYPE html>
                  |<html>
                  |<head>
                  |    <title>Timeout</title>
                  |</head>
                  |<body>
                  |<p>The request has timed out!</p>
                  |</body>
                  |</html>""".stripMargin

  val throttle = """<!DOCTYPE html>
                  |<html>
                  |<head>
                  |    <title>Throttle</title>
                  |</head>
                  |<body>
                  |<p>The throttle limit has been reached and the request cannot be processed!</p>
                  |</body>
                  |</html>""".stripMargin





}
