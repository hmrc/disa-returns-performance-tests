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

import java.lang.management.{BufferPoolMXBean, ManagementFactory}
import scala.jdk.CollectionConverters._

object DirectMemoryLogger {

  def log(): Unit = {
    val bufferPools: Seq[BufferPoolMXBean] =
      ManagementFactory.getPlatformMXBeans(classOf[BufferPoolMXBean]).asScala.toSeq

    bufferPools.foreach { pool =>
      if (pool.getName == "direct") {
        val usedMb = pool.getMemoryUsed / 1024 / 1024
        val capMb  = pool.getTotalCapacity / 1024 / 1024
        val count  = pool.getCount

        println(
          s"[DIRECT MEMORY] Used: ${usedMb}MB | Capacity: ${capMb}MB | Buffers: $count"
        )
      }
    }
  }
}
