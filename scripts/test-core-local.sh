#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KOTLINC:=kotlinc}"
"$KOTLINC" core/src/main/kotlin/com/rukn/core/*.kt core/src/main/kotlin/com/rukn/expiry/data/*.kt scripts/CoreSmoke.kt scripts/ExcelSmoke.kt scripts/CatalogSmoke.kt scripts/VoiceSmoke.kt -include-runtime -d /tmp/rukn-test-core.jar
java -cp /tmp/rukn-test-core.jar CoreSmokeKt
java -cp /tmp/rukn-test-core.jar ExcelSmokeKt
java -cp /tmp/rukn-test-core.jar CatalogSmokeKt
java -cp /tmp/rukn-test-core.jar VoiceSmokeKt
