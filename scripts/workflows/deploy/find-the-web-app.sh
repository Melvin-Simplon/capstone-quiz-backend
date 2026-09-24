#!/usr/bin/env bash

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/workflows/lib.sh
source "${HERE}/lib.sh"

readonly RECAP_NAME="find-the-web-app"
readonly TAGGED="[?tags.component=='backend' && tags.project=='simplon-quiz']"

emit() {
    printf '%s=%s\n' "$1" "$2" >> "$GITHUB_OUTPUT"
}

query_one() {
    az webapp list --query "${TAGGED}.$1 | [0]" -o tsv
}

main() {
    task "azure : find the web app to deploy to"

    local name group state

    if ! name=$(query_one name); then
        report_unreachable "azure" "az webapp list did not answer"
        recap "$RECAP_NAME"
        return
    fi

    if [[ -z "$name" ]]; then
        report_skipped "backend" "nothing tagged component=backend, project=simplon-quiz"
        hint "the environment is not built: run make backend from the infrastructure repository"
        emit found false
        summary "deploy" "azure" "skipping" "no web app tagged component=backend" <<<'The environment does not exist, so there was nothing to deploy to. This is not a failed deployment.'
        recap "$RECAP_NAME"
        return
    fi

    group=$(query_one resourceGroup)
    state=$(query_one state)

    if [[ -z "$group" ]]; then
        report_failed "$name" "was listed but carries no resource group"
        recap "$RECAP_NAME"
        return
    fi

    if [[ "$state" != "Running" ]]; then
        report_failed "$name" "is ${state}, not Running"
        hint "az webapp start -n ${name} -g ${group}"
        recap "$RECAP_NAME"
        return
    fi

    report_ok "$name" "running in ${group}"
    emit found true
    emit name "$name"
    emit group "$group"
    recap "$RECAP_NAME"
}

main "$@"
