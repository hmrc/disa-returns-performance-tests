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
import uk.gov.hmrc.perftests.disareturns.constant.Headers.{headerOnlyWithBearerToken, headerWithJsonContentType}

object MonthlyReconciliationReportRequests {

  val npsCallbackPayload: String = s"""
                   |{
                   |  "totalRecords": 1000
                   |}
                   |""".stripMargin

  val submitReturnSummaryCallback: HttpRequestBuilder =
    http("POST Reconciliation report summary callback")
      .post(s"$disaReturnsHost$disaReturnsCallbackPath#{isaManagerReference}/2025-26/#{month}")
      .headers(headerWithJsonContentType)
      .body(StringBody(npsCallbackPayload))
      .check(status.is(204))

  val getReportingResultsSummary: HttpRequestBuilder =
    http("GET Reconciliation report summary")
      .get(s"$disaReturnsHost$disaReturnsRoute#{isaManagerReference}/2025-26/#{month}$reportingResultsSummaryPath")
      .headers(headerOnlyWithBearerToken)
      .check(status.is(200))

  val getReconciliationReport: HttpRequestBuilder =
    http("GET Reconciliation report")
      .get(
        s"$disaReturnsHost$disaReturnsRoute#{isaManagerReference}/#{taxYear}/#{month}$reconciliationReportPath?page=#{page}"
      )
      .headers(headerOnlyWithBearerToken)
      .check(status.is(200))
}
