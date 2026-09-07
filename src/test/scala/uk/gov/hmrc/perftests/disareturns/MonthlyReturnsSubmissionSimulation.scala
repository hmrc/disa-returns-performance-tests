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

import com.typesafe.config.ConfigFactory
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
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class MonthlyReturnsSubmissionSimulation extends PerformanceTestRunner with BaseRequests {

  var setupIsaManagers: IsaManagers                   = _
  var setupIsaApplications: Applications              = _
  var setupSharedIsaManagers: IsaManagers             = _
  var memoryLoggerScheduler: ScheduledExecutorService = _

  private val noOfDeclarationZReferences = definitions(labels)
    .find(_.id == "post-declare-monthly-returns")
    .map { declarationJourney =>
      LoadSizing.declarationUserCount(
        smoke = runSingleUserJourney,
        journeyLoad = declarationJourney.load,
        loadFactor = loadFactor,
        rampUpTime = rampUpTime,
        constantRateTime = constantRateTime,
        rampDownTime = rampDownTime
      )
    }
    .getOrElse(0)
  private val configuredSharedPoolSize   = ConfigFactory.load().getInt("perftest.zReferencePoolSize")
  private val sharedPoolSize             = if (runSingleUserJourney) 1 else configuredSharedPoolSize
  private val allocatedReferences        = LoadSizing.allocate(noOfDeclarationZReferences, sharedPoolSize)
  private val declarationZReferences     = allocatedReferences.declaration
  private val sharedZReferences          = allocatedReferences.shared

  private val callbackRequests    = Seq.fill(if (runSingleUserJourney) 1 else 5)(submitReconciliationReportReadyCallback)
  private val declarationRequests = Seq(submitMonthlyReturn, submitDeclaration) ++ callbackRequests

  private def referenceOperationTimeout(referenceCount: Int): FiniteDuration =
    ((referenceCount + 4) / 5).seconds + 2.minutes

  before {
    val setup = for {
      declarationManagers <- setupDeclarationZReferences(declarationZReferences)
      _                    = setupIsaManagers = declarationManagers
      applications        <- setupApplications()
      _                    = setupIsaApplications = applications
      sharedManagers      <- setupSharedZReferences(sharedZReferences)
      _                    = setupSharedIsaManagers = sharedManagers
    } yield ()

    Await.result(setup, referenceOperationTimeout(declarationZReferences.size + sharedZReferences.size))

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
        Option(setupSharedIsaManagers).toSeq.flatMap(_.isaManager.map(_.zRef))

    Await.result(
      testDataCleanUp(Option(setupIsaApplications), preparedSubmissionZReferences),
      referenceOperationTimeout(declarationZReferences.size + sharedZReferences.size)
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

  private def sharedFeeder(rotation: Int): ChainBuilder =
    feed(
      LoadSizing.circular(sharedZReferences.indices, rotation).map { index =>
        val im = setupSharedIsaManagers.isaManager(index)
        Map(
          "isaManagerReference" -> im.zRef,
          "bearerToken"         -> im.bearerToken
        )
      }
    )

  val submissionOnlyFeeder: ChainBuilder = sharedFeeder(rotation = 0)

  val reconciliationReportZRefFeeder: ChainBuilder = sharedFeeder(rotation = sharedPoolSize / 2)

  val reconciliationReportPageFeeder: ChainBuilder =
    feed(
      Iterator
        .continually((0 until 100).map(page => Map("page" -> page)))
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
    declarationRequests: _*
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
