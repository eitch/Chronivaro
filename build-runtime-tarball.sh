#!/bin/bash

SOURCE_DIR="runtime"
OUTPUT_FILE="runtime.tar.gz"

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
  echo 1>&2 -e "$(basename "$0"): ERROR: $*"

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

############################################################
# Help                                                     #
############################################################
function usage {
  echo
  echo "Usage: $(basename "${0}") [options] " 2>&1
  echo "   -h         show the help"
  echo "   -s <dir>   source runtime directory (default: runtime)"
  echo "   -o <file>  output tar.gz file (default: runtime.tar.gz)"
  exit 1
}

############################################################
# Process the input options                                #
############################################################
optstring=":hs:o:"
while getopts ${optstring} arg; do
  case ${arg} in
  h) usage ;;
  s) SOURCE_DIR="${OPTARG}" ;;
  o) OUTPUT_FILE="${OPTARG}" ;;
  :) err "Option -$OPTARG requires an argument" || usage ;;
  \?) err "Invalid option: -${OPTARG}" || usage ;;
  esac
done
shift $((OPTIND - 1))
if [[ "$*" != "" ]]; then
  err "Unexpected arguments: $*" || usage
fi

cd "${SCRIPT_DIR}" || exit

if ! [[ -d "${SOURCE_DIR}" ]]; then
  fail "Source directory '${SOURCE_DIR}' does not exist!"
fi

if ! which tar >/dev/null; then
  fail "tar is not installed!"
fi

if ! which gzip >/dev/null; then
  fail "gzip is not installed!"
fi

# Convert to absolute path for output file before creating temporary directory
if [[ "${OUTPUT_FILE}" != /* ]]; then
  OUTPUT_FILE="$(pwd)/${OUTPUT_FILE}"
fi

TMP_DIR="$(mktemp -d -t chronivaro-runtime-XXXXXX)"
function cleanup() {
  if [[ -d "${TMP_DIR}" ]]; then
    rm -rf "${TMP_DIR}"
  fi
}
trap cleanup EXIT

info "Staging runtime files from ${SOURCE_DIR}..."

mkdir -p "${TMP_DIR}/runtime"

# Copy files while excluding temp and data/dbStore
for item in "${SOURCE_DIR}/"*; do
  if ! [[ -e "${item}" ]]; then
    continue
  fi
  base_item="$(basename "${item}")"

  if [[ "${base_item}" == "temp" ]]; then
    # Keep temp directory itself but skip its contents
    mkdir -p "${TMP_DIR}/runtime/temp"
    continue
  elif [[ "${base_item}" == "data" ]]; then
    mkdir -p "${TMP_DIR}/runtime/data"
    for data_item in "${item}/"*; do
      if ! [[ -e "${data_item}" ]]; then
        continue
      fi
      data_base_item="$(basename "${data_item}")"
      if [[ "${data_base_item}" != "dbStore" ]]; then
        cp -a "${data_item}" "${TMP_DIR}/runtime/data/"
      fi
    done
  else
    cp -a "${item}" "${TMP_DIR}/runtime/"
  fi
done

# Ensure empty runtime/temp directory exists
mkdir -p "${TMP_DIR}/runtime/temp"

# Filter PrivilegeUsers.xml if present
PRIVILEGE_USERS="${TMP_DIR}/runtime/config/PrivilegeUsers.xml"
if [[ -f "${PRIVILEGE_USERS}" ]]; then
  info "Filtering ${PRIVILEGE_USERS} to keep only SYSTEM state and admin users..."

  if which xmlstarlet >/dev/null; then
    xmlstarlet ed -L -d "/Users/User[translate(State, 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ') != 'SYSTEM' and translate(@username, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz') != 'admin' and translate(@userId, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz') != 'admin']" "${PRIVILEGE_USERS}" || fail "Failed to filter PrivilegeUsers.xml with xmlstarlet"
  elif which python3 >/dev/null; then
    python3 -c "
import xml.etree.ElementTree as ET
import sys

tree = ET.parse('${PRIVILEGE_USERS}')
root = tree.getroot()
for user in list(root.findall('User')):
    username = user.get('username', '').lower()
    userid = user.get('userId', '').lower()
    state_elem = user.find('State')
    state = state_elem.text.strip().upper() if state_elem is not None and state_elem.text else ''
    if username != 'admin' and userid != 'admin' and state != 'SYSTEM':
        root.remove(user)
tree.write('${PRIVILEGE_USERS}', encoding='UTF-8', xml_declaration=True)
" || fail "Failed to filter PrivilegeUsers.xml with python3"
  else
    fail "Neither xmlstarlet nor python3 is available to filter PrivilegeUsers.xml!"
  fi
fi

# Ensure target directory exists for output file
OUTPUT_DIR="$(dirname "${OUTPUT_FILE}")"
mkdir -p "${OUTPUT_DIR}"

info "Creating tarball ${OUTPUT_FILE}..."
tar -czf "${OUTPUT_FILE}" -C "${TMP_DIR}" runtime || fail "Failed to create runtime tarball"

info "Runtime archive created successfully at ${OUTPUT_FILE} ($(du -h "${OUTPUT_FILE}" | cut -f1))"
exit 0
