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
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder
import uk.gov.hmrc.perftests.disareturns.constant.AppConfig._
import uk.gov.hmrc.perftests.disareturns.constant.Headers.{headerOnlyWithBearerToken, headerWithBearerTokenAndContentTypeJson}

object ReconciliationReportService {

  val NpsCallbackPayload: String = s"""
                   |{
                   |  "totalRecords": 1000
                   |}
                   |""".stripMargin

  val makeReturnSummaryCallback: HttpRequestBuilder =
    http("Make return summary callback")
      .post(s"$disaReturnsHost$disaReturnsCallbackPath#{isaManagerReference}/2025-26/#{month}")
      .headers(headerWithBearerTokenAndContentTypeJson)
      .body(StringBody(NpsCallbackPayload))
      .check(status.is(204))

  val generateReconciliationReportPayload: String = s"""
         {
                   |    "oversubscribed": 1,
                   |    "traceAndMatch": 2,
                   |    "failedEligibility": 3
                   |}""".stripMargin

  val generateReconciliationReportScenario: HttpRequestBuilder =
    http("Trigger report ready scenario")
      .post(s"$disaReturnsTestSupportBaseUrl/#{isaManagerReference}/2025-26/#{month}/$testSupportPath")
      .headers(headerWithBearerTokenAndContentTypeJson)
      .body(StringBody(generateReconciliationReportPayload))
      .check(status.is(204))

  val getReportingResultsSummary: HttpRequestBuilder =
    http("Get Reporting Results Summary")
      .get(s"$disaReturnsHost$disaReturnsRoute#{isaManagerReference}/2025-26/#{month}$reportingResultsSummaryPath")
      .headers(headerOnlyWithBearerToken)
      .check(status.is(200))
}
