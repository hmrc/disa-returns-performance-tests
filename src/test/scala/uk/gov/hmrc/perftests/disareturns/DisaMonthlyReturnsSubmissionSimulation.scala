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
import uk.gov.hmrc.perftests.disareturns.ReconciliationReportService.{generateReconciliationReportScenario, getReportingResultsSummary, makeReturnSummaryCallback}
import uk.gov.hmrc.perftests.disareturns.Util.RandomDataGenerator.{generateRandomISAReference, getMonth, getTaxYear}

class DisaMonthlyReturnsSubmissionSimulation extends PerformanceTestRunner with BaseRequests {

  var setupData: TestDataSetupResult = _

  before {
    setupData = testDataSetup()
  }

  after {
    testDataCleanUp(setupData)
  }

  val bearerTokenFeeder: ChainBuilder = feed(Iterator.continually(
    Map("bearerToken" -> setupData.bearerToken)))

  val clientIdFeeder: ChainBuilder = feed(Iterator.continually(
    Map("clientId" -> setupData.clientIds(scala.util.Random.nextInt(setupData.clientIds.size)))))


  def generateReportInformation(): Iterator[Map[String, String]] = {
    Iterator.continually(
      Map(
        "isaManagerReference" -> generateRandomISAReference(),
        "taxYear" -> getTaxYear,
        "month" -> getMonth
      )
    )
  }
  setup(
    "Disa-Monthly-returns-Submission",
    "Disa Monthly returns submission"
  ) withActions (bearerTokenFeeder.actionBuilders ++ feed(generateReportInformation()).actionBuilders ++ clientIdFeeder.actionBuilders: _*) withRequests (
    submitMonthlyReport,
    submitDeclaration
  )
  setup(
    "Reconciliation-Report-Journey-1",
    "Reconciliation Report Journey through the call back api"
  ) withActions (bearerTokenFeeder.actionBuilders ++ feed(generateReportInformation()).actionBuilders: _*) withRequests (
    makeReturnSummaryCallback,
    getReportingResultsSummary
  )

  //This can probably be removed if we are having our own repo for test-support-api
  setup(
    "Reconciliation-Report-Journey-2",
    "Reconciliation Report Journey through the test support api"
  ) withActions (bearerTokenFeeder.actionBuilders ++ feed(generateReportInformation()).actionBuilders: _*) withRequests (
    generateReconciliationReportScenario,
    getReportingResultsSummary
  )

  runSimulation()
}
