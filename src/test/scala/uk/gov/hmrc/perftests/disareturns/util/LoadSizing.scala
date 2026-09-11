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

import scala.concurrent.duration.FiniteDuration

object LoadSizing {

  final case class AllocatedReferences(
    declaration: Vector[String],
    submission: Vector[String],
    reconciliation: Vector[String]
  )

  val reservedReferences: Set[String] = Set("Z1400", "Z1500", "Z1503")

  private val noLoad        = 0.0001d
  private val namespaceSize = 100000000 - reservedReferences.size

  def userCount(
    smoke: Boolean,
    journeyLoad: Double,
    loadFactor: Double,
    rampUpTime: FiniteDuration,
    constantRateTime: FiniteDuration,
    rampDownTime: FiniteDuration
  ): Int =
    if (smoke) 1
    else {
      require(journeyLoad >= 0, "journeyLoad must not be negative")
      require(loadFactor >= 0, "loadFactor must not be negative")

      val configuredRate = journeyLoad * loadFactor
      val rate           =
        if ((constantRateTime.toSeconds * configuredRate).toInt < 1)
          1d / (constantRateTime.toSeconds - 1)
        else configuredRate

      val rampUpUsers   = ((noLoad + (rate - noLoad) / 2) * rampUpTime.toSeconds).toLong
      val constantUsers = (constantRateTime.toSeconds * rate).round
      val rampDownUsers = ((rate + (noLoad - rate) / 2) * rampDownTime.toSeconds).toLong
      Math.toIntExact(rampUpUsers + constantUsers + rampDownUsers)
    }

  def allocate(declarationCount: Int, submissionCount: Int, reconciliationCount: Int): AllocatedReferences = {
    require(declarationCount >= 0, "declarationCount must not be negative")
    require(submissionCount >= 0, "submissionCount must not be negative")
    require(reconciliationCount >= 0, "reconciliationCount must not be negative")

    val total = Seq(declarationCount, submissionCount, reconciliationCount).map(_.toLong).sum
    require(
      total <= namespaceSize,
      s"Required $total Z-references exceed the $namespaceSize available references"
    )

    val references          = Iterator
      .from(0)
      .map(value => f"Z$value%04d")
      .filterNot(reservedReferences)
      .take(total.toInt)
      .toVector
    val submissionStart     = declarationCount
    val reconciliationStart = submissionStart + submissionCount

    AllocatedReferences(
      declaration = references.take(declarationCount),
      submission = references.slice(submissionStart, reconciliationStart),
      reconciliation = references.drop(reconciliationStart)
    )
  }
}
