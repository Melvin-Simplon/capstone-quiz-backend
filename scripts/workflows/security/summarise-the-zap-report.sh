#!/usr/bin/env bash

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/workflows/lib.sh
source "${HERE}/lib.sh"

readonly RECAP_NAME="summarise-the-zap-report"
readonly REPORT="${REPORT:-report_md.md}"
readonly TARGET="${TARGET:?}"
readonly RUN_URL="${RUN_URL:-}"

alerts() {
    awk -F'|' -v level="$1" '$2 ~ "^ *"level" *$" { gsub(/ /, "", $3); print $3; exit }' "$REPORT"
}

status() {
    [[ "$(alerts High)" == "0" ]] && printf 'ok' || printf 'failed'
}

figures() {
    printf '%s High, %s Medium, %s Low, %s Informational on %s' \
        "$(alerts High)" "$(alerts Medium)" "$(alerts Low)" "$(alerts Informational)" "$TARGET"
}

table() {
    sed -n '/^| Risk Level | Number of Alerts |/,/^$/p' "$REPORT"
}

report() {
    summary "DAST" "OWASP ZAP baseline" "$(status)" "$(figures)" <<TABLE
$(table)

[Full report in the zap-baseline-report artifact](${RUN_URL})
TABLE
}

main() {
    task "zap : put the findings on the summary page"

    if [[ ! -f "$REPORT" ]]; then
        report_unreachable "zap" "${REPORT} was never written, nothing to summarise"
    else
        report
        report_ok "zap" "findings written to the run summary"
    fi

    recap "$RECAP_NAME"
}

main "$@"
