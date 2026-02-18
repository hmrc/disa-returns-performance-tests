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
import uk.gov.hmrc.perftests.disareturns.models.{IsaManager, TestDataSetupResult}

import scala.concurrent.{ExecutionContext, Future}

trait BaseRequests {
  implicit val system: ActorSystem = ActorSystem("setup-system")
  implicit val mat: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher
  val wsClient: StandaloneAhcWSClient = StandaloneAhcWSClient()
  val authRequests = new AuthRequests(wsClient)
  val thirdPartyApplicationRequests = new ThirdPartyApplicationRequests(wsClient)
  val stubTestOnlyRequests = new StubTestOnlyRequests(wsClient)
  val noOfThirdPartyApplications = 10

  def testDataSetup(): Future[TestDataSetupResult] = {

    val zRefs: List[String] =
      (0 until noOfThirdPartyApplications)
        .map(i => {
          require(i < 10000, "Exceeded max ZRef limit (Z9999)")
          f"Z$i%04d"
        })
        .toList

    val setup = for {
      _ <- stubTestOnlyRequests.setReportingWindowsOpen()

      isaManagers <- Future.traverse(zRefs) { zRef =>
        for {
          bearerToken <- authRequests.getSubmissionBearerToken(zRef)

          app <- thirdPartyApplicationRequests.createClientApplication(bearerToken)

          _ <- thirdPartyApplicationRequests.createNotificationBox(app.clientId)

        } yield IsaManager(
          zRef = zRef,
          bearerToken = bearerToken,
          clientId = app.clientId,
          applicationId = app.applicationId
        )
      }

      _ <- thirdPartyApplicationRequests.createSubscriptionFields()

    } yield TestDataSetupResult(isaManager = isaManagers)

    setup.recover { case e =>
      cancel(s"Test has been aborted due to test setup failure: ${e.getMessage}")
    }
  }

  def testDataCleanUp(setupData: TestDataSetupResult): Future[Unit] =
    Future
      .traverse(setupData.isaManager) { im =>
        thirdPartyApplicationRequests.deleteClientApplication(
          im.bearerToken,
          im.applicationId
        )
      }
      .map(_ => ())
      .andThen { case _ =>
        wsClient.close()
        system.terminate()
      }

}
