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

package uk.gov.hmrc.perftests.disareturns.constant

import uk.gov.hmrc.performance.conf.ServicesConfiguration

object AppConfig extends ServicesConfiguration {
  val disaReturnsHost: String               = baseUrlFor("disa-returns")
  val disaReturnsTestSupportHost: String    = baseUrlFor("disa-returns-test-support-api")
  val disaReturnsRoute: String              = "/monthly/"
  val disaReturnsCallbackPath: String       = "/callback/monthly/"
  val authHost: String                      = baseUrlFor("auth-login-api")
  val ggSignInUrl                           = s"$authHost/government-gateway/session/login"
  val disaReturnsStubHost: String           = baseUrlFor("disa-returns-stub")
  val reportingWindowPath: String           = "/test-only/etmp/reporting-window-state"
  val openObligationStatusPath: String      = "/test-only/etmp/open-obligation-status/"
  val third_party_application_host: String  = baseUrlFor("third-party-application")
  val ppns_host: String                     = baseUrlFor("push-pull-notification")
  val api_subscription_fields_host: String  = baseUrlFor("api-subscription-fields")
  val thirdPartyApplicationPath: String     = "/application"
  val ppnsPath: String                      = "/box"
  val subscriptionPath: String              = "/definition/context/disa-returns/version/1.0"
  val subscriptionFieldValuesPath: String   = "/field/application/clientId/context/disa-returns/version/1.0"
  val disaReturnsTestSupportBaseUrl: String = baseUrlFor("disa-returns-test-support-api")
  val reportingResultsSummaryPath: String   = "/results/summary"
  val reconciliationReportPath: String      = "/results"
  val disaReturnsSubmissionHost: String              = baseUrlFor("disa-returns-submission")
  val submissionClockPath: String                    = "/disa-returns-submission/test-only/clock/"
  val submissionMonthlyReturnsResetPath: String       = "/disa-returns-submission/test-only/monthly-returns"

  // Test data is submitted for month AUG, tax year 2026-27; disa-returns-submission's clock must be
  // pinned to a date within that same month/tax year (and inside declarationPeriodStart/End, currently
  // 6-19) for MonthlyReturnService.isWithinDeclarationPeriod to accept declarations.
  val submissionMonth: String        = "AUG"
  val submissionTaxYear: String      = "2026-27"
  val submissionClockDate: String    = "2026-08-17"

  val perfTestCredIdPrefix: String = "disa-returns-perf-test"
}
