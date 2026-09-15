# disa-returns-performance-tests

Performance test suite for the `DISA Returns`, using [performance-test-runner](https://github.com/hmrc/performance-test-runner) under the hood.

## Pre-requisites

### Services

Start Mongo Docker container following instructions from the [MDTP Handbook](https://docs.tax.service.gov.uk/mdtp-handbook/documentation/developer-set-up/set-up-mongodb.html).

Start services as follows:

```bash
sm2 --start DISA_RETURNS_ALL --appendArgs '{
  "DISA_RETURNS": [
    "-Dfeatures.enrolment-verification-enabled=false",
    "-Dfeatures.strict-z-reference-validation-enabled=false",
    "-Dapplication.router=testOnlyDoNotUseInAppConf.Routes"
  ],
  "DISA_RETURNS_SUBMISSION": [
    "-Dfeatures.strict-z-reference-validation-enabled=false",
    "-Dapplication.router=testOnlyDoNotUseInAppConf.Routes"
  ]
}'
```

`DISA_RETURNS` needs enrolment verification disabled because the suite shares one bearer token across Z-references. Both
`DISA_RETURNS` and `DISA_RETURNS_SUBMISSION` need strict Z-reference validation disabled for references containing five
to eight digits. Their test-only routers provide the bulk setup and cleanup endpoints. The suite also requires
`DISA_RETURNS_STUB` at `POST /etmp/reporting-window-state`.

### Logging

The default log level for all HTTP requests is set to `WARN`. Configure [logback.xml](src/test/resources/logback.xml) to update this if required.

### WARNING :warning:

Do **NOT** run a full performance test against staging from your local machine. Please [implement a new performance test job](https://docs.tax.service.gov.uk/mdtp-handbook/documentation/mdtp-test-approach/performance-testing/performance-test-a-microservice/index.html) and execute your job from the dashboard in [Performance Jenkins](https://performance.tools.staging.tax.service.gov.uk).

## Tests

### Test Data Lifecycle

The suite prepares aggregate clock/reporting-window overrides and scoped monthly-return data before execution, then
deletes those records and overrides afterwards. Each aggregate uses the fixed clock date `2026-08-17` and that day's
reporting window. References are allocated dynamically using the staging-only 4-to-8 digit format. Reserved stub error
references `Z1400`, `Z1500`, and `Z1503` are excluded.

Each injected user gets a unique reference across the declaration, submission-only, and reconciliation journeys. Counts
are calculated from each journey's load and duration. Smoke runs allocate one reference per journey.

Do not overlap runs that use this namespace. Cleanup is scoped to references prepared by the run and does not delete all
service data.

Submission setup and cleanup use:

- `POST /disa-returns-submission/test-only/monthly-returns` with `{"zReferences":[...]}` to delete scoped monthly returns
- `POST /disa-returns-submission/test-only/overrides/delete` to delete overrides
- `PUT /disa-returns-submission/test-only/overrides` to set clock and reporting window overrides
- `POST /test-only/monthly` on `DISA_RETURNS` with `{"zReferences":[...]}` to delete scoped reconciliation-report-ready callback data

Submission monthly-return cleanup covers all references. Callback cleanup covers declaration
references only. Overrides use the same fixed clock and reporting window. One bearer token is shared by all
Z-references. Setup and cleanup time out after two minutes.

The callback requests are grouped causally after each successful submission and declaration. Full runs send five
callbacks per declaration, preserving the previous request-level 5:1 callback volume ratio without a standalone journey
racing declaration; smoke runs send one callback.

### Routes Under Test

- `POST /monthly/:zReference`
- `POST /monthly/:zReference/declaration`
- `POST /callback/monthly/:zReference`
- `GET /monthly/:zReference/results` with optional `cursor` and `limit` query parameters

Tax year and month are derived from the aggregate override rather than URL segments.

### Bash Scripts

- `./smoke-run-tests.sh` runs every journey locally with one user per journey.
- `./local-run-tests.sh` runs the full local performance test using the configured journey loads and a compact valid
  four-line NDJSON body. Jenkins and direct `sbt` runs use the representative configured 1,700-line body.

Both scripts stop immediately if Gatling fails.

Run either script from the repository root, for example:

```bash
./smoke-run-tests.sh
```

### Commands

Run formatting and compile the test suite before committing:

```bash
sbt precommit
```

Run smoke test (locally) as follows:

```bash
sbt -Dperftest.runSmokeTest=true -DrunLocal=true "Gatling / test"
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
  "Gatling / test"
```

At 100% load, a standard Jenkins run uploads about 600 monthly-return files with 1,700 records in each file. That's
roughly 1 million records over the run and is close to the estimated peak-month volume of 567 API submissions. Loads
above 100% are for capacity testing rather than expected traffic.

Run smoke test (staging) as follows:

```bash
sbt -Dperftest.runSmokeTest=true -DrunLocal=false "Gatling / test"
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
