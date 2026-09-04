#!/usr/bin/env bash
set -euo pipefail

sbt scalafmtCheckAll scalafmtSbtCheck
sbt -Dperftest.runSmokeTest=true -DrunLocal=true gatling:test
