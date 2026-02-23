/*
 * Copyright 2023 HM Revenue & Customs
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

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import uk.gov.hmrc.performance.simulation.PerformanceTestRunner
import uk.gov.hmrc.perftests.disareturns.MonthlyReconciliationReportRequests.{getReportingResultsSummary, submitReturnSummaryCallback}
import uk.gov.hmrc.perftests.disareturns.MonthlyReturnsDeclarationRequest.submitDeclaration
import uk.gov.hmrc.perftests.disareturns.MonthlyReturnsSubmissionRequests.submitMonthlyReport
import uk.gov.hmrc.perftests.disareturns.TestOnlyRequests.openObligationStatus
import uk.gov.hmrc.perftests.disareturns.Util.DirectMemoryLogger
import uk.gov.hmrc.perftests.disareturns.Util.RandomDataGenerator.{getMonth, getTaxYear}
import uk.gov.hmrc.perftests.disareturns.models.{Applications, IsaManagers}
import uk.gov.hmrc.perftests.disareturns.testSetup.BaseRequests

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class MonthlyReturnsSubmissionSimulation extends PerformanceTestRunner with BaseRequests {

  var setupIsaManagers: IsaManagers = _
  var setupIsaApplications: Applications = _

  before {
    setupIsaManagers = Await.result(setupTestData(), 30.seconds)
    setupIsaApplications = Await.result(setupApplications(), 30.seconds)

    val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()

    scheduler.scheduleAtFixedRate(
      new Runnable {
        override def run(): Unit = DirectMemoryLogger.log()
      },
      0,
      5,
      java.util.concurrent.TimeUnit.SECONDS
    )
  }

  after {
    testDataCleanUp(setupIsaApplications)
  }


  val appFeeder: ChainBuilder =
    feed(
      Iterator
        .continually(setupIsaApplications.applications)
        .flatten
        .map { im =>
          Map(
            "clientId"            -> im.clientId,
            "applicationId"       -> im.applicationId)
        }
    )

  val isaManagerFeeder: ChainBuilder =
    feed(
      Iterator
        .continually(setupIsaManagers.isaManager)
        .flatten
        .map { im =>
          Map(
            "isaManagerReference" -> im.zRef,
            "bearerToken"         -> im.bearerToken,
            "taxYear"             -> getTaxYear,
            "month"               -> getMonth
          )
        }
    )

  setup(
    "submit-monthly-returns",
    "Submit Monthly Returns"
  ).withActions(
    isaManagerFeeder.actionBuilders ++ appFeeder.actionBuilders: _*
  ).withRequests(
    openObligationStatus,
    submitMonthlyReport,
    submitDeclaration
  )

  setup(
    "nps-report-summary-callback",
    "NPS Report Summary Callback"
  ).withActions(
    isaManagerFeeder.actionBuilders ++ appFeeder.actionBuilders: _*
  ).withRequests(
    submitReturnSummaryCallback
  )

  //TODO: Ticket raised - DFI-1720
  // - to implement request to test support API to setup reconciliation report
  // - to implement request to get reconciliation report
  // - remove NPS callback as this is tested separately
  setup(
    "nps-retrieve-monthly-summary-and-report",
    "NPS Retrieve Monthly Summary and Report"
  ).withActions(
    isaManagerFeeder.actionBuilders ++ appFeeder.actionBuilders: _*
  ).withRequests(
    submitReturnSummaryCallback,
    getReportingResultsSummary
  )


  runSimulation()
}
