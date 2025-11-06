/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.perftests.disareturns

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.slf4j.LoggerFactory
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import uk.gov.hmrc.perftests.disareturns.AuthRequests.getSubmissionBearerToken
import uk.gov.hmrc.perftests.disareturns.ReportingWindowRequests.setReportingWindowsOpen
import uk.gov.hmrc.perftests.disareturns.ThirdPartyApplicationRequests.{createClientApplication, createNotificationBox, createSubscriptionFields, deleteClientApplication}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

trait BaseRequests {
  val clientIds                     = ListBuffer[String]()
  val applicationIds                = ListBuffer[String]()
  private val logger                = LoggerFactory.getLogger("SetupLogger")
  var bearerToken: String           = _
  implicit val system: ActorSystem  = ActorSystem("setup-system")
  implicit val mat: Materializer    = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher
  val wsClient                      = StandaloneAhcWSClient()
  val noOfThirdPartyApplications    = 10

  def testDataSetup(): Unit = try {
    val extractedToken = getSubmissionBearerToken(wsClient)
    bearerToken = extractedToken
    setReportingWindowsOpen(wsClient)
    for (_ <- 1 to noOfThirdPartyApplications) {
      val futureApp: Future[ClientApplication] = createClientApplication(wsClient, extractedToken)
      val app: ClientApplication               = Await.result(futureApp, 10.seconds)
      Await.result(createNotificationBox(wsClient, app.clientId), 5.seconds)
      clientIds += app.clientId
      applicationIds += app.applicationId
    }

    /** * Logging the application Ids in case the application exit in the middle of an execution.So later we can do the
      * third party application clean up. This is TBD after the PR review.
      */
    logger.info(s"Application IDs created: ${applicationIds.mkString(", ")}")
    Await.result(createSubscriptionFields(wsClient), 5.seconds)
  }

  def testDataCleanUp(): Unit = try
    for (i <- applicationIds.indices)
      Await.result(deleteClientApplication(wsClient, bearerToken, applicationIds(i)), 5.seconds)
  finally {
    wsClient.close()
    system.terminate()
  }
}
case class ClientApplication(clientId: String, applicationId: String)
