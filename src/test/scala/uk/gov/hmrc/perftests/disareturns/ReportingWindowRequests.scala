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

package uk.gov.hmrc.perftests.disareturns

import play.api.libs.json.{JsObject, Json}
import scalaj.http.Http
import uk.gov.hmrc.performance.conf.ServicesConfiguration
import uk.gov.hmrc.perftests.disareturns.constant.AppConfig._
import uk.gov.hmrc.perftests.disareturns.constant.Headers.reportingWindowHeaders

object ReportingWindowRequests extends ServicesConfiguration {
  val reportingWindowPayload: JsObject = Json.obj("reportingWindowOpen" -> true)

  def setReportingWindowsOpen(): Unit = {
    val url      = s"$disaReturnsStubHost$reportingWindowPath"
    val response = Http(url)
      .postData(reportingWindowPayload.toString())
      .headers(reportingWindowHeaders)
      .asString
    if (response.code != 204) {
      throw new RuntimeException(
        s"Failed to set reporting window open. Status: ${response.code}, Body: ${response.body}"
      )
    }
  }
}
