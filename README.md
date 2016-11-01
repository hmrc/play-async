
# play-async

[![Build Status](https://travis-ci.org/hmrc/play-async.svg?branch=master)](https://travis-ci.org/hmrc/play-async) [ ![Download](https://api.bintray.com/packages/hmrc/releases/play-async/images/download.svg) ](https://bintray.com/hmrc/releases/play-async/_latestVersion)


Framework disconnects the HTTP client (Browser/RestFul API) request from waiting for long running server-side actions to complete and shields the application server from clients attempting to re-submit duplicate requests.

The play-async framework provides the tools to transform a synchronous controller action into a true async controller, where the client drives the polling for the requested resource back to the server.

##Background

Browser or API requests to web applications will block waiting for the request to be processed by the web application before HTML content is returned. If the web application processing the request invokes back-end systems, the maximum response time of the request is dependent on the configured socket read timeout.

If a server side action invokes only 2 backend HTTP services in succession during a single user request, and the socket timeouts on these services are configured to 30 seconds, the maximum response time for this page request could be 59 seconds due to the large read timeout value.

When systems start to degrade, response times to clients increase. The normal reaction for a user where the browser is taking a long time to load the page, would be to either refresh the page or press the browser back button and re-attempt to submit the request.
Since the client browser makes a blocking HTTP request to the server side action to service a single page request, termination of the HTTP request will leave the original request running on the application server, which results in wasted transaction/resources being consumed for wasted requests, which in turn can put unnecessary load on back-end systems,
and the application server from processing too many requests.

The play-async library shields the application server from the issues detailed above. Instead of the usual approach of the client blocking and waiting for the server response, the client simply polls for the response to the requested resource from the server.

##Features

The play_async_mvc library addresses the above concerns with the following features…

* Allow long running Future to be placed onto a background queue for off-line processing and disconnect the HTTP client from waiting on the result. This removes the need for the client to remain connected to the application server waiting for the result.
* Throttle the number of concurrent async requests that are currently executing on a single application server instance.
* Shield the application servers from processing unnecessary duplicate user requests. i.e. frustrated users refreshing or re-submitting duplicate requests.
* Tools to control the web journey when async tasks are executing.
* Allow back-end service read socket timeouts to be increased! Normally read timeouts are defined too short in order to reduce the amount of time the client waits for the response!
* Present auto-refreshing presentation content informing the user the request is being processed.
* Library supports two modes for running a Future on a background queue. The blocking mode will block the client request a number of seconds when the background Future is initially created, in order to remove the need for the polling page to be presented to the user when systems response times are in SLA. The non-blocking mode will always return the polling page.
* Framework can be used on both front-end HTTP content requests and back-end ReSTFul services.
* Library is lightweight Trait where the client integrates by extending the Play Controller action with the AsyncMVC Trait.
* Remove the need for long running socket connections through web-servers and firewalls.
* Cluster friendly.

##Under the hood

The play-async Controller defined within an application will use an Akka Actor to process the off-line request. The clients session is used to store a unique Id which is associated with an off-line task. The status of the off-line task is stored within a cache, which is supplied through the integration of the framework. The cache is used as the central store, where async polling requests to application servers (in a cluster) will invoke to check the status of the task, and obtain the response of the processed Future.
The HeaderCarrier associated with the original request will be carried through to the off-line request.

##Code Example

This section demonstrates the conversion from a normal blocking Play Controller action to a play-async action.

The below example demonstrates the function stockConnector being mapped over for a result, and the result is bound to a HTML result.

```
trait ExampleNormalController extends Controller {
  lazy val stockConnector: StockConnector = ???
  def normalAction = Action.async {
    implicit request =>
      implicit val hc=HeaderCarrier.fromHeadersAndSession(request.headers)
      stockConnector.getStock(1L).map { response =>
        Ok(views.html.complete(response))
      }
  }
}

object ExampleNormalController extends ExampleNormalController {
  override lazy val stockConnector = StockConnector
}
```

The play-async controller is defined below. Please note the function asyncWrapper and the two new methods called asyncUICallback and asyncUICallbackWithStatus. The asyncWrapper will place the supplied Future body onto a background Akka message queue for processing, and the supplied callback functions are invoked from the framework. On initial invocation of asyncWrapper, the off-line Future is sent to a background queue and asyncUICallbackWithStatus is invoked passing the status as ViewCodes.Polling. The HTML poll content returned contains a meta-refresh command to auto submit back to the server to poll the status of the background task.  The pollTask action is called to check the status of the running task, and once complete the callback asyncUICallback is invoked passing the supplied response from the background task, otherwise asyncUICallbackWithStatus is invoked and supplied a status.

```
trait ExampleNormalController extends Controller with AsyncMvcIntegration {
  lazy val stockConnector: StockConnector = ???
  def asyncAction = Action.async { implicit request =>
    implicit val hc=HeaderCarrier.fromHeadersAndSession(request.headers)
    asyncWrapper(asyncUICallback, asyncUICallbackWithStatus) {
      hc =>
        stockConnector.getStock(1L)
    }
  }

  def asyncUICallback(stock:Stock)(implicit request:Request[AnyContent]) : Future[Result] = {
    val json=stock.asInstanceOf[Stock]
    Future.successful(Ok(views.html.complete(json)))
  }

  def asyncUICallbackWithStatus(status:Int)(implicit request:Request[AnyContent]) : Future[Result] = {
    val res = status match {
      case ViewCodes.Timeout => Ok(views.html.timeout.apply())
      case ViewCodes.Polling => Ok(views.html.polling.apply())
      case ViewCodes.ThrottleReached => Ok(views.html.throttle_limit_reached.apply())
      case ViewCodes.Error | _ => Ok(views.html.error.apply())
    }
    Future.successful(res)
  }

  def poll = Action.async {
    implicit request =>
      pollTask(Call("GET","/capture"), updateUICallback, updateUICallbackWithStatus)
  }
}

object ExampleNormalController extends ExampleNormalController {
  override lazy val stockConnector = StockConnector
}
```

The supported status codes which can be supplied to asyncUICallbackWithStatus are detailed in the table below. Integration of the framework could require 4 HTML files to be defined, which are associated for processing the status codes. The supported status code are...

| Status Code  | Reason  |
|---|---|
|Poll   | Task has not completed, return to poll page.   |
|Timeout | The task has timed out.   |
|Error   | The task generated an error.  |
|Throttle   | The throttle marker has been reached. Too many concurrent requests.  |

##Example PlayAsync controller

uk.gov.hmrc.play.asyncmvc.example.controllers.ExampleAsyncController	- Example async controller where the client is disconnected from the Future. The example is based on the ExampleNormalController controller where the Future and Result have been separated.


## Installing

Include the following dependency in your SBT build

``` scala
resolvers += Resolver.bintrayRepo("hmrc", "releases")

libraryDependencies += "uk.gov.hmrc" %% "play-async" % "[INSERT-VERSION]"
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
