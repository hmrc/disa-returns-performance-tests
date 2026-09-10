#!/usr/bin/env bash
set -euo pipefail

sbt -Dperftest.runSmokeTest=true -DrunLocal=true "Gatling / test"
