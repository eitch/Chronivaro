#!/bin/bash

declare REGISTRY
declare PUSH_TO_REGISTRY

APP_TAG="chronivaro"
JAR_NAME="chronivaro.jar"
APP_PATH="chronivaro-app"
TARGET_PATH="${APP_PATH}/target/${JAR_NAME}"
TARGET_LIB_PATH="${APP_PATH}/target/lib"

REGISTRY="repo.strolch.li"
PUSH_TO_REGISTRY=false
CLEAN_AFTER_PUSH=false

declare SCRIPT_DIR
SCRIPT_DIR="$(
  cd "${0%/*}" || exit
  pwd
)"

############################################################
# Logging                                                  #
############################################################
function fail() {
  # log the error to console
  echo 1>&2 -e "$(basename $0): ERROR: $*"

  # exit the script
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

function is_semver_snapshot() {
  version=$1
  snapshot=${version##*-SNAPSHOT}
  test "$snapshot" != "${version}"
  return $?
}

############################################################
# Help                                                     #
############################################################
function usage {
  echo
  echo "Usage: $(basename "${0}") [options] " 2>&1
  echo "   -h   show the help"
  echo "   -p   push to registry"
  echo "   -c   clean local images after pushing"
  echo "   -r   registry to push to"
  exit 1
}

############################################################
# Process the input options                                #
############################################################
optstring=":hpcr:"
while getopts ${optstring} arg; do
  case ${arg} in
  h) usage ;;
  p) PUSH_TO_REGISTRY=true ;;
  c) CLEAN_AFTER_PUSH=true ;;
  r) REGISTRY="${OPTARG}" ;;
  :) err "Option -$OPTARG requires an argument" || usage ;;
  \?) err "Invalid option: -${OPTARG}" || usage ;;
  esac
done
shift $((OPTIND - 1))
if [[ "$*" != "" ]]; then
  err "Unexpected arguments: $*" || usage
fi

if ! which xmlstarlet >/dev/null; then
  fail "xmlstarlet is not installed!"
fi

cd "${SCRIPT_DIR}" || exit
if ! [[ -f pom.xml ]]; then
  fail "pom.xml does not exist in $(pwd)"
fi

declare BRANCH
declare VERSION
declare DOCKER_TAG

BRANCH="$(git branch 2>/dev/null | sed -e '/^[^*]/d' -e 's/* \(.*\)/\1/')"
if [[ "${BRANCH}" == "develop" ]]; then
  VERSION="develop"
else
  VERSION="$(xmlstarlet sel -t -m _:project -v _:version -n pom.xml)"
fi
DOCKER_TAG="${APP_TAG}:${VERSION}"
DOCKER_LATEST_TAG="${APP_TAG}:latest"

info "Building ${APP_TAG} docker image with tag: ${DOCKER_TAG}, using registry ${REGISTRY}"

if ! [[ -f "${TARGET_PATH}" ]] || ! [[ -d "${TARGET_LIB_PATH}" ]]; then
  err "Couldn't find ${TARGET_PATH} or ${TARGET_LIB_PATH}"
  fail "First build project before creating docker image!"
fi

info "Building image..."
docker image build --no-cache --load --pull -f Dockerfile --tag "${DOCKER_TAG}" . || fail "Could not build docker image"
info

TAG_PATH="${REGISTRY}/docker/${DOCKER_TAG}"
TAG_LATEST_PATH="${REGISTRY}/docker/${DOCKER_LATEST_TAG}"

if [[ "${PUSH_TO_REGISTRY}" == "true" ]]; then
  info "Pushing image..."

  info "Docker image ${DOCKER_TAG} ready, now tagging and pushing to registry ${REGISTRY}..."

  info "Tagging ${DOCKER_TAG} for remote tag ${TAG_PATH}"
  docker tag "${DOCKER_TAG}" "${TAG_PATH}" || fail "Could not tag image"
  docker push "${TAG_PATH}" || fail "Could not push image"
  info "Pushed ${DOCKER_TAG}"

  if ! is_semver_snapshot "${VERSION}"; then
    info "Tagging ${DOCKER_TAG} for latest remote tag ${TAG_LATEST_PATH}"
    docker tag "${DOCKER_TAG}" "${TAG_LATEST_PATH}" || fail "Could not tag image"
    docker push "${TAG_LATEST_PATH}" || fail "Could not push image"
    info
    info "Pushed ${TAG_LATEST_PATH}"
  fi
else
  info "Not pushing to registry."
fi

if [[ "${CLEAN_AFTER_PUSH}" == "true" ]]; then
  info
  info "Cleaning up..."
  docker image remove "${DOCKER_TAG}" || fail "Could not remove image"
  docker image remove "${TAG_PATH}" || fail "Could not remove image"
  if ! is_semver_snapshot "${VERSION}"; then
    docker image remove "${TAG_LATEST_PATH}" || fail "Could not remove image"
  fi
  info
fi

info "Done."
exit 0
