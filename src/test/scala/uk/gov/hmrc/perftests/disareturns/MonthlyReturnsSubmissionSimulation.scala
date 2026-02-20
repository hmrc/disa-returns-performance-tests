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
import io.gatling.core.feeder.Feeder
import io.gatling.core.structure.ChainBuilder
import uk.gov.hmrc.performance.simulation.PerformanceTestRunner
import uk.gov.hmrc.perftests.disareturns.MonthlyReconciliationReportRequests.{getReportingResultsSummary, submitReturnSummaryCallback}
import uk.gov.hmrc.perftests.disareturns.MonthlyReturnsDeclarationRequest.submitDeclaration
import uk.gov.hmrc.perftests.disareturns.MonthlyReturnsSubmissionRequests.submitMonthlyReport
import uk.gov.hmrc.perftests.disareturns.TestOnlyRequests.openObligationStatus
import uk.gov.hmrc.perftests.disareturns.Util.DirectMemoryLogger
import uk.gov.hmrc.perftests.disareturns.Util.RandomDataGenerator.{getMonth, getTaxYear}
import uk.gov.hmrc.perftests.disareturns.models.TestDataSetupResult
import uk.gov.hmrc.perftests.disareturns.testSetup.BaseRequests

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class MonthlyReturnsSubmissionSimulation extends PerformanceTestRunner with BaseRequests {

  var setupData: TestDataSetupResult = _

  before {
    setupData = Await.result(testDataSetup(), 30.seconds)

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
    testDataCleanUp(setupData)
  }

  val isaManagerFeeder: ChainBuilder =
    feed(
      Iterator
        .continually(setupData.isaManager)
        .flatten
        .map { im =>
          Map(
            "isaManagerReference" -> im.zRef,
            "bearerToken"         -> im.bearerToken,
            "clientId"            -> im.clientId,
            "applicationId"       -> im.applicationId,
            "taxYear"             -> getTaxYear,
            "month"               -> getMonth
          )
        }
    )

  val isaManagerFeeder2: ChainBuilder =
    exec { session =>
      val im = setupData.isaManager(session.userId.toInt % setupData.isaManager.size)

      session
        .set("isaManagerReference", im.zRef)
        .set("bearerToken", im.bearerToken)
        .set("clientId", im.clientId)
        .set("applicationId", im.applicationId)
        .set("taxYear", getTaxYear)
        .set("month", getMonth)
    }

  setup(
    "monthly-returns-journey",
    "Monthly returns journey"
  ).withActions(
    isaManagerFeeder2.actionBuilders: _*
  ).withRequests(
    openObligationStatus,
    submitMonthlyReport,
    submitDeclaration,
    submitReturnSummaryCallback,
    getReportingResultsSummary
  )

  //TODO: Raising ticket to implement get report endpoint request
  //TODO: Improvement - implement call to test support API to create report

  runSimulation()
}
