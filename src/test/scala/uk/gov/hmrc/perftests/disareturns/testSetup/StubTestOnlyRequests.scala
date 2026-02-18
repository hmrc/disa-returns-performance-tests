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

import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import uk.gov.hmrc.perftests.disareturns.constant.AppConfig._
import uk.gov.hmrc.perftests.disareturns.constant.Headers.headerWithJsonContentType
import uk.gov.hmrc.perftests.disareturns.models.SetupAssertions

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StubTestOnlyRequests(ws: StandaloneAhcWSClient)(implicit ec: ExecutionContext) extends SetupAssertions {
  val reportingWindowPayload: JsObject = Json.obj("reportingWindowOpen" -> true)

  def setReportingWindowsOpen(): Future[Unit] =
    ws.url(s"$disaReturnsStubHost$reportingWindowPath")
      .addHttpHeaders(headerWithJsonContentType.toSeq: _*)
      .post(reportingWindowPayload.toString())
      .map { response =>
        ensureSetup(
          response.status == 204,
          s"Failed to set the reporting window open. Status=${response.status}, Body=${response.body}"
        )
      }
}
