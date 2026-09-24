#!/usr/bin/env bash
# Finds the API for a scheduled scan, which has no caller to hand it the url.
#
# By tag, like find-the-web-app.sh: the hostname changes with every rebuild of
# the environment. That environment is torn down between sessions, so finding
# no API is a normal state here. It reports skipping and leaves url empty,
# rather than failing a run that had nothing to scan.
#
# Writes to GITHUB_OUTPUT: url.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/workflows/lib.sh
source "${HERE}/lib.sh"

readonly RECAP_NAME="find-the-api-to-scan"
readonly QUERY="[?tags.project=='simplon-quiz' && tags.component=='backend'].defaultHostName | [0]"

# Silent without GITHUB_OUTPUT, so the script still runs by hand.
emit() {
    [[ -n "${GITHUB_OUTPUT:-}" ]] || return 0
    printf '%s=%s\n' "$1" "$2" >> "$GITHUB_OUTPUT"
}

main() {
    task "azure : find the deployed API to scan"

    local host
    if ! host=$(az webapp list --query "$QUERY" -o tsv); then
        report_unreachable "azure" "az webapp list did not answer"
        recap "$RECAP_NAME"
        return
    fi

    if [[ -z "$host" ]]; then
        report_skipped "backend" "no web app tagged project=simplon-quiz, component=backend"
        hint "the environment is not built, so there is nothing to scan"
        summary "dast" "OWASP ZAP" "skipping" "no API tagged project=simplon-quiz" <<<'The environment does not exist, so there was nothing to scan. This is not a failed scan.'
        emit url ""
        recap "$RECAP_NAME"
        return
    fi

    report_ok "backend" "https://${host}"
    emit url "https://${host}"
    recap "$RECAP_NAME"
}

main "$@"
