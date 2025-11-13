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
import org.scalatest.Assertions.cancel
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

trait BaseRequests {
  implicit val system: ActorSystem  = ActorSystem("setup-system")
  implicit val mat: Materializer    = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher
  val wsClient                      = StandaloneAhcWSClient()
  val authRequests                  = new AuthRequests(wsClient)
  val thirdPartyApplicationRequests = new ThirdPartyApplicationRequests(wsClient)
  val reportingWindowRequests       = new ReportingWindowRequests(wsClient)
  val noOfThirdPartyApplications    = 1

  def testDataSetup(): TestDataSetupResult = {
    try {
      val extractedToken = Await.result(authRequests.getSubmissionBearerToken, 10.seconds)
      println(Console.GREEN + "1" + Console.RESET)
      Await.result(reportingWindowRequests.setReportingWindowsOpen(), 10.seconds)
      println(Console.GREEN + "2" + Console.RESET)
      val futureApps: Future[List[ClientApplication]] = Future.traverse((1 to noOfThirdPartyApplications).toList) { _ =>
        for {
          app <- thirdPartyApplicationRequests.createClientApplication(extractedToken)
          _   <- thirdPartyApplicationRequests.createNotificationBox(app.clientId)
        } yield app
      }
      println(Console.GREEN + "3" + Console.RESET)
      val appData: List[ClientApplication] = Await.result(futureApps, 30.seconds)
      val clientIds      = appData.map(_.clientId)
      val applicationIds = appData.map(_.applicationId)
      println(Console.GREEN + "4" + Console.RESET)
      Await.result(thirdPartyApplicationRequests.createSubscriptionFields(), 5.seconds)
      println(Console.GREEN + "5" + Console.RESET)

      TestDataSetupResult(
        bearerToken    = extractedToken,
        clientIds      = clientIds,
        applicationIds = applicationIds
      )

    } catch {
      case e: Exception =>
        cancel(s"Test has been aborted due to test setup failure: ${e.getMessage}")
    }
  }

  def testDataCleanUp(setupData: TestDataSetupResult): Unit = try
    setupData.applicationIds.foreach { appId =>
      Await.result(
        thirdPartyApplicationRequests.deleteClientApplication(setupData.bearerToken, appId),
        5.seconds
      )
    }
  finally {
    wsClient.close()
    system.terminate()
  }

}
case class ClientApplication(clientId: String, applicationId: String)
case class TestDataSetupResult(
  bearerToken: String,
  clientIds: List[String],
  applicationIds: List[String]
)
