#!/bin/bash
# ==============================================================================
# Chronivaro Release & Publishing Script
# ==============================================================================
# Automates the release process for Chronivaro:
# 1. Determines version and tag
# 2. Builds fat-JAR and packages sanitized runtime tarball
# 3. Generates release notes / changelog (with MVP feature list for 0.1.0)
# 4. Computes SHA-256 checksums
# 5. Pushes git tags and creates GitHub Release with assets
# 6. Posts announcement toot to Mastodon via REST API hook
# 7. Supports dry-run / simulation mode (-s / --simulate)
# ==============================================================================

set -eo pipefail

declare SCRIPT_DIR
SCRIPT_DIR="$(
  cd "${0%/*}" || exit
  pwd
)"
cd "${SCRIPT_DIR}" || exit

# ------------------------------------------------------------------------------
# Default Settings
# ------------------------------------------------------------------------------
APP_NAME="Chronivaro"
JAR_SOURCE="chronivaro-app/target/chronivaro.jar"
BUILD_PROJECT=false
SKIP_BUILD=false
SIMULATE=false
DRAFT=false
PRERELEASE=false
ENABLE_MASTODON=false
CUSTOM_CHANGELOG=""
SPECIFIED_VERSION=""
SPECIFIED_TAG=""
PREV_TAG=""

# Load environment configuration if available
ENV_FILE="${HOME}/.config/chronivaro/release.env"
if [[ -f "${ENV_FILE}" ]]; then
  echo "Loading environment file ${ENV_FILE}"
  # shellcheck disable=SC1090
  source "${ENV_FILE}" 2>/dev/null || true
fi

# GitHub configuration (can be provided via env or CLI)
GITHUB_TOKEN="${GITHUB_TOKEN:-${GH_TOKEN:-}}"
GITHUB_REPO="https://github.com/eitch/Chronivaro"

# Mastodon configuration (can be provided via env or CLI)
MASTODON_INSTANCE="${MASTODON_INSTANCE:-${MASTODON_SERVER:-}}"
MASTODON_TOKEN="${MASTODON_ACCESS_TOKEN:-${MASTODON_TOKEN:-}}"
MASTODON_VISIBILITY="${MASTODON_VISIBILITY:-public}"

# ------------------------------------------------------------------------------
# Logging Helpers
# ------------------------------------------------------------------------------
function fail() {
  echo 1>&2 -e "\033[1;31m[ERROR]\033[0m $*"
  exit 1
}

function warn() {
  echo 1>&2 -e "\033[1;33m[WARN]\033[0m $*"
}

function info() {
  echo -e "\033[1;34m[INFO]\033[0m $*"
}

function success() {
  echo -e "\033[1;32m[SUCCESS]\033[0m $*"
}

# ------------------------------------------------------------------------------
# Usage / Help
# ------------------------------------------------------------------------------
function usage() {
  cat <<EOF

Usage: $(basename "${0}") [options]

Options:
   -h, --help                  Show this help message
   -v, --version <version>     Release version (default: extracted from pom.xml, e.g. 0.1.0)
   -t, --tag <tag>             Git tag name (default: v<version>, e.g. v0.1.0)
   -p, --prev-tag <tag>        Previous git tag for changelog diff (default: auto-detected)
   -c, --changelog <file>      Path to custom release notes markdown file
   -s, -d, --simulate, --dry-run
                               Simulate the release (prints GitHub notes, assets, and Mastodon toot without publishing)
   -b, --build                 Build project (mvn clean package -DskipTests) before releasing
   -B, --no-build              Do not build project even if JAR is missing
   -m, --mastodon              Enable posting announcement to Mastodon
   -M, --no-mastodon           Disable posting to Mastodon
   --mastodon-instance <url>   Mastodon instance host (e.g. mastodon.social or https://mastodon.social)
   --mastodon-token <token>    Mastodon API access token
   --github-token <token>      GitHub API token (fallback if 'gh' CLI is not authenticated)
   --github-repo <owner/repo>  GitHub repository (default: parsed from git origin remote)
   --draft                     Create GitHub release as a draft
   --prerelease                Create GitHub release as a prerelease

Environment Variables:
   GITHUB_TOKEN / GH_TOKEN     GitHub Personal Access Token
   MASTODON_INSTANCE           Mastodon instance hostname/URL (e.g. mastodon.social)
   MASTODON_ACCESS_TOKEN       Mastodon API bearer token
   MASTODON_VISIBILITY         Post visibility: public (default), unlisted, private

Environment File:
   \${HOME}/.config/chronivaro/release.env
                               Configuration file automatically loaded if present
                               (can define any of the environment variables above)

Examples:
   # Simulate 0.1.0 release (dry-run):
   ./release.sh --simulate

   # Simulate with Mastodon post preview:
   ./release.sh --simulate --mastodon --mastodon-instance mastodon.social --mastodon-token xyz

   # Perform actual release for 0.1.0:
   ./release.sh -v 0.1.0 -b

   # Release and publish to Mastodon:
   ./release.sh -v 0.1.0 -m

EOF
  exit 0
}

# ------------------------------------------------------------------------------
# Parse Command-Line Arguments
# ------------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      ;;
    -v|--version)
      SPECIFIED_VERSION="$2"
      shift 2
      ;;
    -t|--tag)
      SPECIFIED_TAG="$2"
      shift 2
      ;;
    -p|--prev-tag)
      PREV_TAG="$2"
      shift 2
      ;;
    -c|--changelog)
      CUSTOM_CHANGELOG="$2"
      shift 2
      ;;
    -s|-d|--simulate|--dry-run)
      SIMULATE=true
      shift
      ;;
    -b|--build)
      BUILD_PROJECT=true
      shift
      ;;
    -B|--no-build)
      SKIP_BUILD=true
      shift
      ;;
    -m|--mastodon)
      ENABLE_MASTODON=true
      shift
      ;;
    -M|--no-mastodon)
      ENABLE_MASTODON=false
      shift
      ;;
    --mastodon-instance)
      MASTODON_INSTANCE="$2"
      ENABLE_MASTODON=true
      shift 2
      ;;
    --mastodon-token)
      MASTODON_TOKEN="$2"
      ENABLE_MASTODON=true
      shift 2
      ;;
    --github-token)
      GITHUB_TOKEN="$2"
      shift 2
      ;;
    --github-repo)
      GITHUB_REPO="$2"
      shift 2
      ;;
    --draft)
      DRAFT=true
      shift
      ;;
    --prerelease)
      PRERELEASE=true
      shift
      ;;
    *)
      fail "Unknown option: $1 (use -h for help)"
      ;;
  esac
done

# If Mastodon credentials are fully configured and user didn't explicitly pass -M, enable Mastodon
if [[ -n "${MASTODON_INSTANCE}" && -n "${MASTODON_TOKEN}" && "${ENABLE_MASTODON}" != "false" ]]; then
  ENABLE_MASTODON=true
fi

# ------------------------------------------------------------------------------
# Version & Tag Resolution
# ------------------------------------------------------------------------------
if [[ -z "${SPECIFIED_VERSION}" ]]; then
  if [[ -f "pom.xml" ]]; then
    if which xmlstarlet >/dev/null 2>&1; then
      RAW_VERSION="$(xmlstarlet sel -t -m _:project -v _:version -n pom.xml 2>/dev/null || true)"
    fi
    if [[ -z "${RAW_VERSION}" && -x "$(command -v python3)" ]]; then
      RAW_VERSION="$(python3 -c "
import xml.etree.ElementTree as ET
try:
    tree = ET.parse('pom.xml')
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    v = tree.find('m:version', ns)
    if v is not None and v.text:
        print(v.text.strip())
except Exception:
    pass
" 2>/dev/null || true)"
    fi
    if [[ -z "${RAW_VERSION}" ]]; then
      RAW_VERSION="$(grep -m 1 "<version>" pom.xml | sed -e 's/.*<version>//' -e 's/<\/version>.*//' | tr -d ' ')"
    fi
    VERSION="${RAW_VERSION%-SNAPSHOT}"
  else
    fail "pom.xml not found and no version specified via -v/--version"
  fi
else
  VERSION="${SPECIFIED_VERSION#v}"
fi

if [[ -z "${VERSION}" ]]; then
  fail "Could not determine release version!"
fi

if [[ -z "${SPECIFIED_TAG}" ]]; then
  TAG="v${VERSION}"
else
  TAG="${SPECIFIED_TAG}"
fi

RELEASE_TITLE="Chronivaro ${VERSION}"

# ------------------------------------------------------------------------------
# Detect GitHub Repository
# ------------------------------------------------------------------------------
if [[ -z "${GITHUB_REPO}" ]]; then
  if git remote get-url origin >/dev/null 2>&1; then
    ORIGIN_URL="$(git remote get-url origin)"
    # Match git@github.com:owner/repo.git or https://github.com/owner/repo.git
    GITHUB_REPO="$(echo "${ORIGIN_URL}" | sed -E 's#(git@|https://)([^:/]+)[:/]([^/]+)/([^/.]+)(\.git)?#\3/\4#')"
  fi
fi
if [[ -z "${GITHUB_REPO}" || "${GITHUB_REPO}" == *"@"* ]]; then
  GITHUB_REPO="eitch/Chronivaro"
fi

# Clean Mastodon instance host
if [[ -n "${MASTODON_INSTANCE}" ]]; then
  MASTODON_HOST="${MASTODON_INSTANCE#http://}"
  MASTODON_HOST="${MASTODON_HOST#https://}"
  MASTODON_HOST="${MASTODON_HOST%/}"
  MASTODON_API_URL="https://${MASTODON_HOST}/api/v1/statuses"
else
  MASTODON_HOST=""
  MASTODON_API_URL=""
fi

# ------------------------------------------------------------------------------
# Build Artifacts
# ------------------------------------------------------------------------------
RELEASE_DIR="${SCRIPT_DIR}/target/release"
mkdir -p "${RELEASE_DIR}"

if [[ "${SKIP_BUILD}" != "true" ]]; then
  if [[ "${BUILD_PROJECT}" == "true" || ! -f "${JAR_SOURCE}" ]]; then
    info "Building project with Maven (clean package -DskipTests)..."
    mvn clean package -DskipTests || fail "Maven build failed!"
  fi
fi

if [[ ! -f "${JAR_SOURCE}" ]]; then
  fail "Fat-JAR not found at ${JAR_SOURCE}! Run with -b to build first."
fi

RELEASE_JAR="${RELEASE_DIR}/chronivaro-${VERSION}.jar"
RELEASE_TARBALL="${RELEASE_DIR}/runtime-${VERSION}.tar.gz"
RELEASE_CHECKSUMS="${RELEASE_DIR}/SHA256SUMS.txt"
RELEASE_NOTES_FILE="${RELEASE_DIR}/RELEASE_NOTES.md"

info "Staging release artifacts in ${RELEASE_DIR}..."
cp -f "${JAR_SOURCE}" "${RELEASE_JAR}"

# Build runtime tarball
info "Packaging sanitized runtime tarball..."
./build-runtime-tarball.sh -s runtime -o "${RELEASE_TARBALL}" >/dev/null || fail "Failed to generate runtime tarball!"

# Compute SHA-256 Checksums
info "Computing SHA-256 checksums..."
(
  cd "${RELEASE_DIR}"
  sha256sum "chronivaro-${VERSION}.jar" "runtime-${VERSION}.tar.gz" > "SHA256SUMS.txt"
)

# ------------------------------------------------------------------------------
# Changelog / Release Notes Generation
# ------------------------------------------------------------------------------
if [[ -n "${CUSTOM_CHANGELOG}" && -f "${CUSTOM_CHANGELOG}" ]]; then
  info "Using custom changelog from ${CUSTOM_CHANGELOG}..."
  cp -f "${CUSTOM_CHANGELOG}" "${RELEASE_NOTES_FILE}"
else
  # Auto-detect previous tag if not provided
  if [[ -z "${PREV_TAG}" ]]; then
    PREV_TAG="$(git describe --tags --abbrev=0 2>/dev/null || git tag --sort=-v:refname 2>/dev/null | grep -v "^${TAG}$" | head -n 1 || true)"
  fi

  if [[ -z "${PREV_TAG}" || "${VERSION}" == "0.1.0" || "${TAG}" == "v0.1.0" ]]; then
    info "Generating initial MVP release notes for version ${VERSION}..."
    cat > "${RELEASE_NOTES_FILE}" <<'EOF'
## Chronivaro 0.1.0 — Initial MVP Release

We are excited to announce the initial release (**0.1.0**) of **Chronivaro**, a lightweight, high-performance working time, absence management, and monthly period closing platform built on the [Strolch](https://strolch.li) framework.

Chronivaro is packaged as a standalone fat-JAR with an embedded Eclipse Jetty 12 runtime, serving both modern Web Component frontend assets and REST API endpoints out-of-the-box.

---

### ✨ Key Features & Capabilities

- ⏱️ **Time Tracking & Live Timer**
  - Real-time start/stop timer with optional persistent activity comments.
  - Manual work entry creation, inline editing, and deletion for open periods.
  - Multi-interval daily tracking with automatic break calculation from time gaps.
  - Automatic overnight shift handling (splitting across midnight 24:00 boundary).
  - Forgotten timer auto-capping to daily target schedule duration.
  - Morning and afternoon working location tagging (Office, Home Office, Customer Site).

- 🌴 **Absence & Vacation Management**
  - Preconfigured 10 absence types (*Vacation, Illness, Accident, Military / Civil Defense, Doctor Appointment, Training, Parental Leave, Unpaid Leave, Overtime Compensation, Other*).
  - Flexible absence duration units (*Full-Day, Half-Day Morning/Afternoon, Custom Hours*).
  - Immutable vacation accounting journal with upfront entitlement, pro-rated entry calculation, automated carry-over, and FIFO consumption.
  - Real-time balance progression and negative-balance prevention safeguards.

- ✅ **Supervisor & HR Approval Workflows**
  - Dedicated approval queues for team supervisors and HR managers.
  - Absence request reviews with mandatory rejection reasoning and optimistic concurrency checks.
  - Monthly period closing submission, calculation snapshot generation, supervisor sign-off, and HR locking.

- 📊 **Reporting & Export Engine**
  - Personal summaries, monthly balance histories, vacation account journals, and team performance overviews.
  - Filtered absence reports with UTF-8 BOM RFC 4180 CSV export for Microsoft Excel.
  - Native server-side OpenPDF monthly timesheet report generation.

- 👥 **Master Data & Tenant Administration**
  - Comprehensive management for Employees, Teams, Locations, Holiday Calendars, Work Schedules, and Absence Types.
  - Role-based access control (*Employee, Supervisor, HR, Administrator, StrolchAdmin*).
  - Non-destructive user deletion with soft employee deactivation and full reactivation workflows.
  - Self-service user challenge registration and secure password management.

- 🔒 **Security, Observability & Auditing**
  - Immutable, append-only audit trail recording all entity lifecycle events, approvals, and configuration changes.
  - Audit log inspection UI with multi-field filtering and before/after diff views.
  - Unauthenticated system health probes (`/health`, `/readiness`, `/version`, `/metrics`).
  - Structured logging with correlation ID tracing (`X-Correlation-Id`).

- 🚀 **Deployment & Distribution**
  - Standalone executable fat-JAR with embedded Jetty 12.
  - Clean, sanitized runtime environment distribution tarball.
  - Docker container image and `docker-compose` orchestration support.
  - Multilingual UI support with full German (Swiss German) and English translations.

---

### 📦 Distribution Artifacts

| File | Description |
|---|---|
| `chronivaro-0.1.0.jar` | Standalone executable fat-JAR with embedded Jetty 12 |
| `runtime-0.1.0.tar.gz` | Sanitized Strolch runtime directory structure and default templates |
| `SHA256SUMS.txt` | SHA-256 verification checksums for all release binaries |

### ⚡ Quick Start

```bash
# Extract runtime environment
tar -xzf runtime-0.1.0.tar.gz

# Run standalone application
java -jar chronivaro-0.1.0.jar --port 8080 --runtime ./runtime --env dev
```

Open `http://localhost:8080` in your browser and log in with `admin` / `admin`.
EOF
  else
    info "Generating changelog diff against previous tag ${PREV_TAG}..."
    cat > "${RELEASE_NOTES_FILE}" <<EOF
## Chronivaro ${VERSION}

### Changes since ${PREV_TAG}

$(git log "${PREV_TAG}..HEAD" --pretty=format:"* %s (%h)" --no-merges)

---

### 📦 Distribution Artifacts

| File | Description |
|---|---|
| \`chronivaro-${VERSION}.jar\` | Standalone executable fat-JAR |
| \`runtime-${VERSION}.tar.gz\` | Sanitized Strolch runtime distribution archive |
| \`SHA256SUMS.txt\` | SHA-256 verification checksums |
EOF
  fi
fi

# ------------------------------------------------------------------------------
# Mastodon Message Preparation
# ------------------------------------------------------------------------------
RELEASE_URL="https://github.com/${GITHUB_REPO}/releases/tag/${TAG}"

MASTODON_STATUS="🚀 Chronivaro ${VERSION} has been released!

Chronivaro is a modern open-source working time, absence management, and monthly period closing system built on the Strolch framework.

Key highlights:
• Live timer & multi-interval daily time recording
• Absence management & immutable vacation journal
• Supervisor approval workflows & period closing
• RFC 4180 CSV & PDF report exports
• Standalone fat-JAR & Docker distribution

📦 Release notes & downloads:
${RELEASE_URL}

#Chronivaro #Strolch #Java #OpenSource #TimeTracking #SelfHosted"

# ------------------------------------------------------------------------------
# Simulation Mode (Dry-Run)
# ------------------------------------------------------------------------------
if [[ "${SIMULATE}" == "true" ]]; then
  echo
  echo "================================================================================"
  echo "                      CHRONIVARO RELEASE SIMULATION (DRY-RUN)                   "
  echo "================================================================================"
  echo
  info "Release Target: ${APP_NAME} ${VERSION} (Tag: ${TAG})"
  info "GitHub Repository: ${GITHUB_REPO}"
  info "Release Title: ${RELEASE_TITLE}"
  info "Release URL: ${RELEASE_URL}"
  echo
  echo "--------------------------------------------------------------------------------"
  echo "📦 RELEASE ASSETS"
  echo "--------------------------------------------------------------------------------"
  (
    cd "${RELEASE_DIR}"
    ls -lh "chronivaro-${VERSION}.jar" "runtime-${VERSION}.tar.gz" "SHA256SUMS.txt"
  )
  echo
  echo "Checksums (SHA-256):"
  cat "${RELEASE_CHECKSUMS}"
  echo
  echo "--------------------------------------------------------------------------------"
  echo "📝 GITHUB RELEASE NOTES / CHANGELOG"
  echo "--------------------------------------------------------------------------------"
  cat "${RELEASE_NOTES_FILE}"
  echo
  echo "--------------------------------------------------------------------------------"
  echo "🐙 GITHUB PUBLISHING ACTIONS (Simulated)"
  echo "--------------------------------------------------------------------------------"
  if which gh >/dev/null 2>&1; then
    info "GitHub CLI (gh) detected. Release command that would execute:"
    echo "   gh release create \"${TAG}\" \\"
    echo "      --repo \"${GITHUB_REPO}\" \\"
    echo "      --title \"${RELEASE_TITLE}\" \\"
    echo "      --notes-file \"${RELEASE_NOTES_FILE}\" \\"
    echo "      \"${RELEASE_JAR}\" \"${RELEASE_TARBALL}\" \"${RELEASE_CHECKSUMS}\""
  else
    info "GitHub REST API would be called via curl using GITHUB_TOKEN:"
    echo "   POST https://api.github.com/repos/${GITHUB_REPO}/releases"
    echo "   Upload assets: chronivaro-${VERSION}.jar, runtime-${VERSION}.tar.gz, SHA256SUMS.txt"
  fi
  echo
  echo "--------------------------------------------------------------------------------"
  echo "🐘 MASTODON HOOK (Simulated)"
  echo "--------------------------------------------------------------------------------"
  if [[ "${ENABLE_MASTODON}" == "true" ]]; then
    if [[ -n "${MASTODON_HOST}" ]]; then
      info "Target Mastodon Server: https://${MASTODON_HOST}"
      info "API Endpoint: ${MASTODON_API_URL}"
      info "Visibility: ${MASTODON_VISIBILITY}"
      info "Authentication: Bearer Token (${#MASTODON_TOKEN} characters configured)"
    else
      warn "Mastodon is enabled (-m), but no server/token configured (set MASTODON_INSTANCE & MASTODON_TOKEN or use --mastodon-instance / --mastodon-token)"
    fi
    echo
    echo "Mastodon Status Text:"
    echo "---"
    echo "${MASTODON_STATUS}"
    echo "---"
  else
    info "Mastodon hook disabled. (Pass -m / --mastodon and credentials to enable)."
  fi
  echo
  echo "--------------------------------------------------------------------------------"
  echo "🏷️ GIT TAGGING ACTIONS (Simulated)"
  echo "--------------------------------------------------------------------------------"
  echo "   git tag -a \"${TAG}\" -m \"Release ${TAG}\""
  echo "   git push origin \"${TAG}\""
  echo
  echo "================================================================================"
  success "Simulation complete! No changes were pushed to GitHub or Mastodon."
  echo "================================================================================"
  exit 0
fi

# ------------------------------------------------------------------------------
# Real Release Execution
# ------------------------------------------------------------------------------
info "Starting release process for ${APP_NAME} ${VERSION}..."

# 1. Create and Push Git Tag
if git rev-parse "${TAG}" >/dev/null 2>&1; then
  warn "Git tag '${TAG}' already exists locally."
else
  info "Creating git tag '${TAG}'..."
  git tag -a "${TAG}" -m "Release ${TAG}" || fail "Failed to create git tag '${TAG}'"
fi

info "Pushing git tag '${TAG}' to origin..."
git push origin "${TAG}" || warn "Could not push tag '${TAG}' to remote (it might already exist or remote access requires authentication)."

# 2. Publish to GitHub Releases
ASSET_FILES=(
  "${RELEASE_JAR}"
  "${RELEASE_TARBALL}"
  "${RELEASE_CHECKSUMS}"
)

RELEASE_CREATED=false

if which gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  info "Creating release on GitHub using 'gh' CLI..."
  GH_FLAGS=()
  if [[ "${DRAFT}" == "true" ]]; then GH_FLAGS+=("--draft"); fi
  if [[ "${PRERELEASE}" == "true" ]]; then GH_FLAGS+=("--prerelease"); fi

  gh release create "${TAG}" "${ASSET_FILES[@]}" \
    --repo "${GITHUB_REPO}" \
    --title "${RELEASE_TITLE}" \
    --notes-file "${RELEASE_NOTES_FILE}" \
    "${GH_FLAGS[@]}" || fail "Failed to create GitHub release with gh CLI"
  RELEASE_CREATED=true
elif [[ -n "${GITHUB_TOKEN}" ]]; then
  info "Creating release on GitHub using REST API..."
  RELEASE_JSON_PAYLOAD="$(RELEASE_NOTES_FILE="${RELEASE_NOTES_FILE}" TAG="${TAG}" RELEASE_TITLE="${RELEASE_TITLE}" DRAFT="${DRAFT}" PRERELEASE="${PRERELEASE}" python3 -c "
import json, os, sys
body_file = os.environ.get('RELEASE_NOTES_FILE', '')
with open(body_file, 'r', encoding='utf-8') as f:
    body = f.read()
payload = {
    'tag_name': os.environ.get('TAG'),
    'name': os.environ.get('RELEASE_TITLE'),
    'body': body,
    'draft': os.environ.get('DRAFT') == 'true',
    'prerelease': os.environ.get('PRERELEASE') == 'true'
}
print(json.dumps(payload))
")"

  CREATE_RESP="$(curl -s -f -X POST \
    -H "Authorization: token ${GITHUB_TOKEN}" \
    -H "Accept: application/vnd.github+json" \
    -H "Content-Type: application/json" \
    "https://api.github.com/repos/${GITHUB_REPO}/releases" \
    -d "${RELEASE_JSON_PAYLOAD}")" || fail "Failed to create release via GitHub API"

  RELEASE_ID="$(echo "${CREATE_RESP}" | python3 -c "import json, sys; print(json.load(sys.stdin)['id'])")"
  info "GitHub Release created with ID: ${RELEASE_ID}"

  for asset in "${ASSET_FILES[@]}"; do
    asset_name="$(basename "${asset}")"
    info "Uploading asset: ${asset_name}..."
    curl -s -f -X POST \
      -H "Authorization: token ${GITHUB_TOKEN}" \
      -H "Content-Type: application/octet-stream" \
      --data-binary @"${asset}" \
      "https://uploads.github.com/repos/${GITHUB_REPO}/releases/${RELEASE_ID}/assets?name=${asset_name}" || warn "Failed to upload asset ${asset_name}"
  done
  RELEASE_CREATED=true
else
  fail "Neither authenticated 'gh' CLI nor GITHUB_TOKEN found! Install/authenticate 'gh' or set GITHUB_TOKEN."
fi

if [[ "${RELEASE_CREATED}" == "true" ]]; then
  success "GitHub release ${TAG} published successfully: ${RELEASE_URL}"
fi

# 3. Post Announcement to Mastodon
if [[ "${ENABLE_MASTODON}" == "true" ]]; then
  if [[ -z "${MASTODON_HOST}" || -z "${MASTODON_TOKEN}" ]]; then
    warn "Mastodon post skipped: MASTODON_INSTANCE and MASTODON_TOKEN must be set."
  else
    info "Posting release announcement to Mastodon (${MASTODON_HOST})..."
    MASTODON_RESP="$(MASTODON_STATUS="${MASTODON_STATUS}" MASTODON_VISIBILITY="${MASTODON_VISIBILITY}" curl -s -w "\n%{http_code}" -X POST "${MASTODON_API_URL}" \
      -H "Authorization: Bearer ${MASTODON_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "$(python3 -c "
import json, os
payload = {
    'status': os.environ.get('MASTODON_STATUS', ''),
    'visibility': os.environ.get('MASTODON_VISIBILITY', 'public')
}
print(json.dumps(payload))
")")"

    HTTP_CODE="$(echo "${MASTODON_RESP}" | tail -n1)"
    BODY="$(echo "${MASTODON_RESP}" | sed '$d')"

    if [[ "${HTTP_CODE}" == "200" || "${HTTP_CODE}" == "201" ]]; then
      TOOT_URL="$(echo "${BODY}" | python3 -c "import json, sys; print(json.load(sys.stdin).get('url', ''))" 2>/dev/null || true)"
      success "Posted announcement to Mastodon successfully! ${TOOT_URL}"
    else
      warn "Failed to post to Mastodon (HTTP ${HTTP_CODE}): ${BODY}"
    fi
  fi
fi

echo
success "Release ${VERSION} completed successfully!"
exit 0
