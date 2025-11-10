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

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ThirdPartyApplicationRequests(ws: StandaloneAhcWSClient)(implicit ec: ExecutionContext) {

  val clientApplicationPayload: String  = """{
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
  val notificationBoxPayload: String    = """{
                                                 |  "boxName": "obligations/declaration/isa/return##1.0##callbackUrl",
                                                 |  "clientId": "CLIENT_ID"
                                                 |}""".stripMargin
  val subscriptionFieldsPayload: String = """{
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

  def createClientApplication(token: String): Future[ClientApplication] = {
    val url = s"$third_party_application_host$thirdPartyApplicationPath"
    ws
      .url(url)
      .addHttpHeaders(
        "Authorization" -> token,
        "Content-Type"  -> "application/json"
      )
      .post(clientApplicationPayload)
      .map { response =>
        if (response.status != 201) {
          throw new RuntimeException(
            s"Failed to create client application. Status: ${response.status}, Body: ${response.body}"
          )
        }
        val json          = Json.parse(response.body)
        val clientId      = (json \ "details" \ "token" \ "clientId").as[String]
        val applicationId = (json \ "details" \ "id").as[String]
        ClientApplication(clientId, applicationId)
      }
  }

  def createNotificationBox(clientID: String): Future[Unit] = {
    val url         = s"$ppns_host$ppnsPath"
    val requestBody = notificationBoxPayload.replace("CLIENT_ID", clientID)
    ws
      .url(url)
      .withHttpHeaders(
        notificationBoxHadersMap.toSeq: _*
      )
      .put(requestBody)
      .map { response =>
        if (response.status != 201) Right(())
        else Left(s"Failed to create notification box. status ${response.status}, body: ${response.body}")
      }
  }

  def createSubscriptionFields(): Future[Unit] = {
    val url = s"$api_subscription_fields_host$subscriptionPath"
    ws
      .url(url)
      .addHttpHeaders(subscriptionFieldsHeadersMap.toSeq: _*)
      .put(subscriptionFieldsPayload)
      .map { response =>
        if (response.status != 201 && response.status != 200) Right(())
        else Left(s"Failed to create the subscription fields. status ${response.status}, body: ${response.body}")
      }
  }

  def deleteClientApplication(token: String, clientID: String): Future[Unit] = {
    val url = s"$third_party_application_host$thirdPartyApplicationPath/$clientID/delete"
    ws
      .url(url)
      .addHttpHeaders(
        "Authorization" -> token
      )
      .post("")
      .map { response =>
        if (response.status != 204) Right(())
        else Left(s"Failed to delete the thrird party application. Status ${response.status}, body: ${response.body}")
      }
  }
}
