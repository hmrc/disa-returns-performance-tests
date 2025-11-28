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

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder
import uk.gov.hmrc.perftests.disareturns.constant.AppConfig.ggSignInUrl

object MonthlyReturnLoginRequest {

  val authRequestPayload: String = """{
  "credentials": {
    "providerId": "8124873381064832",
    "providerType": "GovernmentGateway"
  },
  "affinityGroup": "Organisation",
  "credId": "1234567890",
  "credentialStrength": "strong",
  "enrolments": [
    {
      "key": "HMRC-DISA-ORG",
      "identifiers": [{
         "key":"ZREF",
         "value":"#{isaManagerReference}"
      }],
      "state": "Activated"
    }
  ]
}""".stripMargin

  def getBearerToken: HttpRequestBuilder =
    http("Retrieve bearer token")
      .post(ggSignInUrl)
      .body(StringBody(authRequestPayload))
      .asJson
      .check(status.is(201))
      .check(header(HttpHeaderNames.Authorization).saveAs("bearerToken"))
      .silent
}
