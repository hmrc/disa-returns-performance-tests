# disa-returns-performance-tests

Performance test suite for the `DISA Returns`, using [performance-test-runner](https://github.com/hmrc/performance-test-runner) under the hood.

## Pre-requisites

### Services

Start Mongo Docker container following instructions from the [MDTP Handbook](https://docs.tax.service.gov.uk/mdtp-handbook/documentation/developer-set-up/set-up-mongodb.html).

Start services as follows:

```bash
sm2 --start DISA_RETURNS_ALL
```

The performance setup uses test-only endpoints in both `DISA_RETURNS` and `DISA_RETURNS_SUBMISSION`. Run both services
with `-Dapplication.router=testOnlyDoNotUseInAppConf.Routes` when starting them outside the configured non-production
environments. The suite also requires `DISA_RETURNS_STUB` at `POST /etmp/reporting-window-state`.

### Logging

The default log level for all HTTP requests is set to `WARN`. Configure [logback.xml](src/test/resources/logback.xml) to update this if required.

### WARNING :warning:

Do **NOT** run a full performance test against staging from your local machine. Please [implement a new performance test job](https://docs.tax.service.gov.uk/mdtp-handbook/documentation/mdtp-test-approach/performance-testing/performance-test-a-microservice/index.html) and execute your job from the dashboard in [Performance Jenkins](https://performance.tools.staging.tax.service.gov.uk).

## Tests

### Test Data Lifecycle

The suite prepares aggregate clock/reporting-window overrides and scoped monthly-return data before execution, then
deletes those records and overrides afterwards. Each aggregate uses the fixed clock date `2026-08-17` and that day's
reporting window. Full runs reserve these Z-reference ranges:

- Grouped declaration, callback, and summary journey: `Z0000` to `Z0499`
- Submission-only journeys: `Z0500` to `Z0509`
- Reconciliation-report journeys: `Z0510` to `Z0609`

Smoke runs use `Z0000`, `Z0001`, and `Z0002` for the grouped declaration journey, submission-only journey, and
reconciliation-report journey respectively. Do not overlap runs that use these ranges. Cleanup is scoped to the prepared
Z-references and does not delete all service data.

Submission setup and cleanup use:

- `POST /disa-returns-submission/test-only/monthly-returns` with `{"zReferences":[...]}` to delete scoped monthly returns
- `DELETE /disa-returns-submission/test-only/overrides/:zReference` to remove an aggregate override
- `PUT /disa-returns-submission/test-only/overrides/:zReference` to set its clock and reporting window
- `POST /test-only/monthly` on `DISA_RETURNS` with `{"zReferences":[...]}` to delete scoped callback summaries

Submission monthly-return cleanup covers `Z0000` to `Z0509` in full runs and `Z0000` to `Z0001` in smoke runs. Summary
cleanup covers declaration references only. Aggregate overrides cover every reserved reference, including reconciliation
references, and use the same fixed clock and reporting window.

The callback and summary requests are grouped causally after each successful submission and declaration. Full runs send
five callback/summary pairs per declaration, preserving the previous request-level 5:1 callback volume ratio without a
standalone journey racing declaration; smoke runs send one pair.

The submission-only journey deliberately cycles its load over ten Z-references in a full run. This is intentional
hot-aggregate contention. Gatling reports latency for the workload, but the suite has no latency pass/fail SLO.

### Routes Under Test

- `POST /monthly/:zReference`
- `POST /monthly/:zReference/declaration`
- `POST /callback/monthly/:zReference`
- `GET /monthly/:zReference/results/summary`
- `GET /monthly/:zReference/results?page=:page`

Tax year and month are derived from the aggregate override rather than URL segments.

### Bash Scripts

- `./smoke-run-tests.sh` runs every journey locally with one user per journey.
- `./local-run-tests.sh` runs the full local performance test using the configured journey loads.

Both scripts run `sbt scalafmtCheckAll scalafmtSbtCheck` before Gatling.
They stop immediately if formatting or Gatling fails.

Run either script from the repository root, for example:

```bash
./smoke-run-tests.sh
```

### Commands

Run smoke test (locally) as follows:

```bash
sbt -Dperftest.runSmokeTest=true -DrunLocal=true gatling:test
```

Run full performance test (locally) as follows:

```bash
sbt -DrunLocal=true gatling:test
```

Run smoke test (staging) as follows:

```bash
sbt -Dperftest.runSmokeTest=true -DrunLocal=false gatling:test
```

## Scalafmt

Check all project files are formatted as expected as follows:

```bash
sbt scalafmtCheckAll scalafmtSbtCheck
```

Format `*.sbt` and `project/*.scala` files as follows:

```bash
sbt scalafmtSbt
```

Format all project files as follows:

```bash
sbt scalafmtAll
```

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
