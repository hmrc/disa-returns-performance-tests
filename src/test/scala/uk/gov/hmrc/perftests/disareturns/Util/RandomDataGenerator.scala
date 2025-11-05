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

import scala.util.Random

object RandomDataGenerator {
  def generateNino(): String = {
    val prefix: String = "AA"
    val num            = Random.nextInt(1000000)
    val suffix         = "C"
    val nino           = f"$prefix$num%06d$suffix"
    nino
  }

  def generateAccountNumber(): String = {
    val number = Random.nextInt(999999) + 1
    f"STD$number%06d"
  }
  def generateRandomISAReference(): String =
    f"Z${scala.util.Random.nextInt(10000)}%04d"

  def getMonth: String = {
    val months = Seq("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    months(scala.util.Random.nextInt(months.length))
  }

  def getTaxYear: String = {
    val currentYear       = java.time.Year.now.getValue
    val nextYearTwoDigits = (currentYear + 1) % 100
    f"$currentYear-$nextYearTwoDigits%02d"
  }
}
