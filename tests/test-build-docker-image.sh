#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

declare FIXTURE
declare CURRENT_CASE
declare RUN_STATUS
FAILURES=0

function new_fixture() {
  local name="$1"
  local version="$2"

  FIXTURE="${TEMP_DIR}/${name}"
  mkdir -p "${FIXTURE}/bin" "${FIXTURE}/chronivaro-app/target/lib"
  cp "${PROJECT_ROOT}/build-docker-image.sh" "${FIXTURE}/build-docker-image.sh"
  touch "${FIXTURE}/Dockerfile" "${FIXTURE}/chronivaro-app/target/chronivaro.jar"
  printf '<project><version>%s</version></project>\n' "${version}" > "${FIXTURE}/pom.xml"
  : > "${FIXTURE}/docker.log"

  cat > "${FIXTURE}/bin/xmlstarlet" <<'STUB'
#!/usr/bin/env bash
sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml | head -n 1
STUB
  cat > "${FIXTURE}/bin/docker" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "${DOCKER_LOG}"
if [[ "${DOCKER_FAIL_ON:-}" == "${1:-}" ]]; then
  exit 42
fi
STUB
  cat > "${FIXTURE}/bin/git" <<'STUB'
#!/usr/bin/env bash
if [[ -n "${GIT_BRANCH:-}" ]]; then
  printf '* %s\n' "${GIT_BRANCH}"
fi
STUB
  chmod +x "${FIXTURE}/bin/xmlstarlet" "${FIXTURE}/bin/docker" "${FIXTURE}/bin/git"
}

function run_script() {
  local branch="${1:-}"
  local failure="${2:-}"
  shift 2 || true

  if (
    cd "${FIXTURE}"
    export PATH="${FIXTURE}/bin:${PATH}"
    export DOCKER_LOG="${FIXTURE}/docker.log"
    export GIT_BRANCH="${branch}"
    export DOCKER_FAIL_ON="${failure}"
    ./build-docker-image.sh "$@"
  ) > "${FIXTURE}/output.log" 2>&1; then
    RUN_STATUS=0
  else
    RUN_STATUS=$?
  fi
}

function fail() {
  printf 'FAIL: %s: %s\n' "${CURRENT_CASE}" "$1" >&2
  FAILURES=$((FAILURES + 1))
}

function assert_status() {
  local expected="$1"
  if [[ "${RUN_STATUS}" -ne "${expected}" ]]; then
    fail "expected exit status ${expected}, got ${RUN_STATUS}"
  fi
}

function assert_log() {
  local expected actual expected_display actual_display
  if (($#)); then
    expected="$(printf '%s\n' "$@")"
  else
    expected=""
  fi
  actual="$(<"${FIXTURE}/docker.log")"
  if [[ "${actual}" != "${expected}" ]]; then
    printf -v expected_display '%q' "${expected}"
    printf -v actual_display '%q' "${actual}"
    fail "unexpected Docker calls; expected ${expected_display}, got ${actual_display}"
  fi
}

CURRENT_CASE="snapshot tags on develop with custom registry and cleanup"
new_fixture snapshot 0.10.3-SNAPSHOT
run_script develop '' -p -c -r registry.example
assert_status 0
assert_log \
  'image build --no-cache --pull -f Dockerfile --tag chronivaro:0.10.3-SNAPSHOT .' \
  'tag chronivaro:0.10.3-SNAPSHOT registry.example/docker/chronivaro:0-SNAPSHOT' \
  'push registry.example/docker/chronivaro:0-SNAPSHOT' \
  'tag chronivaro:0.10.3-SNAPSHOT registry.example/docker/chronivaro:0.10-SNAPSHOT' \
  'push registry.example/docker/chronivaro:0.10-SNAPSHOT' \
  'tag chronivaro:0.10.3-SNAPSHOT registry.example/docker/chronivaro:0.10.3-SNAPSHOT' \
  'push registry.example/docker/chronivaro:0.10.3-SNAPSHOT' \
  'image remove chronivaro:0.10.3-SNAPSHOT' \
  'image remove registry.example/docker/chronivaro:0-SNAPSHOT' \
  'image remove registry.example/docker/chronivaro:0.10-SNAPSHOT' \
  'image remove registry.example/docker/chronivaro:0.10.3-SNAPSHOT'

CURRENT_CASE="release version tags and latest"
new_fixture release 1.2.3
run_script '' '' -p
assert_status 0
assert_log \
  'image build --no-cache --pull -f Dockerfile --tag chronivaro:1.2.3 .' \
  'tag chronivaro:1.2.3 repo.strolch.li/docker/chronivaro:1' \
  'push repo.strolch.li/docker/chronivaro:1' \
  'tag chronivaro:1.2.3 repo.strolch.li/docker/chronivaro:1.2' \
  'push repo.strolch.li/docker/chronivaro:1.2' \
  'tag chronivaro:1.2.3 repo.strolch.li/docker/chronivaro:1.2.3' \
  'push repo.strolch.li/docker/chronivaro:1.2.3' \
  'tag chronivaro:1.2.3 repo.strolch.li/docker/chronivaro:latest' \
  'push repo.strolch.li/docker/chronivaro:latest'

CURRENT_CASE="build-only cleanup removes only the built image"
new_fixture build_only 2.4.6
run_script '' '' -c
assert_status 0
assert_log \
  'image build --no-cache --pull -f Dockerfile --tag chronivaro:2.4.6 .' \
  'image remove chronivaro:2.4.6'

for invalid_version in '1.2' 'v1.2.3' '1.2.3-beta' ''; do
  CURRENT_CASE="reject invalid version '${invalid_version}' before Docker operations"
  new_fixture invalid "${invalid_version}"
  run_script '' '' -p
  assert_status 1
  assert_log
done

CURRENT_CASE="propagate Docker push failures"
new_fixture push_failure 3.4.5
run_script '' push -p
assert_status 1
assert_log \
  'image build --no-cache --pull -f Dockerfile --tag chronivaro:3.4.5 .' \
  'tag chronivaro:3.4.5 repo.strolch.li/docker/chronivaro:3' \
  'push repo.strolch.li/docker/chronivaro:3'

if [[ "${FAILURES}" -ne 0 ]]; then
  printf '%s test assertion(s) failed.\n' "${FAILURES}" >&2
  exit 1
fi

printf 'All build-docker-image integration tests passed.\n'