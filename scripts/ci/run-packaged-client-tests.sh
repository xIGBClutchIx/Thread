#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "usage: $0 <fabric|neoforge|forge> <dedicated|universal>" >&2
    exit 2
fi

loader="$1"
artifact_kind="$2"
matrix_file="build/matrix/supported-minecraft-versions.txt"
launcher=()

# Xvfb uses Mesa's software renderer on GitHub-hosted runners. Keeping llvmpipe to one worker
# prevents the invisible client from starving the integrated server while it prepares spawn chunks.
if [[ "${CI:-false}" == "true" ]]; then
    export LP_NUM_THREADS="${LP_NUM_THREADS:-1}"
fi

case "${loader}:${artifact_kind}" in
    fabric:dedicated)
        enabled_task="runRestartProductionClientGameTest"
        disabled_task="runMcpDisabledProductionClientGameTest"
        ;;
    fabric:universal)
        enabled_task="runUniversalRestartProductionClientGameTest"
        disabled_task="runUniversalMcpDisabledProductionClientGameTest"
        ;;
    neoforge:dedicated)
        enabled_task="verifyNeoForgeRestartProductionClientGameTest"
        disabled_task="verifyNeoForgeMcpDisabledProductionClientGameTest"
        launcher=(xvfb-run -a)
        ;;
    neoforge:universal)
        enabled_task="verifyUniversalNeoForgeRestartProductionClientGameTest"
        disabled_task="verifyUniversalNeoForgeMcpDisabledProductionClientGameTest"
        launcher=(xvfb-run -a)
        ;;
    forge:dedicated)
        enabled_task="verifyForgeRestartProductionClientGameTest"
        disabled_task="verifyForgeMcpDisabledProductionClientGameTest"
        launcher=(xvfb-run -a)
        ;;
    forge:universal)
        enabled_task="verifyUniversalForgeRestartProductionClientGameTest"
        disabled_task="verifyUniversalForgeMcpDisabledProductionClientGameTest"
        launcher=(xvfb-run -a)
        ;;
    *)
        echo "unsupported packaged-client target: ${loader} ${artifact_kind}" >&2
        exit 2
        ;;
esac

if [[ ! -s "${matrix_file}" ]]; then
    echo "supported Minecraft version matrix is missing: ${matrix_file}" >&2
    exit 1
fi

run_for_versions() {
    local task="$1"
    local version

    while IFS= read -r version; do
        [[ -z "${version}" ]] && continue
        echo "Running ${loader} ${artifact_kind} ${task} for Minecraft ${version}"
        "${launcher[@]}" ./gradlew --no-daemon ":loaders:${loader}:${version}:${task}"
    done < "${matrix_file}"
}

run_for_versions "${enabled_task}"
run_for_versions "${disabled_task}"
