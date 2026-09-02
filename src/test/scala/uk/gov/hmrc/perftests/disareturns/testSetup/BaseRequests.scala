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
import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.Assertions.cancel
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import uk.gov.hmrc.performance.conf.PerftestConfiguration
import uk.gov.hmrc.perftests.disareturns.constant.AppConfig.{perfTestCredIdPrefix, submissionClockDate}
import uk.gov.hmrc.perftests.disareturns.models.{Application, Applications, IsaManager, IsaManagers}

import java.time.{LocalDate, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.collection.concurrent.TrieMap
import scala.util.control.NonFatal

trait BaseRequests { self: PerftestConfiguration =>
  implicit val system: ActorSystem    = ActorSystem("setup-system")
  implicit val mat: Materializer      = Materializer(system)
  implicit val ec: ExecutionContext   = system.dispatcher
  val wsClient: StandaloneAhcWSClient = StandaloneAhcWSClient()
  val authRequests                    = new AuthRequests(wsClient)
  val thirdPartyApplicationRequests   = new ThirdPartyApplicationRequests(wsClient)
  val stubTestOnlyRequests            = new ReportingWindowRequest(wsClient)
  val disaReturnsTestOnlyRequests     = new DisaReturnsTestOnlyRequests(wsClient)
  val submissionTestOnlyRequests      = new SubmissionTestOnlyRequests(wsClient)

  val noOfThirdPartyApplications = if (runSingleUserJourney) 1 else 10

  private val authSetupParallelism = 10
  private val authSetupRateLimit   = 5
  private val createdApplications  = TrieMap.empty[String, Application]
  private val preparedZReferences  = TrieMap.empty[String, Unit]
  private val summaryZReferences   = TrieMap.empty[String, Unit]

  private def withBoundedConcurrency[A, B](items: Seq[A])(f: A => Future[B]): Future[Seq[B]] =
    Source(items.toList)
      .throttle(authSetupRateLimit, 1.second)
      .mapAsync(authSetupParallelism)(f)
      .runWith(Sink.seq)

  def setupDeclarationZReferences(zReferences: Seq[String]): Future[IsaManagers] = {
    zReferences.foreach(summaryZReferences.put(_, ()))

    val deleteMonthlyReturns =
      if (zReferences.nonEmpty) submissionTestOnlyRequests.deleteMonthlyReturns(zReferences) else Future.unit
    val deleteSummaries      =
      if (zReferences.nonEmpty) disaReturnsTestOnlyRequests.deleteMonthlyReturnSummaries(zReferences) else Future.unit

    val setup = for {
      _           <- stubTestOnlyRequests.setReportingWindowsOpen()
      _           <- deleteMonthlyReturns
      _           <- deleteSummaries
      isaManagers <- withBoundedConcurrency(zReferences) { zRef =>
                       for {
                         _           <- prepareSubmissionZReference(zRef)
                         bearerToken <- authRequests.getSubmissionBearerToken(zRef)
                       } yield IsaManager(
                         zRef = zRef,
                         bearerToken = bearerToken
                       )
                     }
    } yield IsaManagers(isaManager = isaManagers)

    setup.recover { case e =>
      cancel(s"Test has been aborted due to test setup failure: ${e.getMessage}")
    }
  }

  def setupSharedZReferences(zReferences: Seq[String]): Future[IsaManagers] = {
    val setup = for {
      _           <- submissionTestOnlyRequests.deleteMonthlyReturns(zReferences)
      isaManagers <- withBoundedConcurrency(zReferences) { zRef =>
                       for {
                         _           <- prepareSubmissionZReference(zRef)
                         bearerToken <- authRequests.getSubmissionBearerToken(zRef, s"$perfTestCredIdPrefix-$zRef")
                       } yield IsaManager(
                         zRef = zRef,
                         bearerToken = bearerToken
                       )
                     }
    } yield IsaManagers(isaManagers)

    setup.recover { case e =>
      cancel(s"Test has been aborted due to test setup failure: ${e.getMessage}")
    }
  }

  def setupApplications(): Future[Applications] = {

    val setup = for {
      _            <- thirdPartyApplicationRequests.createSubscriptionFields()
      applications <- withBoundedConcurrency(1 to noOfThirdPartyApplications) { _ =>
                        for {
                          bearerToken <- authRequests.getSubmissionBearerToken("Z1234")
                          app         <- thirdPartyApplicationRequests.createClientApplication(bearerToken)
                          application  = Application(
                                           bearer = bearerToken,
                                           clientId = app.clientId,
                                           applicationId = app.applicationId
                                         )
                          _            = createdApplications.put(application.applicationId, application)
                          _           <- thirdPartyApplicationRequests.createNotificationBox(app.clientId)
                        } yield application
                      }
    } yield applications

    setup.map(Applications.apply).recover { case NonFatal(e) =>
      cancel(s"Test has been aborted due to test setup failure: ${e.getMessage}")
    }
  }

  private def prepareSubmissionZReference(zReference: String): Future[Unit] = {
    val date      = LocalDate.parse(submissionClockDate)
    val startDate = date.atStartOfDay(ZoneOffset.UTC).toInstant.toString
    val endDate   = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant.minusNanos(1).toString

    preparedZReferences.put(zReference, ())
    for {
      _ <- submissionTestOnlyRequests.deleteOverrides(zReference)
      _ <- submissionTestOnlyRequests.setReportingWindowOverride(zReference, startDate, endDate)
    } yield ()
  }

  def testDataCleanUp(applications: Option[Applications], submissionZReferences: Seq[String]): Future[Unit] = {

    val applicationsToDelete =
      (applications.toSeq
        .flatMap(_.applications) ++ createdApplications.values).groupBy(_.applicationId).values.map(_.head)

    val applicationCleanup: Seq[Future[Unit]] = applicationsToDelete.toSeq.map { app =>
      thirdPartyApplicationRequests
        .deleteClientApplication(app.bearer, app.applicationId)
        .recover { case NonFatal(e) =>
          println(s"Warning: failed to delete application ${app.applicationId}: ${e.getMessage}")
          ()
        }
    }

    val zReferencesToDelete  = submissionZReferences.distinct
    val monthlyReturnCleanup =
      if (zReferencesToDelete.nonEmpty) {
        submissionTestOnlyRequests.deleteMonthlyReturns(zReferencesToDelete).recover { case NonFatal(e) =>
          println(s"Warning: failed to clean submission monthly returns: ${e.getMessage}")
          ()
        }
      } else Future.unit
    val summaryCleanup       =
      if (summaryZReferences.nonEmpty) {
        disaReturnsTestOnlyRequests.deleteMonthlyReturnSummaries(summaryZReferences.keys.toSeq).recover {
          case NonFatal(e) =>
            println(s"Warning: failed to clean disa-returns monthly return summaries: ${e.getMessage}")
            ()
        }
      } else Future.unit
    val overrideCleanup      =
      if (preparedZReferences.nonEmpty) {
        withBoundedConcurrency(preparedZReferences.keys.toSeq) { zReference =>
          submissionTestOnlyRequests.deleteOverrides(zReference).recover { case NonFatal(e) =>
            println(s"Warning: failed to clean submission overrides for $zReference: ${e.getMessage}")
            ()
          }
        }.map(_ => ())
      } else Future.unit
    val submissionCleanup    = Future.sequence(Seq(monthlyReturnCleanup, summaryCleanup, overrideCleanup)).map(_ => ())

    Future
      .sequence(applicationCleanup :+ submissionCleanup.map(_ => ()))
      .map(_ => ())
      .andThen { case _ =>
        wsClient.close()
        system.terminate()
      }
  }
}
