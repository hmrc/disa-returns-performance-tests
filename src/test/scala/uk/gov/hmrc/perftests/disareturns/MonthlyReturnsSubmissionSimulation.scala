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
import uk.gov.hmrc.perftests.disareturns.MonthlyReconciliationReportRequests.{getReconciliationReport, getReportingResultsSummary, submitReturnSummaryCallback}
import uk.gov.hmrc.perftests.disareturns.MonthlyReturnsDeclarationRequest.submitDeclaration
import uk.gov.hmrc.perftests.disareturns.MonthlyReturnsSubmissionRequests.submitMonthlyReturn
import uk.gov.hmrc.perftests.disareturns.Util.DirectMemoryLogger
import uk.gov.hmrc.perftests.disareturns.Util.RandomDataGenerator.{getMonth, getTaxYear}
import uk.gov.hmrc.perftests.disareturns.constant.AppConfig.{submissionMonth, submissionTaxYear}
import uk.gov.hmrc.perftests.disareturns.models.{Applications, IsaManagers}
import uk.gov.hmrc.perftests.disareturns.testSetup.BaseRequests

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class MonthlyReturnsSubmissionSimulation extends PerformanceTestRunner with BaseRequests {

  var setupIsaManagers: IsaManagers = _
  var setupIsaApplications: Applications = _
  var setupReconciliationReportIsaManagers: IsaManagers = _
  var setupSubmissionOnlyIsaManagers: IsaManagers = _

  before {
    setupIsaManagers = Await.result(setupTestData(), 3.minutes)
    setupIsaApplications = Await.result(setupApplications(), 1.minute)
    setupReconciliationReportIsaManagers = Await.result(setupReconciliationReportZReferences(), 1.minute)
    setupSubmissionOnlyIsaManagers = Await.result(setupSubmissionOnlyZReferences(), 1.minute)

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
            "taxYear"             -> submissionTaxYear,
            "month"               -> submissionMonth
          )
        }
    )

  val declarationFeeder: ChainBuilder =
    feed(
      Iterator
        .continually(setupIsaManagers.isaManager)
        .flatten
        .take(noOfDeclarationZReferences)
        .map { im =>
          Map(
            "isaManagerReference" -> im.zRef,
            "bearerToken"         -> im.bearerToken,
            "taxYear"             -> submissionTaxYear,
            "month"               -> submissionMonth
          )
        }
    )

  val submissionOnlyFeeder: ChainBuilder =
    feed(
      Iterator
        .continually(setupSubmissionOnlyIsaManagers.isaManager)
        .flatten
        .map { im =>
          Map(
            "isaManagerReference" -> im.zRef,
            "bearerToken"         -> im.bearerToken,
            "taxYear"             -> submissionTaxYear,
            "month"               -> submissionMonth
          )
        }
    )

  val reconciliationReportZRefFeeder: ChainBuilder =
    feed(
      Iterator
        .continually(setupReconciliationReportIsaManagers.isaManager)
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

  val reconciliationReportPageFeeder: ChainBuilder =
    feed(
      Iterator
        .continually((0 until 100).map(page => Map("page" -> page.toString)))
        .flatten
    )

  setup(
    "post-submit-monthly-returns",
    "POST Submit Monthly Return"
  ).withActions(
    submissionOnlyFeeder.actionBuilders ++ appFeeder.actionBuilders: _*
  ).withRequests(
    submitMonthlyReturn
  )

  setup(
    "post-declare-monthly-returns",
    "POST Declare Monthly Return"
  ).withActions(
    declarationFeeder.actionBuilders ++ appFeeder.actionBuilders: _*
  ).withRequests(
    submitMonthlyReturn,
    submitDeclaration
  )

  setup(
    "get-report-summary-callback-and-summary",
    "GET Report Summary Callback & Summary"
  ).withActions(
    isaManagerFeeder.actionBuilders ++ appFeeder.actionBuilders: _*
  ).withRequests(
    submitReturnSummaryCallback,
    getReportingResultsSummary
  )

  setup(
    "get-reconciliation-report",
    "Get Reconciliation Report"
  ).withActions(
    reconciliationReportZRefFeeder.actionBuilders ++ reconciliationReportPageFeeder.actionBuilders: _*
  ).withRequests(
    getReconciliationReport
  )

  runSimulation()
}
