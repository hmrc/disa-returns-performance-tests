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

import play.api.libs.ws.DefaultBodyWritables.writeableOf_String
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import uk.gov.hmrc.perftests.disareturns.constant.AppConfig.ggSignInUrl
import uk.gov.hmrc.perftests.disareturns.models.{SetupAssertions, SetupFailure}

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthRequests(ws: StandaloneAhcWSClient)(implicit ec: ExecutionContext) extends SetupAssertions {

  val authRequestPayload: String =
    """{
      |  "credentials": {
      |    "providerId": "8124873381064832",
      |    "providerType": "GovernmentGateway"
      |  },
      |  "affinityGroup": "Organisation",
      |  "credId": "credIdPlaceholder",
      |  "credentialStrength": "strong",
      |  "enrolments": [
      |    {
      |      "key": "HMRC-DISA-ORG",
      |      "identifiers": [{
      |         "key":"ZREF",
      |         "value":"isaReference"
      |      }],
      |      "state": "Activated"
      |    }
      |  ]
      |}""".stripMargin

  def getSubmissionBearerToken(zRef: String, credId: String = java.util.UUID.randomUUID().toString): Future[String] = {
    val url         = ggSignInUrl
    val bearerRegex = "Bearer\\s+\\S+".r
    val payload     = authRequestPayload
      .replaceAll("isaReference", zRef)
      .replaceAll("credIdPlaceholder", credId)
    ws.url(url)
      .addHttpHeaders(
        "Content-Type" -> "application/json"
      )
      .post(payload)
      .map { response =>
        ensureSetup(
          response.status == 201,
          s"Failed to retrieve bearer token. Status=${response.status}, Body=${response.body}"
        )
        response
          .header("Authorization")
          .flatMap(str => bearerRegex.findFirstMatchIn(str))
          .map(_.matched)
          .getOrElse(throw SetupFailure("Bearer token missing or invalid"))
      }
  }
}
