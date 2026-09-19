#!/usr/bin/env bash
# Answers whether there is a web app to deploy to, and where it is.
#
# The environment is built and torn down between sessions, so finding nothing is
# a normal state here: there is nothing to deploy, which is not the same as a
# deployment that did not work. That case reports skipping and hands the
# decision back through the found output, rather than failing the run.
#
# An app that exists but is stopped is the other case, and that one is an error:
# the jar would upload, the tracker would wait for a site that never starts, and
# the job would die on a timeout that names nothing.
#
# Asked for by tag rather than by name, so that renaming or rebuilding the
# infrastructure leaves this untouched.
#
# Writes to GITHUB_OUTPUT: found, and when found is true, name and group.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/workflows/lib.sh
source "${HERE}/lib.sh"

readonly RECAP_NAME="find-the-web-app"
readonly TAGGED="[?tags.component=='backend' && tags.project=='simplon-quiz']"

emit() {
    printf '%s=%s\n' "$1" "$2" >> "$GITHUB_OUTPUT"
}

# One query per value: asking for a pair returns a JSON array, which tsv prints
# one element per line rather than as two columns, so reading it into two
# variables silently leaves the second one empty.
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
