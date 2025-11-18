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

package uk.gov.hmrc.perftests.disareturns.testSetup

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.scalatest.Assertions.cancel
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import uk.gov.hmrc.perftests.disareturns.models.TestDataSetupResult

import scala.concurrent.{ExecutionContext, Future}

trait BaseRequests {
  implicit val system: ActorSystem = ActorSystem("setup-system")
  implicit val mat: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher
  val wsClient: StandaloneAhcWSClient = StandaloneAhcWSClient()
  val authRequests = new AuthRequests(wsClient)
  val thirdPartyApplicationRequests = new ThirdPartyApplicationRequests(wsClient)
  val reportingWindowRequests = new ReportingWindowRequests(wsClient)
  val noOfThirdPartyApplications = 10

  def testDataSetup(): Future[TestDataSetupResult] = {
    val setup = for {
      bearerToken <- authRequests.getSubmissionBearerToken
      _ <- reportingWindowRequests.setReportingWindowsOpen()
      apps <- Future.traverse((1 to noOfThirdPartyApplications).toList) { _ =>
        for {
          app <- thirdPartyApplicationRequests.createClientApplication(bearerToken)
          _ <- thirdPartyApplicationRequests.createNotificationBox(app.clientId)
        } yield app
      }
      _ <- thirdPartyApplicationRequests.createSubscriptionFields()
    } yield TestDataSetupResult(bearerToken = bearerToken, clientIds = apps.map(_.clientId), applicationIds = apps.map(_.applicationId))
    setup.recover { case e =>
      cancel(s"Test has been aborted due to test setup failure: ${e.getMessage}")
    }
  }

  def testDataCleanUp(setupData: TestDataSetupResult): Future[Unit] =
    Future
      .traverse(setupData.applicationIds)(appId =>
        thirdPartyApplicationRequests.deleteClientApplication(setupData.bearerToken, appId)
      )
      .map(_ => ())
      .andThen { case _ =>
        wsClient.close()
        system.terminate()
      }
}
