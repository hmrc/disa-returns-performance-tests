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
reporting window. References are allocated dynamically from `Z0000` to `Z9999`; reserved stub error references `Z1400`,
`Z1500`, and `Z1503` are always excluded. The suite fails before setup if the required allocation exceeds the remaining
9,997-reference namespace.

The declaration journey gets one unique reference for every user Gatling will inject. Its count is calculated from the
active `post-declare-monthly-returns` load, load percentage, and active ramp-up, constant-rate, and ramp-down durations
using the same truncation and rounding as the runner and Gatling. Its feeder is finite, so a reference cannot be reused.
Smoke runs always allocate one declaration reference.

Submission-only and reconciliation journeys share one authenticated circular pool configured by
`perftest.zReferencePoolSize` (default 1,000, smoke always one). They have independent feeders; reconciliation starts
halfway around the pool. The pool follows the declaration allocation in the list of allowed references, rather than
assuming a contiguous range. Shared credentials use the performance-test credential prefix required by the
reconciliation stub fast path and the same tokens are valid for submission.

At Jenkins loads, the default 1,000-reference pool is reused at these approximate intervals during constant load:

| Load percentage | Submission-only (base 10 JPS) | Reconciliation (base 5 JPS) |
| --- | ---: | ---: |
| 200 | 50 seconds | 100 seconds |
| 500 | 20 seconds | 40 seconds |
| 1000 | 10 seconds | 20 seconds |

Do not overlap runs that use this namespace. Cleanup is scoped to references prepared by the run and does not delete all
service data.

Submission setup and cleanup use:

- `POST /disa-returns-submission/test-only/monthly-returns` with `{"zReferences":[...]}` to delete scoped monthly returns
- `DELETE /disa-returns-submission/test-only/overrides/:zReference` to remove an aggregate override
- `PUT /disa-returns-submission/test-only/overrides/:zReference` to set its clock and reporting window
- `POST /test-only/monthly` on `DISA_RETURNS` with `{"zReferences":[...]}` to delete scoped callback summaries

Submission monthly-return cleanup covers all declaration and shared references. Summary cleanup covers declaration
references only. Aggregate overrides cover every prepared declaration and shared reference and use the same fixed clock
and reporting window. Setup and cleanup are rate-limited to five references per second; their `Await` budgets are derived
from that rate with a two-minute margin instead of fixed timeouts.

With Jenkins phases of 1 minute ramp-up, 8 minutes constant rate, and 1 minute ramp-down, expected allocations,
five-reference-per-second processing durations, and timeout budgets are:

| Load percentage | Unique declarations | Shared pool | Expected setup/cleanup | Timeout budget |
| --- | ---: | ---: | ---: | ---: |
| 200 | 1,080 | 1,000 | 6 minutes 56 seconds | 8 minutes 56 seconds |
| 500 | 2,700 | 1,000 | 12 minutes 20 seconds | 14 minutes 20 seconds |
| 1000 | 5,400 | 1,000 | 21 minutes 20 seconds | 23 minutes 20 seconds |

The runner's fallback 1/5/1-minute phases allocate 720, 1,800, and 3,600 unique declaration references at 200%, 500%,
and 1000% respectively. A custom shared pool can be supplied with `-Dperftest.zReferencePoolSize`; it must be positive
and fit beside the calculated declaration allocation in the available namespace.

The callback and summary requests are grouped causally after each successful submission and declaration. Full runs send
five callback/summary pairs per declaration, preserving the previous request-level 5:1 callback volume ratio without a
standalone journey racing declaration; smoke runs send one pair.

The submission-only and reconciliation journeys deliberately cycle over the shared reference pool. Gatling reports
latency for the workload, but the suite has no latency pass/fail SLO.

### Routes Under Test

- `POST /monthly/:zReference`
- `POST /monthly/:zReference/declaration`
- `POST /callback/monthly/:zReference`
- `GET /monthly/:zReference/results/summary`
- `GET /monthly/:zReference/results?page=:page`

Tax year and month are derived from the aggregate override rather than URL segments.

### Bash Scripts

- `./smoke-run-tests.sh` runs every journey locally with one user per journey.
- `./local-run-tests.sh` runs the full local performance test using the configured journey loads and a compact valid
  four-line NDJSON body. This keeps request-rate validation meaningful without sending about 28 GB through a single
  local service stack. Jenkins and direct `sbt` runs retain the configured 16,000-line body.

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
./local-run-tests.sh
```

Run the supported Jenkins profiles with active 1/8/1-minute phases as follows, substituting `200`, `500`, or `1000`:

```bash
sbt -DrunLocal=false \
  -Dperftest.loadPercentage=200 \
  -Dperftest.rampupTime=1 \
  -Dperftest.constantRateTime=8 \
  -Dperftest.rampdownTime=1 \
  gatling:test
```

To change the shared circular pool, add (for example) `-Dperftest.zReferencePoolSize=1500`. The declaration count plus
pool size cannot exceed the 9,997 non-reserved references.

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
