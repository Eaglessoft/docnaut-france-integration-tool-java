#!/bin/sh
set -eu
cd "$(dirname "$0")"
mvn --batch-mode --no-transfer-progress verify
