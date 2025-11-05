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
import uk.gov.hmrc.perftests.disareturns.AuthRequests.getSubmissionBearerToken
import uk.gov.hmrc.perftests.disareturns.DisaMonthlyReturnsSubmissionRequests.submitMonthlyReport
import uk.gov.hmrc.perftests.disareturns.MonthlyReturnsDeclarationRequest.submitDeclaration
import uk.gov.hmrc.perftests.disareturns.ReconciliationReportService.{getReportingResultsSummary, makeReturnSummaryCallback, triggerReportReadyScenario}
import uk.gov.hmrc.perftests.disareturns.ReportingWindowRequests.setReportingWindowsOpen
import uk.gov.hmrc.perftests.disareturns.ThirdPartyApplicationRequests.{createClientApplication, createNotificationBox, createSubscriptionFields}
import uk.gov.hmrc.perftests.disareturns.Util.RandomDataGenerator.{generateRandomISAReference, getMonth, getTaxYear}

import scala.collection.mutable.ListBuffer

class DisaMonthlyReturnsSubmissionSimulation extends PerformanceTestRunner {
  val clientIds           = ListBuffer[String]()
  var bearerToken: String = _

  before {
    bearerToken = getSubmissionBearerToken
    setReportingWindowsOpen()
    (1 to 1).foreach { i =>
      println("&&&&&&&&&&&&&& " +i)
      val clientId = createClientApplication()
      createNotificationBox(clientId)
      //createSubscriptionFields()
      clientIds += clientId
    }
  }

  val tokenFeeder: Iterator[Map[String, Any]] = Iterator.continually(
    Map("bearerToken" -> bearerToken)
  )

  val tokenFeederAction: ChainBuilder = feed(tokenFeeder)

  val clientFeeder: Iterator[Map[String, String]] = Iterator.continually(
    Map("clientId" -> clientIds(scala.util.Random.nextInt(clientIds.size)))
  )

  val clientFeedAction: ChainBuilder = feed(clientFeeder)

  val isaMonthlyReportInformation: Iterator[Map[String, String]] =
    Iterator.continually(
      Map("isaManagerReference" -> generateRandomISAReference(), "taxYear" -> getTaxYear, "month" -> getMonth)
    )
  def isaReportInformationFeeder: ChainBuilder                   = feed(isaMonthlyReportInformation)

  val reportingResultsSummaryInformation1: Iterator[Map[String, String]] =
    Iterator.continually(
      Map("isaManagerReference" -> generateRandomISAReference(), "taxYear" -> getTaxYear, "month" -> getMonth)
    )
  def reportingResultsSummaryFeeder1: ChainBuilder                       = feed(reportingResultsSummaryInformation1)

  val reportingResultsSummaryInformation2: Iterator[Map[String, String]] =
    Iterator.continually(
      Map("isaManagerReference" -> generateRandomISAReference(), "taxYear" -> getTaxYear, "month" -> getMonth)
    )
  def reportingResultsSummaryFeeder2: ChainBuilder                       = feed(reportingResultsSummaryInformation2)

  setup(
    "Disa-Monthly-returns-Submission",
    "Disa Monthly returns submission"
  ) withActions (tokenFeederAction.actionBuilders ++ isaReportInformationFeeder.actionBuilders ++ clientFeedAction.actionBuilders: _*) withRequests (
    submitMonthlyReport,
    submitDeclaration
  )

  setup(
    "Reconciliation-Report-Journey-1",
    "Reconciliation Report Journey through call back api"
  ) withActions (tokenFeederAction.actionBuilders ++ reportingResultsSummaryFeeder1.actionBuilders: _*) withRequests (
    makeReturnSummaryCallback,
    getReportingResultsSummary
  )

  setup(
    "Reconciliation-Report-Journey-2",
    "Reconciliation Report Journey through test support api"
  ) withActions (tokenFeederAction.actionBuilders ++ reportingResultsSummaryFeeder2.actionBuilders: _*) withRequests (
    triggerReportReadyScenario,
    getReportingResultsSummary
  )

  runSimulation()
}
