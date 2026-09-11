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
import uk.gov.hmrc.perftests.disareturns.MonthlyReconciliationReportRequests._
import uk.gov.hmrc.perftests.disareturns.MonthlyReturnsDeclarationRequest._
import uk.gov.hmrc.perftests.disareturns.MonthlyReturnsSubmissionRequests._
import uk.gov.hmrc.perftests.disareturns.models.{Applications, IsaManagers}
import uk.gov.hmrc.perftests.disareturns.testSetup.BaseRequests
import uk.gov.hmrc.perftests.disareturns.util.{DirectMemoryLogger, LoadSizing}

import java.util.concurrent._
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class MonthlyReturnsSubmissionSimulation extends PerformanceTestRunner with BaseRequests {

  var setupIsaManagers: IsaManagers                   = _
  var setupIsaApplications: Applications              = _
  var setupNonDeclarationIsaManagers: IsaManagers     = _
  var memoryLoggerScheduler: ScheduledExecutorService = _

  private def userCount(journeyId: String): Int =
    definitions(labels)
      .find(_.id == journeyId)
      .map { journey =>
        LoadSizing.userCount(
          smoke = runSingleUserJourney,
          journeyLoad = journey.load,
          loadFactor = loadFactor,
          rampUpTime = rampUpTime,
          constantRateTime = constantRateTime,
          rampDownTime = rampDownTime
        )
      }
      .getOrElse(0)

  private val allocatedReferences       = LoadSizing.allocate(
    declarationCount = userCount("post-declare-monthly-returns"),
    submissionCount = userCount("post-submit-monthly-returns"),
    reconciliationCount = userCount("get-reconciliation-report")
  )
  private val declarationZReferences    = allocatedReferences.declaration
  private val submissionZReferences     = allocatedReferences.submission
  private val reconciliationZReferences = allocatedReferences.reconciliation
  private val nonDeclarationZReferences = submissionZReferences ++ reconciliationZReferences

  private val callbackRequests    = Seq.fill(if (runSingleUserJourney) 1 else 5)(submitReconciliationReportReadyCallback)
  private val declarationRequests = Seq(submitMonthlyReturn, submitDeclaration) ++ callbackRequests

  private val setupAndCleanupTimeout = 2.minutes

  before {
    val setup = for {
      declarationManagers    <- setupDeclarationZReferences(declarationZReferences)
      _                       = setupIsaManagers = declarationManagers
      applications           <- setupApplications()
      _                       = setupIsaApplications = applications
      nonDeclarationManagers <- setupNonDeclarationZReferences(nonDeclarationZReferences)
      _                       = setupNonDeclarationIsaManagers = nonDeclarationManagers
    } yield ()

    Await.result(setup, setupAndCleanupTimeout)

    memoryLoggerScheduler = Executors.newSingleThreadScheduledExecutor()

    memoryLoggerScheduler.scheduleAtFixedRate(
      new Runnable {
        override def run(): Unit = DirectMemoryLogger.log()
      },
      0,
      5,
      TimeUnit.SECONDS
    )
  }

  after {
    if (memoryLoggerScheduler != null) {
      memoryLoggerScheduler.shutdownNow()
    }

    val preparedSubmissionZReferences =
      Option(setupIsaManagers).toSeq.flatMap(_.isaManager.map(_.zRef)) ++
        Option(setupNonDeclarationIsaManagers).toSeq.flatMap(_.isaManager.map(_.zRef))

    Await.result(
      testDataCleanUp(Option(setupIsaApplications), preparedSubmissionZReferences),
      setupAndCleanupTimeout
    )
  }

  val appFeeder: ChainBuilder =
    feed(
      Iterator
        .continually(setupIsaApplications.applications)
        .flatten
        .map { im =>
          Map("clientId" -> im.clientId, "applicationId" -> im.applicationId)
        }
    )

  val declarationFeeder: ChainBuilder =
    feed(
      declarationZReferences.indices.iterator.map { index =>
        val im = setupIsaManagers.isaManager(index)
        Map(
          "isaManagerReference" -> im.zRef,
          "bearerToken"         -> im.bearerToken
        )
      }
    )

  private def nonDeclarationFeeder(zReferences: Seq[String], offset: Int): ChainBuilder =
    feed(
      zReferences.indices.iterator.map { index =>
        val im = setupNonDeclarationIsaManagers.isaManager(index + offset)
        Map(
          "isaManagerReference" -> im.zRef,
          "bearerToken"         -> im.bearerToken
        )
      }
    )

  val submissionOnlyFeeder: ChainBuilder = nonDeclarationFeeder(submissionZReferences, offset = 0)

  val reconciliationReportZRefFeeder: ChainBuilder =
    nonDeclarationFeeder(reconciliationZReferences, offset = submissionZReferences.size)

  setup(
    "post-submit-monthly-returns",
    "POST Submit Monthly Return"
  ).withActions(
    (submissionOnlyFeeder.actionBuilders ++ appFeeder.actionBuilders)*
  ).withRequests(
    submitMonthlyReturn
  )

  setup(
    "post-declare-monthly-returns",
    "POST Declare Monthly Return"
  ).withActions(
    (declarationFeeder.actionBuilders ++ appFeeder.actionBuilders)*
  ).withRequests(
    declarationRequests*
  )

  setup(
    "get-reconciliation-report",
    "Get Reconciliation Report"
  ).withActions(
    reconciliationReportZRefFeeder.actionBuilders*
  ).withRequests(
    getFirstReconciliationReportPage,
    getNextReconciliationReportPage
  )

  runSimulation()
}
