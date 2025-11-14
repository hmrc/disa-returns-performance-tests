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

import play.api.libs.json.Json
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import uk.gov.hmrc.perftests.disareturns.constant.AppConfig._
import uk.gov.hmrc.perftests.disareturns.constant.Headers.{notificationBoxHadersMap, subscriptionFieldsHeadersMap}
import uk.gov.hmrc.perftests.disareturns.models.{SetupAssertions, SetupFailure}

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ThirdPartyApplicationRequests(ws: StandaloneAhcWSClient)(implicit ec: ExecutionContext) extends SetupAssertions {

  val clientApplicationPayload: String =
    """{
      |  "name": "TEST APP",
      |  "access": {
      |    "accessType": "STANDARD",
      |    "redirectUris": [],
      |    "overrides": []
      |  },
      |  "environment": "SANDBOX",
      |  "collaborators": [
      |    {
      |      "emailAddress": "test@test.com",
      |      "role": "ADMINISTRATOR",
      |      "userId": "dfdf62b2-5f07-29d9-9302-45cd2e5eb49b"
      |    }
      |  ]
      |}""".stripMargin

  val notificationBoxPayload: String =
    """{
      |  "boxName": "obligations/declaration/isa/return##1.0##callbackUrl",
      |  "clientId": "CLIENT_ID"
      |}""".stripMargin

  val subscriptionFieldsPayload: String =
    """{
      |  "fieldDefinitions": [
      |    {
      |      "name": "callbackUrl",
      |      "shortDescription": "Notification URL",
      |      "description": "What is your notification web address for us to send push notifications to?",
      |      "type": "PPNSField",
      |      "hint": "You must only give us a web address that you own. Your application will use this address to listen to notifications from HMRC.",
      |      "validation": {
      |        "errorMessage": "notificationUrl must be a valid https URL",
      |        "rules": [
      |          {
      |            "UrlValidationRule": {}
      |          }
      |        ]
      |      }
      |    }
      |  ]
      |}""".stripMargin

  def createClientApplication(token: String): Future[ClientApplication] =
    ws.url(s"$third_party_application_host$thirdPartyApplicationPath")
      .addHttpHeaders(
        "Authorization" -> token,
        "Content-Type"  -> "application/json"
      )
      .post(clientApplicationPayload)
      .map { response =>
        ensureSetup(
          response.status == 201,
          s"Failed to create client application. Status=${response.status}, Body=${response.body}"
        )

        val json               = Json.parse(response.body)
        val maybeClientId      = (json \ "details" \ "token" \ "clientId").asOpt[String].filter(_.nonEmpty)
        val maybeApplicationId = (json \ "details" \ "id").asOpt[String].filter(_.nonEmpty)

        (maybeClientId, maybeApplicationId) match {
          case (Some(clientId), Some(applicationId)) =>
            ClientApplication(clientId, applicationId)
          case _                                     =>
            throw SetupFailure(
              s"JSON validation failed: 'clientId' or 'applicationId' missing/empty. Body=${response.body}"
            )
        }
      }

  def createNotificationBox(clientId: String): Future[Unit] =
    ws.url(s"$ppns_host$ppnsPath")
      .withHttpHeaders(notificationBoxHadersMap.toSeq: _*)
      .put(notificationBoxPayload.replace("CLIENT_ID", clientId))
      .map { response =>
        ensureSetup(
          response.status == 201,
          s"Failed to create notification box. Status=${response.status}, Body=${response.body}"
        )
      }

  def createSubscriptionFields(): Future[Unit] =
    ws.url(s"$api_subscription_fields_host$subscriptionPath")
      .addHttpHeaders(subscriptionFieldsHeadersMap.toSeq: _*)
      .put(subscriptionFieldsPayload)
      .map { response =>
        ensureSetup(
          response.status == 200 || response.status == 201,
          s"Failed to create subscription fields. Status=${response.status}, Body=${response.body}"
        )
      }

  def deleteClientApplication(token: String, clientId: String): Future[Unit] =
    ws.url(s"$third_party_application_host$thirdPartyApplicationPath/$clientId/delete")
      .addHttpHeaders("Authorization" -> token)
      .post("")
      .map { response =>
        ensureSetup(
          response.status == 204,
          s"Failed to delete client application. Status=${response.status}, Body=${response.body}"
        )
      }
}
