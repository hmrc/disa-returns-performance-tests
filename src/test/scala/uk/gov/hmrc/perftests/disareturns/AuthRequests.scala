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

import scalaj.http.Http
import uk.gov.hmrc.performance.conf.ServicesConfiguration
import uk.gov.hmrc.perftests.disareturns.constant.AppConfig.ggSignInUrl

object AuthRequests extends ServicesConfiguration {

  val authRequestPayload: String = """{
                                     |  "internalId": "Int-a7688cda-d983-472d-9971-ddca5f124641",
                                     |  "externalId": "Ext-c4ebc935-ac7a-4cc2-950a-19e6fac91f2a",
                                     |  "credentials": {
                                     |    "providerId": "8124873381064832",
                                     |    "providerType": "GovernmentGateway"
                                     |  },
                                     |  "credentialRole": "User",
                                     |  "agentInformation": {},
                                     |  "affinityGroup": "Organisation",
                                     |  "credId": "1234567890",
                                     |  "credentialStrength": "strong",
                                     |  "enrolments": [
                                     |    {
                                     |      "key": "",
                                     |      "identifiers": [],
                                     |      "state": ""
                                     |    }
                                     |  ]
                                     |}""".stripMargin

  def getSubmissionBearerToken: String = {
    val url         = ggSignInUrl
    val response    = Http(url)
      .postData(authRequestPayload)
      .header("Content-Type", "application/json")
      .header("Accept", "application/json")
      .asString
    if (response.code != 201) {
      throw new RuntimeException(
        s"Failed to retrieve the bearer token. Status: ${response.code}, Body: ${response.body}"
      )
    }
    val bearerToken = response.header("authorization") match {
      case Some(h) =>
        h.replaceAll(".*(Bearer\\s+\\S+).*", "$1")
      case None    =>
        throw new RuntimeException("Authorization header not found")
    }
    bearerToken
  }
}
