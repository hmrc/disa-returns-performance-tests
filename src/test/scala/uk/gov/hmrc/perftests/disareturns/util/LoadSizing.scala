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

  final case class AllocatedReferences(declaration: Vector[String], shared: Vector[String])

  val ReservedReferences: Set[String] = Set("Z1400", "Z1500", "Z1503")

  private val NoLoad = 0.0001d
  private val Namespace = (0 until 10000).map(value => f"Z$value%04d").filterNot(ReservedReferences).toVector

  def declarationUserCount(
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
      val rate =
        if ((constantRateTime.toSeconds * configuredRate).toInt < 1)
          1d / (constantRateTime.toSeconds - 1)
        else configuredRate

      val rampUpUsers   = ((NoLoad + (rate - NoLoad) / 2) * rampUpTime.toSeconds).toLong
      val constantUsers = (constantRateTime.toSeconds * rate).round
      val rampDownUsers = ((rate + (NoLoad - rate) / 2) * rampDownTime.toSeconds).toLong
      Math.toIntExact(rampUpUsers + constantUsers + rampDownUsers)
    }

  def allocate(declarationCount: Int, sharedPoolSize: Int): AllocatedReferences = {
    require(declarationCount >= 0, "declarationCount must not be negative")
    require(sharedPoolSize >= 1, "perftest.zReferencePoolSize must be at least 1")
    require(
      declarationCount + sharedPoolSize <= Namespace.size,
      s"Declaration count $declarationCount and shared pool size $sharedPoolSize exceed the ${Namespace.size} available Z-references"
    )

    val declaration = Namespace.take(declarationCount)
    val shared      = Namespace.slice(declarationCount, declarationCount + sharedPoolSize)
    AllocatedReferences(declaration, shared)
  }

  def circular[A](values: IndexedSeq[A], rotation: Int): Iterator[A] = {
    require(values.nonEmpty, "Circular pool must not be empty")
    require(rotation >= 0, "rotation must not be negative")

    Iterator.from(rotation).map(index => values(index % values.size))
  }
}
