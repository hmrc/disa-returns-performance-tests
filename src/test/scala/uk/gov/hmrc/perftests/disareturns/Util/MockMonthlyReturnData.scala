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

package uk.gov.hmrc.perftests.disareturns.Util

import com.typesafe.config.ConfigFactory
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.perftests.disareturns.models.isaAccounts.{LifetimeISASubscriptionPayload, LiftimeISAClosurePayload, StandardISAClosurePayload, StandardISASubscriptionPayload}

object MockMonthlyReturnData extends NdjsonSupport {
  private val config         = ConfigFactory.load()
  private val noOfJsons: Int = config.getInt("saveMonthlyReturnLocally.no-of-json-lines")
  def getLISASubscriptionPayload(
    nino: String,
    accountNumber: String
  ): LifetimeISASubscriptionPayload = LifetimeISASubscriptionPayload(
    accountNumber,
    nino,
    "First24997",
    "LISA Subscription model",
    "Last24997",
    "1980-01-22",
    2001.02,
    1988.53,
    "2025-06-01",
    2500.23,
    10000.12,
    "LIFETIME",
    "2025-01-22",
    5000.56,
    3000.56
  )

  def getLISAClosurePayload(
    nino: String,
    accountNumber: String
  ): LiftimeISAClosurePayload = LiftimeISAClosurePayload(
    accountNumber,
    nino,
    "First24998",
    "LISA Closure model",
    "Last24998",
    "1980-01-22",
    2500.23,
    125.23,
    "2025-01-22",
    10000.12,
    5000.56,
    "LIFETIME",
    "2025-06-01",
    "2025-03-22",
    "CLOSED",
    3000.56,
    4200.54
  )

  def getSISASubscriptionPayload(
    nino: String,
    accountNumber: String
  ): StandardISASubscriptionPayload = StandardISASubscriptionPayload(
    accountNumber,
    nino,
    "First25001",
    "SISA subscription model",
    "Last25001",
    "1980-01-22",
    2500.23,
    4560.12,
    "2025-06-01",
    10000.12,
    5678.12,
    "STOCKS_AND_SHARES",
    false
  )

  def getSISAClosurePayload(
    nino: String,
    accountNumber: String
  ): StandardISAClosurePayload = StandardISAClosurePayload(
    accountNumber,
    nino,
    "First25001",
    "SISA closure model",
    "Last25001",
    "1980-01-22",
    2500.23,
    4560.12,
    "2025-06-01",
    10000.12,
    5678.12,
    "INNOVATIVE_FINANCE",
    false,
    "2025-07-01",
    "VOID"
  )

  def validNdjsonTestData(): String = {

    def generatePayloadBlock(): Seq[JsValue] = {
      val lisaSubscriptionPayload =
        getLISASubscriptionPayload(RandomDataGenerator.generateNino(), RandomDataGenerator.generateAccountNumber())

      val lisaClosurePayload =
        getLISAClosurePayload(RandomDataGenerator.generateNino(), RandomDataGenerator.generateAccountNumber())

      val sisaSubscriptionPayload =
        getSISASubscriptionPayload(RandomDataGenerator.generateNino(), RandomDataGenerator.generateAccountNumber())

      val sisaClosurePayload =
        getSISAClosurePayload(RandomDataGenerator.generateNino(), RandomDataGenerator.generateAccountNumber())

      Seq(
        Json.toJson(lisaSubscriptionPayload),
        Json.toJson(lisaClosurePayload),
        Json.toJson(sisaSubscriptionPayload),
        Json.toJson(sisaClosurePayload)
      )
    }

    val allPayloads: Seq[JsValue] =
      Seq.fill(noOfJsons)(generatePayloadBlock()).flatten

    val ndjsonString = toNdjson(allPayloads)

    /** Uncomment below code to save the ndjson fine in case for the testing purposes. 5832 * 4 creates a 10MB NDJson
      * file, where 4 are the different types of subscription.
      */
    /** val path = Paths.get("test-data.txt") Files.write(path, ndjsonString.getBytes(StandardCharsets.UTF_8))* */
    ndjsonString
  }

}
