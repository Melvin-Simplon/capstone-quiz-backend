#!/usr/bin/env bash

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/workflows/lib.sh
source "${HERE}/lib.sh"

readonly RECAP_NAME="summarise-the-quality-report"
readonly SUREFIRE="${SUREFIRE:-target/surefire-reports}"
readonly JACOCO="${JACOCO:-target/site/jacoco/jacoco.xml}"
readonly PULL_REQUEST="${PULL_REQUEST:-}"

dashboard() {
    local key
    key=$(sed -n 's/.*<sonar.projectKey>\(.*\)<\/sonar.projectKey>.*/\1/p' pom.xml | tr -d ' \t')
    local url="https://sonarcloud.io/dashboard?id=${key}"
    [[ -n "$PULL_REQUEST" ]] && url="${url}&pullRequest=${PULL_REQUEST}"
    printf '%s' "$url"
}

tests() {
    python3 -c "
import glob, xml.etree.ElementTree as ET
run = failed = 0
for path in glob.glob('${SUREFIRE}/TEST-*.xml'):
    root = ET.parse(path).getroot()
    run += int(root.get('tests', 0))
    failed += int(root.get('failures', 0)) + int(root.get('errors', 0))
print(run, failed)
"
}

coverage_table() {
    python3 -c "
import xml.etree.ElementTree as ET
root = ET.parse('${JACOCO}').getroot()
print('| Metric | Covered |')
print('| --- | --- |')
for counter in root.findall('counter'):
    missed, covered = int(counter.get('missed')), int(counter.get('covered'))
    total = missed + covered
    pct = round(100 * covered / total, 1) if total else 0.0
    print(f\"| {counter.get('type').capitalize()} | {pct}% ({covered}/{total}) |\")
"
}

line_coverage() {
    python3 -c "
import xml.etree.ElementTree as ET
root = ET.parse('${JACOCO}').getroot()
for counter in root.findall('counter'):
    if counter.get('type') == 'LINE':
        missed, covered = int(counter.get('missed')), int(counter.get('covered'))
        print(round(100 * covered / (missed + covered), 1))
        break
"
}

report() {
    local run="$1" failed="$2" state="$3"
    summary "Quality" "Maven and SonarCloud" "$state" \
        "$(( run - failed )) passed, ${failed} failed, $(line_coverage)% of lines covered" <<TABLE
$(coverage_table)

[Open the SonarCloud dashboard]($(dashboard))
TABLE
}

main() {
    task "quality : put the tests and the coverage on the summary page"

    if [[ ! -d "$SUREFIRE" || ! -f "$JACOCO" ]]; then
        report_unreachable "quality" "no surefire or jacoco report, nothing to summarise"
        recap "$RECAP_NAME"
        return 0
    fi

    local run failed state
    read -r run failed < <(tests)
    [[ "$failed" -eq 0 ]] && state="ok" || state="failed"

    report "$run" "$failed" "$state"
    report_ok "quality" "${run} test(s) and the coverage written to the run summary"

    recap "$RECAP_NAME"
}

main "$@"
