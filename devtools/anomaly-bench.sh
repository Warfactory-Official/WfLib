#!/usr/bin/env bash
# Anomaly bench: a dev client boots straight into run/saves/anomaly-bench (-PquickPlay), AnomalyDebug sets the bench
# up (anomalies on, creative, an anomaly beside the player), runs the test (-Dwflib.anomalyTest, default walkaway:
# teleport away so the chunks unload, come back, check every link from anomaly to drawn lens) and quits. The window
# is parked on Hyprland workspace $BENCH_WS (15). Result: run/wflib-anomaly-test.txt, also [anomaly-test] in the log.
# GEMRENDER_JAR=<jar> runs against a local GemRender instead of the published snapshot; BENCH_ARGS adds Gradle
# arguments (BENCH_ARGS=-PwithSodium: the same with Sodium); BENCH_BLOCK picks the anomaly (wflib:whirligig);
# BENCH_TIME the time of day (night: to see the lighting); BENCH_JVM extra -D flags (-Dwflib.armTestSkin=true: the
# labelled arm skin, palm / back / thumb / pinky on the fist).
set -euo pipefail
cd "$(dirname "$0")/.."
ws=${BENCH_WS:-15}
init=()
if [[ -n "${GEMRENDER_JAR:-}" ]]; then
    script=$(mktemp --suffix=.gradle)
    cat > "$script" <<GRADLE
allprojects {
    configurations.configureEach { exclude group: 'com.wf.gemrender' }
    afterEvaluate { dependencies { compileOnly files('$GEMRENDER_JAR'); localRuntime files('$GEMRENDER_JAR') } }
}
GRADLE
    init=(-I "$script")
fi
[[ -d run/saves/anomaly-bench ]] || { echo "no run/saves/anomaly-bench: copy any 1.21.1 creative world with cheats there" >&2; exit 2; }
# NeoForge's early loading window hands off to the game's; parked mid-handoff, it times out and the run dies.
[[ -f run/config/fml.toml ]] && sed -i 's/^earlyWindowControl = true/earlyWindowControl = false/' run/config/fml.toml
rm -f run/wflib-anomaly-test.txt
python3 devtools/park-window.py run "$ws" &
park=$!
trap 'kill $park 2>/dev/null || true' EXIT
JAVA_TOOL_OPTIONS="${BENCH_JVM:-} -Dwflib.anomalyTest=${BENCH_TEST:-walkaway} -Dwflib.anomalyTest.quit=true -Dwflib.anomalyTest.block=${BENCH_BLOCK:-wflib:gravity_anomaly} -Dwflib.anomalyTest.time=${BENCH_TIME:-day} -Dwflib.anomalyTest.item=${BENCH_ITEM:-wflib:battery}" \
    ./gradlew "${init[@]}" runClient -PquickPlay=anomaly-bench ${BENCH_ARGS:-} || true
cat run/wflib-anomaly-test.txt 2>/dev/null || { echo "no result: see run/logs/latest.log" >&2; exit 1; }
