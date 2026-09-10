/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.perftests.disareturns.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.perftests.disareturns.util.LoadSizing.{allocate, reservedReferences, userCount}

import scala.concurrent.duration.DurationInt

class LoadSizingSpec extends AnyWordSpec with Matchers {

  "userCount" should {
    "match the runner fallback phase counts at supported loads" in {
      Seq(2d -> 720, 5d -> 1800, 10d -> 3600).foreach { case (loadFactor, expected) =>
        userCount(
          smoke = false,
          journeyLoad = 1,
          loadFactor = loadFactor,
          rampUpTime = 1.minute,
          constantRateTime = 5.minutes,
          rampDownTime = 1.minute
        ) shouldBe expected
      }
    }

    "match the Jenkins phase counts at supported loads" in {
      Seq(2d -> 1080, 5d -> 2700, 10d -> 5400).foreach { case (loadFactor, expected) =>
        userCount(
          smoke = false,
          journeyLoad = 1,
          loadFactor = loadFactor,
          rampUpTime = 1.minute,
          constantRateTime = 8.minutes,
          rampDownTime = 1.minute
        ) shouldBe expected
      }
    }

    "allocate one declaration user for smoke" in {
      userCount(
        smoke = true,
        journeyLoad = 1,
        loadFactor = 10,
        rampUpTime = 1.minute,
        constantRateTime = 8.minutes,
        rampDownTime = 1.minute
      ) shouldBe 1
    }
  }

  "allocate" should {
    "exclude reserved references and keep allocations unique and disjoint" in {
      val allocated = allocate(declarationCount = 1501, submissionCount = 1000, reconciliationCount = 500)
      val all       = allocated.declaration ++ allocated.submission ++ allocated.reconciliation

      all.toSet    should contain noElementsOf reservedReferences
      all.distinct should have size all.size
    }

    "reject namespace overflow" in {
      val error = intercept[IllegalArgumentException](
        allocate(declarationCount = 99999998, submissionCount = 0, reconciliationCount = 0)
      )
      error.getMessage should include("exceed the 99999997 available references")
    }

    "support smoke allocations" in {
      val allocated = allocate(declarationCount = 1, submissionCount = 1, reconciliationCount = 1)

      allocated.declaration    shouldBe Vector("Z0000")
      allocated.submission     shouldBe Vector("Z0001")
      allocated.reconciliation shouldBe Vector("Z0002")
    }

    "support runs without the declaration journey" in {
      val allocated = allocate(declarationCount = 0, submissionCount = 2, reconciliationCount = 1)

      allocated.declaration    shouldBe empty
      allocated.submission     shouldBe Vector("Z0000", "Z0001")
      allocated.reconciliation shouldBe Vector("Z0002")
    }

    "allocate references with more than four digits" in {
      allocate(declarationCount = 10001, submissionCount = 0, reconciliationCount = 0).declaration should contain(
        "Z10000"
      )
    }
  }
}
