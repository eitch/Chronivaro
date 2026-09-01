#!/bin/bash

FROM_TAG=""
TO_TAG="HEAD"
RUNTIME_DIR="runtime"
OUTPUT_FILE=""

declare SCRIPT_DIR
SCRIPT_DIR="$(
  cd "${0%/*}" || exit
  pwd
)"

############################################################
# Logging                                                  #
############################################################
function fail() {
  echo 1>&2 -e "$(basename "$0"): ERROR: $*"
  exit 1
}
function err() {
  echo 1>&2 -e "$0: ERROR: $*"
  return 1
}
function warn() {
  echo -e "WARN: $*"
}
function info() {
  echo -e "INFO: $*"
}

############################################################
# Help                                                     #
############################################################
function usage {
  echo
  echo "Usage: $(basename "${0}") [options] [from-tag] [to-tag]" 2>&1
  echo "   -h                  show this help"
  echo "   -f <tag/commit>     source git revision (default: latest tag)"
  echo "   -t <tag/commit>     target git revision (default: HEAD)"
  echo "   -r <dir>            path to runtime directory (default: runtime)"
  echo "   -o <file>           output markdown file (default: stdout)"
  exit 1
}

############################################################
# Process the input options                                #
############################################################
optstring=":hf:t:r:o:"
while getopts ${optstring} arg; do
  case ${arg} in
  h) usage ;;
  f) FROM_TAG="${OPTARG}" ;;
  t) TO_TAG="${OPTARG}" ;;
  r) RUNTIME_DIR="${OPTARG}" ;;
  o) OUTPUT_FILE="${OPTARG}" ;;
  :) err "Option -$OPTARG requires an argument" || usage ;;
  \?) err "Invalid option: -${OPTARG}" || usage ;;
  esac
done
shift $((OPTIND - 1))

if [[ -n "$1" && -z "${FROM_TAG}" ]]; then
  FROM_TAG="$1"
  shift
fi

if [[ -n "$1" ]]; then
  TO_TAG="$1"
  shift
fi

if [[ "$*" != "" ]]; then
  err "Unexpected arguments: $*" || usage
fi

cd "${SCRIPT_DIR}" || exit

if ! which git >/dev/null; then
  fail "git is not installed!"
fi

# Auto-detect latest tag if not provided
if [[ -z "${FROM_TAG}" ]]; then
  FROM_TAG="$(git describe --tags --abbrev=0 2>/dev/null || git tag --sort=-v:refname 2>/dev/null | head -n 1 || true)"
  if [[ -z "${FROM_TAG}" ]]; then
    fail "Could not automatically detect previous git tag. Please specify with -f <tag>."
  fi
fi

APP_JAR="chronivaro-app/target/chronivaro.jar"
APP_CLASS="ch.eitchnet.chronivaro.app.RuntimeUpgradeInstructionsGenerator"

# Check if JAR exists and contains the class or fallback to java / maven
RUN_VIA_JAR=false
if [[ -f "${APP_JAR}" ]]; then
  RUN_VIA_JAR=true
fi

JAVA_ARGS=(-f "${FROM_TAG}" -t "${TO_TAG}" -r "${RUNTIME_DIR}")
if [[ -n "${OUTPUT_FILE}" ]]; then
  JAVA_ARGS+=(-o "${OUTPUT_FILE}")
fi

if [[ "${RUN_VIA_JAR}" == "true" ]]; then
  java -cp "${APP_JAR}:chronivaro-app/target/lib/*" "${APP_CLASS}" "${JAVA_ARGS[@]}"
elif [[ -d "chronivaro-app/target/classes" ]]; then
  java -cp "chronivaro-app/target/classes" "${APP_CLASS}" "${JAVA_ARGS[@]}"
elif which mvn >/dev/null; then
  # Build chronivaro-app classes if needed
  mvn test-compile -DskipTests >/dev/null || fail "Failed to compile project!"
  java -cp "chronivaro-app/target/classes" "${APP_CLASS}" "${JAVA_ARGS[@]}"
else
  fail "Neither ${APP_JAR} nor chronivaro-app/target/classes or Maven (mvn) is available to execute ${APP_CLASS}!"
fi
