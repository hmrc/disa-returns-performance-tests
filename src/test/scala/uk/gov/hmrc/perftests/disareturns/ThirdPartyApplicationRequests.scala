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
import uk.gov.hmrc.perftests.disareturns.constant.AppConfig._
import uk.gov.hmrc.perftests.disareturns.constant.Headers.{headerWithBearerTokenAndContentTypeJson, subscriptionFieldsHeadersMap}

object ThirdPartyApplicationRequests {
  var cookies: String                   = _
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
                                                 |  "clientId": "1CLIENT_ID"
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

  val notificationBoxHeadersMap: Map[String, String] =
    Map("Content-Type" -> "application/json", "User-Agent" -> "disa-returns")

  def createClientApplication(): String = {
    val url      = s"$third_party_application_host$thirdPartyApplicationPath"
    val response = Http(url)
      .postData(clientApplicationPayload)
      .headers(headerWithBearerTokenAndContentTypeJson)
      .asString
    if (response.code != 201) {
      throw new RuntimeException(
        s"Failed to create client application. Status: ${response.code}, Body: ${response.body}"
      )
    }
    cookies = response.headers.get("Cookie") match {
      case Some(list) =>
        list.flatMap(_.split(";").headOption.map(_.trim)).mkString("; ")
      case None       => ""
    }
    println("-----test2--------")
    println(response.headers)
    val json     = ujson.read(response.body)
    val clientId = json("details")("token")("clientId").str
    clientId
  }

  def createNotificationBox(clientId: String): Unit = {
    println("-----test--------")
    println(cookies)
    val url         = s"$ppns_host$ppnsPath"
    val requestBody = notificationBoxPayload.replace("CLIENT_ID", clientId)
    val response    = Http(url)
      .put(requestBody)
      .headers(notificationBoxHeadersMap)
      .header("Cookie", cookies)
      .asString

    println("-------------")
    println(requestBody)
    println("-------------")
    println(notificationBoxHeadersMap)
    println("-------------")
    println(cookies)
    if (response.code != 200) {
      throw new RuntimeException(
        s"Failed2 to create notification box. Status: ${response.code}, Body: ${response.body}"
      )
    }
  }

  def createSubscriptionFields(): Unit = {
    val url      = s"$api_subscription_fields_host$subscriptionPath"
    val response = Http(url)
      .postData(subscriptionFieldsPayload)
      .headers(subscriptionFieldsHeadersMap)
      .asString

    if (response.code != 200) {
      throw new RuntimeException(
        s"Failed to create the subscription fields. Status: ${response.code}, Body: ${response.body}"
      )
    }
  }
}
