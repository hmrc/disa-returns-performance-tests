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

import io.gatling.core.Predef.feed
import io.gatling.core.structure.ChainBuilder
import uk.gov.hmrc.performance.simulation.PerformanceTestRunner
import uk.gov.hmrc.perftests.disareturns.DisaMonthlyReturnsSubmissionRequests.submitMonthlyReport
import uk.gov.hmrc.perftests.disareturns.MonthlyReturnsDeclarationRequest.submitDeclaration
import uk.gov.hmrc.perftests.disareturns.ReconciliationReportService.{getReportingResultsSummary, makeReturnSummaryCallback, triggerReportReadyScenario}
import uk.gov.hmrc.perftests.disareturns.Util.RandomDataGenerator.{generateRandomISAReference, getMonth, getTaxYear}

class DisaMonthlyReturnsSubmissionSimulation extends PerformanceTestRunner with BaseRequests {

  before {
    testDataSetup()
  }
  after {
    testDataCleanUp()
  }

  val bearerTokenInformation: Iterator[Map[String, Any]] = Iterator.continually(
    Map("bearerToken" -> bearerToken)
  )

  val clientIdInformation: Iterator[Map[String, String]] = Iterator.continually(
    Map("clientId" -> clientIds(scala.util.Random.nextInt(clientIds.size)))
  )

  val isaMonthlyReportInformation: Iterator[Map[String, String]] =
    Iterator.continually(
      Map("isaManagerReference" -> generateRandomISAReference(), "taxYear" -> getTaxYear, "month" -> getMonth)
    )

  val reconciliationReportJourneyOneInformation: Iterator[Map[String, String]] =
    Iterator.continually(
      Map("isaManagerReference" -> generateRandomISAReference(), "taxYear" -> getTaxYear, "month" -> getMonth)
    )

  val reconciliationReportJourneyTwoInformation: Iterator[Map[String, String]] =
    Iterator.continually(
      Map("isaManagerReference" -> generateRandomISAReference(), "taxYear" -> getTaxYear, "month" -> getMonth)
    )

  val bearerTokenFeeder: ChainBuilder = feed(bearerTokenInformation)

  val clientIdFeeder: ChainBuilder = feed(clientIdInformation)

  val isaReportInformationFeeder: ChainBuilder = feed(isaMonthlyReportInformation)

  val reconciliationReportJourneyOneFeeder: ChainBuilder = feed(reconciliationReportJourneyOneInformation)

  val reconciliationReportJourneyTwoFeeder: ChainBuilder = feed(reconciliationReportJourneyTwoInformation)

  setup(
    "Disa-Monthly-returns-Submission",
    "Disa Monthly returns submission"
  ) withActions (bearerTokenFeeder.actionBuilders ++ isaReportInformationFeeder.actionBuilders ++ clientIdFeeder.actionBuilders: _*) withRequests (
    submitMonthlyReport,
    submitDeclaration
  )

  setup(
    "Reconciliation-Report-Journey-1",
    "Reconciliation Report Journey through the call back api"
  ) withActions (bearerTokenFeeder.actionBuilders ++ reconciliationReportJourneyOneFeeder.actionBuilders: _*) withRequests (
    makeReturnSummaryCallback,
    getReportingResultsSummary
  )

  setup(
    "Reconciliation-Report-Journey-2",
    "Reconciliation Report Journey through the test support api"
  ) withActions (bearerTokenFeeder.actionBuilders ++ reconciliationReportJourneyTwoFeeder.actionBuilders: _*) withRequests (
    triggerReportReadyScenario,
    getReportingResultsSummary
  )

  runSimulation()
}
