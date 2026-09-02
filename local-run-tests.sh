#!/usr/bin/env bash
set -euo pipefail

sbt scalafmtCheckAll scalafmtSbtCheck

sbt \
  -DrunLocal=true \
  -DmonthlyReturnsTestPayload.numOfIsaAccountSets=1 \
  gatling:test
