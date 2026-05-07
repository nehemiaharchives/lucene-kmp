#!/usr/bin/env bash
set -euo pipefail

awk '
function format_duration(elapsed_ms) {
  if (elapsed_ms < 1000) {
    return sprintf("%8s", elapsed_ms " ms")
  }

  total_seconds = int(elapsed_ms / 1000)
  if (total_seconds < 60) {
    tenths = int((elapsed_ms % 1000) / 100)
    return sprintf("%8s", total_seconds "." tenths " s")
  }

  minutes = int(total_seconds / 60)
  seconds = total_seconds % 60
  return sprintf("%8s", minutes "m " seconds "s")
}

/^\[----------\][[:space:]]+[0-9]+[[:space:]]+tests? from [^ ]+ \([0-9]+ ms total\)$/ {
  count = $2
  suite = $5
  elapsed_ms = $6
  gsub(/\(/, "", elapsed_ms)
  print "PASSED SUITE: " format_duration(elapsed_ms + 0) " | " suite " (" count " tests)"
}
' "$1"
