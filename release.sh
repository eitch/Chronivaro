#!/bin/bash
# ==============================================================================
# Chronivaro Release & Publishing Script
# ==============================================================================
# Automates the release process for Chronivaro:
# 1. Determines release version, tag, and next development version
# 2. Updates Maven POM version to release version (e.g. 0.1.0) & commits
# 3. Builds fat-JAR and packages sanitized runtime tarball
# 4. Computes SHA-256 checksums
# 5. Signs release assets with GPG (.asc detached signatures)
# 6. Pushes signed annotated git tags and creates GitHub Release with assets
# 7. Posts announcement toot to Mastodon via REST API hook
# 8. Increments POM version to next minor snapshot (e.g. 0.2.0-SNAPSHOT) & commits
# 9. Supports dry-run / simulation mode (-s / --simulate)
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
SPECIFIED_NEXT_VERSION=""
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
GITHUB_REPO="${GITHUB_REPO:-}"

# GPG configuration (can be provided via env or CLI)
GPG_KEY="${GPG_KEY:-${GPG_KEY_ID:-${SIGNING_KEY:-}}}"

# Mastodon configuration (can be provided via env or CLI)
MASTODON_INSTANCE="${MASTODON_INSTANCE:-${MASTODON_SERVER:-}}"
MASTODON_TOKEN="${MASTODON_ACCESS_TOKEN:-${MASTODON_TOKEN:-}}"
MASTODON_VISIBILITY="${MASTODON_VISIBILITY:-public}"

# ------------------------------------------------------------------------------
# Logging Helpers & Rollback Handlers
# ------------------------------------------------------------------------------
INITIAL_COMMIT_SHA="$(git rev-parse HEAD 2>/dev/null || true)"
CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "develop")"
TAG_EXISTS_LOCALLY=false
TAG_CREATED_LOCALLY=false
TAG_PUSHED_REMOTELY=false
COMMITTED_RELEASE_VERSION=false
COMMITTED_NEXT_VERSION=false
RELEASE_CREATED_ID=""
GH_RELEASE_CREATED=false
ROLLING_BACK=false

function warn() {
  echo 1>&2 -e "\033[1;33m[WARN]\033[0m $*"
}

function info() {
  echo -e "\033[1;34m[INFO]\033[0m $*"
}

function success() {
  echo -e "\033[1;32m[SUCCESS]\033[0m $*"
}

function rollback() {
  if [[ "${ROLLING_BACK}" == "true" ]]; then
    return
  fi
  ROLLING_BACK=true

  if [[ "${TAG_CREATED_LOCALLY}" == "true" || "${TAG_PUSHED_REMOTELY}" == "true" || -n "${RELEASE_CREATED_ID}" || "${GH_RELEASE_CREATED}" == "true" || "${COMMITTED_RELEASE_VERSION}" == "true" || "${COMMITTED_NEXT_VERSION}" == "true" ]]; then
    echo
    warn "Reverting previous release actions due to failure..."

    # 1. Delete GitHub release if created via REST API
    if [[ -n "${RELEASE_CREATED_ID}" && -n "${GITHUB_TOKEN}" && -n "${GITHUB_REPO}" ]]; then
      info "Rolling back GitHub release ID ${RELEASE_CREATED_ID}..."
      curl -s -X DELETE \
        -H "Authorization: Bearer ${GITHUB_TOKEN}" \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        -H "User-Agent: Chronivaro-Release" \
        "https://api.github.com/repos/${GITHUB_REPO}/releases/${RELEASE_CREATED_ID}" >/dev/null 2>&1 || warn "Could not delete GitHub release ID ${RELEASE_CREATED_ID}."
    fi

    # 2. Delete GitHub release if created via gh CLI
    if [[ "${GH_RELEASE_CREATED}" == "true" ]] && which gh >/dev/null 2>&1; then
      info "Rolling back GitHub release '${TAG}' via gh CLI..."
      gh release delete "${TAG}" --repo "${GITHUB_REPO}" --yes --cleanup-tag=false >/dev/null 2>&1 || warn "Could not delete GitHub release '${TAG}' via gh CLI."
    fi

    # 3. Delete remote git tag if pushed by this script
    if [[ "${TAG_PUSHED_REMOTELY}" == "true" ]]; then
      info "Deleting remote git tag '${TAG}' from origin..."
      git push --delete origin "${TAG}" 2>/dev/null || git push origin ":refs/tags/${TAG}" 2>/dev/null || warn "Could not delete remote git tag '${TAG}' from origin."
    fi

    # 4. Delete local git tag if created by this script
    if [[ "${TAG_CREATED_LOCALLY}" == "true" ]]; then
      info "Deleting local git tag '${TAG}'..."
      git tag -d "${TAG}" 2>/dev/null || warn "Could not delete local git tag '${TAG}'."
    fi

    # 5. Revert git commits made by this script
    if [[ -n "${INITIAL_COMMIT_SHA}" && ( "${COMMITTED_RELEASE_VERSION}" == "true" || "${COMMITTED_NEXT_VERSION}" == "true" ) ]]; then
      info "Resetting git working tree to initial commit (${INITIAL_COMMIT_SHA})..."
      git reset --hard "${INITIAL_COMMIT_SHA}" >/dev/null 2>&1 || warn "Could not reset git HEAD to initial commit."
    else
      # Discard any unstaged/uncommitted POM changes
      git checkout -- pom.xml */pom.xml >/dev/null 2>&1 || true
    fi

    info "Rollback complete."
  fi
}

function fail() {
  echo 1>&2 -e "\033[1;31m[ERROR]\033[0m $*"
  rollback
  exit 1
}

trap 'rollback' ERR

# ------------------------------------------------------------------------------
# Usage / Help
# ------------------------------------------------------------------------------
function usage() {
  cat <<EOF

Usage: $(basename "${0}") [options]

Options:
   -h, --help                  Show this help message
   -v, --version <version>     Release version (default: extracted from pom.xml, e.g. 0.1.0)
   -n, --next-version <ver>    Next development version (default: auto-incremented minor snapshot, e.g. 0.2.0-SNAPSHOT)
   -t, --tag <tag>             Git tag name (default: v<version>, e.g. v0.1.0)
   -p, --prev-tag <tag>        Previous git tag for changelog diff (default: auto-detected)
   -c, --changelog <file>      Path to custom release notes markdown file
   -s, -d, --simulate, --dry-run
                               Simulate the release (prints GitHub notes, assets, version updates, and Mastodon toot without publishing)
   -b, --build                 Build project (mvn clean package -DskipTests) before releasing
   -B, --no-build              Do not build project even if JAR is missing
   -m, --mastodon              Enable posting announcement to Mastodon
   -M, --no-mastodon           Disable posting to Mastodon
   --mastodon-instance <url>   Mastodon instance host (e.g. mastodon.social or https://mastodon.social)
   --mastodon-token <token>    Mastodon API access token
   --github-token <token>      GitHub API token (fallback if 'gh' CLI is not authenticated)
   --github-repo <owner/repo>  GitHub repository (default: parsed from git origin remote)
   --gpg-key <key-id>          GPG key ID or email used for signing tags and assets (default: default GPG key)
   --draft                     Create GitHub release as a draft
   --prerelease                Create GitHub release as a prerelease

Environment Variables:
   GITHUB_TOKEN / GH_TOKEN     GitHub Personal Access Token
   GPG_KEY / GPG_KEY_ID        GPG key ID or email used for signing (default: default GPG key)
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
    -n|--next-version)
      SPECIFIED_NEXT_VERSION="$2"
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
    --gpg-key)
      GPG_KEY="$2"
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
RAW_VERSION=""
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
fi

if [[ -z "${SPECIFIED_VERSION}" ]]; then
  if [[ -n "${RAW_VERSION}" ]]; then
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

if [[ -z "${SPECIFIED_NEXT_VERSION}" ]]; then
  NEXT_VERSION="$(python3 -c "
import sys
v = sys.argv[1]
parts = v.split('.')
if len(parts) >= 2 and parts[0].isdigit() and parts[1].isdigit():
    major, minor = int(parts[0]), int(parts[1])
    print(f'{major}.{minor + 1}.0-SNAPSHOT')
else:
    print(f'{v}-SNAPSHOT')
" "${VERSION}" 2>/dev/null || true)"
  if [[ -z "${NEXT_VERSION}" ]]; then
    NEXT_VERSION="${VERSION}-SNAPSHOT"
  fi
else
  NEXT_VERSION="${SPECIFIED_NEXT_VERSION}"
fi

if [[ -z "${SPECIFIED_TAG}" ]]; then
  TAG="v${VERSION}"
else
  TAG="${SPECIFIED_TAG}"
fi

RELEASE_TITLE="Chronivaro ${VERSION}"

# ------------------------------------------------------------------------------
# Detect & Normalize GitHub Repository
# ------------------------------------------------------------------------------
if [[ -n "${GITHUB_REPO}" ]]; then
  GITHUB_REPO="${GITHUB_REPO#https://github.com/}"
  GITHUB_REPO="${GITHUB_REPO#http://github.com/}"
  GITHUB_REPO="${GITHUB_REPO#git@github.com:}"
  GITHUB_REPO="${GITHUB_REPO%.git}"
fi

if [[ -z "${GITHUB_REPO}" ]]; then
  if git remote get-url origin >/dev/null 2>&1; then
    ORIGIN_URL="$(git remote get-url origin)"
    # Match git@github.com:owner/repo.git or https://github.com/owner/repo.git
    GITHUB_REPO="$(echo "${ORIGIN_URL}" | sed -E 's#(git@|https?://)([^:/]+)[:/]([^/]+)/([^/.]+)(\.git)?#\3/\4#')"
  fi
fi

if [[ -z "${GITHUB_REPO}" || "${GITHUB_REPO}" == *"@"* || "${GITHUB_REPO}" != *"/"* ]]; then
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
# Build Artifacts & Prepare Release
# ------------------------------------------------------------------------------
RELEASE_DIR="${SCRIPT_DIR}/target/release"

if [[ "${SIMULATE}" != "true" ]]; then
  # Check working directory cleanliness
  if [[ -n "$(git status --porcelain pom.xml */pom.xml 2>/dev/null)" ]]; then
    fail "Working directory contains uncommitted changes in POM files! Please commit or stash them before releasing."
  fi

  # 1. Update Maven POM to release version (e.g. 0.1.0) and commit
  if [[ "${RAW_VERSION}" != "${VERSION}" ]]; then
    info "Updating Maven POM versions from ${RAW_VERSION} to ${VERSION}..."
    mvn versions:set -DnewVersion="${VERSION}" -DgenerateBackupPoms=false || fail "Failed to set Maven release version ${VERSION}"
    git add pom.xml */pom.xml
    git commit -m "[Project] Release ${VERSION}" || fail "Failed to commit release version ${VERSION}"
    COMMITTED_RELEASE_VERSION=true
    BUILD_PROJECT=true
  fi
fi

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

mkdir -p "${RELEASE_DIR}"

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

# GPG Sign Release Assets
info "Signing release assets with GPG..."
if ! which gpg >/dev/null 2>&1; then
  fail "GPG is required to sign release assets and git tags but was not found!"
fi

GPG_SIGN_CMD=(gpg --batch --yes --armor --detach-sign)
if [[ -n "${GPG_KEY}" ]]; then
  GPG_SIGN_CMD+=(-u "${GPG_KEY}")
fi

"${GPG_SIGN_CMD[@]}" "${RELEASE_JAR}" || fail "Failed to GPG-sign ${RELEASE_JAR}"
"${GPG_SIGN_CMD[@]}" "${RELEASE_TARBALL}" || fail "Failed to GPG-sign ${RELEASE_TARBALL}"
"${GPG_SIGN_CMD[@]}" "${RELEASE_CHECKSUMS}" || fail "Failed to GPG-sign ${RELEASE_CHECKSUMS}"

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
| `chronivaro-0.1.0.jar.asc` | GPG ASCII-armored detached signature |
| `runtime-0.1.0.tar.gz` | Sanitized Strolch runtime directory structure and default templates |
| `runtime-0.1.0.tar.gz.asc` | GPG ASCII-armored detached signature |
| `SHA256SUMS.txt` | SHA-256 verification checksums for all release binaries |
| `SHA256SUMS.txt.asc` | GPG ASCII-armored detached signature |

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
| \`chronivaro-${VERSION}.jar.asc\` | GPG ASCII-armored detached signature |
| \`runtime-${VERSION}.tar.gz\` | Sanitized Strolch runtime distribution archive |
| \`runtime-${VERSION}.tar.gz.asc\` | GPG ASCII-armored detached signature |
| \`SHA256SUMS.txt\` | SHA-256 verification checksums |
| \`SHA256SUMS.txt.asc\` | GPG ASCII-armored detached signature |
EOF
  fi
fi

# ------------------------------------------------------------------------------
# Mastodon Message Preparation
# ------------------------------------------------------------------------------
RELEASE_URL="https://github.com/${GITHUB_REPO}/releases/tag/${TAG}"

if [[ -z "${MASTODON_STATUS:-}" ]]; then
  if [[ "${VERSION}" == "0.1.0" ]]; then
    MASTODON_STATUS="🚀 Chronivaro ${VERSION} has been released!

Chronivaro is an open-source working time & absence tracking system built on Strolch.

Highlights:
• Live timer & daily time recording
• Absence management & vacation journal
• Approvals & period closing
• CSV & PDF report exports
• Standalone fat-JAR & Docker

📦 Downloads & Release Notes:
${RELEASE_URL}

#Chronivaro #Strolch #Java #OpenSource #TimeTracking"
  else
    MASTODON_STATUS="🚀 Chronivaro ${VERSION} has been released!

Chronivaro is an open-source working time, absence tracking, and monthly period closing system.

📦 Release notes & downloads:
${RELEASE_URL}

#Chronivaro #Strolch #Java #OpenSource #TimeTracking"
  fi
fi

# Ensure status length stays within Mastodon's standard 500 character limit
if [[ "${#MASTODON_STATUS}" -gt 500 ]]; then
  warn "Mastodon status length (${#MASTODON_STATUS} chars) exceeds 500 characters limit. It will be truncated."
  MASTODON_STATUS="${MASTODON_STATUS:0:497}..."
fi

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
  info "Current POM Version: ${RAW_VERSION}"
  info "Next Development Version: ${NEXT_VERSION}"
  info "Target Branch: ${CURRENT_BRANCH}"
  info "GitHub Repository: ${GITHUB_REPO}"
  info "Release Title: ${RELEASE_TITLE}"
  info "Release URL: ${RELEASE_URL}"
  echo
  echo "--------------------------------------------------------------------------------"
  echo "📦 RELEASE ASSETS"
  echo "--------------------------------------------------------------------------------"
  (
    cd "${RELEASE_DIR}"
    ls -lh "chronivaro-${VERSION}.jar" "chronivaro-${VERSION}.jar.asc" "runtime-${VERSION}.tar.gz" "runtime-${VERSION}.tar.gz.asc" "SHA256SUMS.txt" "SHA256SUMS.txt.asc"
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
  echo "🔧 MAVEN & GIT VERSIONING ACTIONS (Simulated)"
  echo "--------------------------------------------------------------------------------"
  if [[ "${RAW_VERSION}" != "${VERSION}" ]]; then
    echo "1. Update POM version to release version:"
    echo "   mvn versions:set -DnewVersion=${VERSION} -DgenerateBackupPoms=false"
    echo "2. Commit release version:"
    echo "   git add pom.xml */pom.xml"
    echo "   git commit -m \"[Project] Release ${VERSION}\""
  else
    echo "1. POM version already matches release version ${VERSION} (no release commit needed)."
  fi
  echo "3. Create signed annotated git tag:"
  if [[ -n "${GPG_KEY}" ]]; then
    echo "   git tag -u \"${GPG_KEY}\" -s \"${TAG}\" -m \"${VERSION}\""
  else
    echo "   git tag -s \"${TAG}\" -m \"${VERSION}\""
  fi
  echo "4. Push release commit and tag to remote origin:"
  if [[ "${RAW_VERSION}" != "${VERSION}" ]]; then
    echo "   git push origin \"${CURRENT_BRANCH}\""
  fi
  echo "   git push origin \"${TAG}\""
  echo "5. Update POM version to next development snapshot:"
  echo "   mvn versions:set -DnewVersion=${NEXT_VERSION} -DgenerateBackupPoms=false"
  echo "6. Commit next development snapshot:"
  echo "   git add pom.xml */pom.xml"
  echo "   git commit -m \"[Project] Next development version ${NEXT_VERSION}\""
  echo "7. Push next development snapshot to remote origin:"
  echo "   git push origin \"${CURRENT_BRANCH}\""
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
    echo "      \"${RELEASE_JAR}\" \"${RELEASE_JAR}.asc\" \"${RELEASE_TARBALL}\" \"${RELEASE_TARBALL}.asc\" \"${RELEASE_CHECKSUMS}\" \"${RELEASE_CHECKSUMS}.asc\""
  else
    info "GitHub REST API would be called via curl using GITHUB_TOKEN:"
    echo "   POST https://api.github.com/repos/${GITHUB_REPO}/releases"
    echo "   Upload assets: chronivaro-${VERSION}.jar, chronivaro-${VERSION}.jar.asc, runtime-${VERSION}.tar.gz, runtime-${VERSION}.tar.gz.asc, SHA256SUMS.txt, SHA256SUMS.txt.asc"
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
    echo "Mastodon Status Text (${#MASTODON_STATUS} / 500 characters):"
    echo "---"
    echo "${MASTODON_STATUS}"
    echo "---"
  else
    info "Mastodon hook disabled. (Pass -m / --mastodon and credentials to enable)."
  fi
  echo
  echo "================================================================================"
  success "Simulation complete! No changes were made to pom.xml or pushed to GitHub / Mastodon."
  echo "================================================================================"
  exit 0
fi

# ------------------------------------------------------------------------------
# Real Release Execution
# ------------------------------------------------------------------------------
info "Starting release process for ${APP_NAME} ${VERSION}..."

# 1. Create and Push Git Tag & Release Commit
if git rev-parse "${TAG}" >/dev/null 2>&1; then
  warn "Git tag '${TAG}' already exists locally."
  TAG_EXISTS_LOCALLY=true
  TAG_CREATED_LOCALLY=false
else
  info "Creating signed git tag '${TAG}' annotated with version '${VERSION}'..."
  GIT_TAG_CMD=(git tag -s)
  if [[ -n "${GPG_KEY}" ]]; then
    GIT_TAG_CMD+=(-u "${GPG_KEY}")
  fi
  GIT_TAG_CMD+=("${TAG}" -m "${VERSION}")

  if ! "${GIT_TAG_CMD[@]}"; then
    fail "Failed to create signed git tag '${TAG}'"
  fi
  TAG_CREATED_LOCALLY=true
fi

if [[ "${COMMITTED_RELEASE_VERSION}" == "true" ]]; then
  info "Pushing release commit to origin (${CURRENT_BRANCH})..."
  git push origin "${CURRENT_BRANCH}" || warn "Could not push release commit to origin (${CURRENT_BRANCH})"
fi

if git ls-remote --tags origin "refs/tags/${TAG}" 2>/dev/null | grep -q "${TAG}"; then
  TAG_EXISTS_REMOTELY=true
  TAG_PUSHED_REMOTELY=false
  info "Git tag '${TAG}' already exists on remote origin."
else
  TAG_EXISTS_REMOTELY=false
  info "Pushing git tag '${TAG}' to origin..."
  if git push origin "${TAG}"; then
    TAG_PUSHED_REMOTELY=true
  else
    TAG_PUSHED_REMOTELY=false
    warn "Could not push tag '${TAG}' to remote (it might already exist or remote access requires authentication)."
  fi
fi

# 2. Publish to GitHub Releases
ASSET_FILES=(
  "${RELEASE_JAR}"
  "${RELEASE_JAR}.asc"
  "${RELEASE_TARBALL}"
  "${RELEASE_TARBALL}.asc"
  "${RELEASE_CHECKSUMS}"
  "${RELEASE_CHECKSUMS}.asc"
)

RELEASE_CREATED=false

if which gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  info "Creating release on GitHub using 'gh' CLI..."
  GH_FLAGS=()
  if [[ "${DRAFT}" == "true" ]]; then GH_FLAGS+=("--draft"); fi
  if [[ "${PRERELEASE}" == "true" ]]; then GH_FLAGS+=("--prerelease"); fi

  if ! gh release create "${TAG}" "${ASSET_FILES[@]}" \
    --repo "${GITHUB_REPO}" \
    --title "${RELEASE_TITLE}" \
    --notes-file "${RELEASE_NOTES_FILE}" \
    "${GH_FLAGS[@]}"; then
    fail "Failed to create GitHub release with gh CLI"
  fi
  GH_RELEASE_CREATED=true
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

  API_URL="https://api.github.com/repos/${GITHUB_REPO}/releases"

  CREATE_RESP="$(curl -s -w "\n%{http_code}" -X POST \
    -H "Authorization: Bearer ${GITHUB_TOKEN}" \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    -H "Content-Type: application/json" \
    -H "User-Agent: Chronivaro-Release" \
    "${API_URL}" \
    -d "${RELEASE_JSON_PAYLOAD}")" || fail "Network error while connecting to GitHub API (${API_URL})"

  HTTP_CODE="$(echo "${CREATE_RESP}" | tail -n1)"
  RESP_BODY="$(echo "${CREATE_RESP}" | sed '$d')"

  if [[ "${HTTP_CODE}" != "200" && "${HTTP_CODE}" != "201" ]]; then
    ERROR_MSG="$(echo "${RESP_BODY}" | python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
    msg = data.get('message', '')
    errors = data.get('errors', [])
    err_details = '; '.join([e.get('message', str(e)) if isinstance(e, dict) else str(e) for e in errors])
    if err_details:
        print(f'{msg} ({err_details})')
    else:
        print(msg)
except Exception:
    pass
" 2>/dev/null || true)"

    if [[ -z "${ERROR_MSG}" ]]; then
      ERROR_MSG="${RESP_BODY}"
    fi

    echo
    warn "GitHub API returned HTTP status ${HTTP_CODE} when creating release for repository '${GITHUB_REPO}'."
    if [[ -n "${ERROR_MSG}" ]]; then
      warn "GitHub API Error: ${ERROR_MSG}"
    fi
    if [[ "${HTTP_CODE}" == "401" ]]; then
      warn "Tip: Check that GITHUB_TOKEN is valid and has not expired."
    elif [[ "${HTTP_CODE}" == "404" ]]; then
      warn "Tip: Repository '${GITHUB_REPO}' was not found or GITHUB_TOKEN lacks 'repo' scope."
    elif [[ "${HTTP_CODE}" == "422" ]]; then
      warn "Tip: Validation failed. Release/tag '${TAG}' might already exist on GitHub, or payload was rejected."
    fi
    echo
    fail "Failed to create release via GitHub API (HTTP ${HTTP_CODE})"
  fi

  RELEASE_ID="$(echo "${RESP_BODY}" | python3 -c "import json, sys; print(json.load(sys.stdin)['id'])")"
  RELEASE_CREATED_ID="${RELEASE_ID}"
  info "GitHub Release created with ID: ${RELEASE_ID}"

  for asset in "${ASSET_FILES[@]}"; do
    asset_name="$(basename "${asset}")"
    info "Uploading asset: ${asset_name}..."
    UPLOAD_URL="https://uploads.github.com/repos/${GITHUB_REPO}/releases/${RELEASE_ID}/assets?name=${asset_name}"
    
    UPLOAD_RESP="$(curl -s -w "\n%{http_code}" -X POST \
      -H "Authorization: Bearer ${GITHUB_TOKEN}" \
      -H "Accept: application/vnd.github+json" \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      -H "Content-Type: application/octet-stream" \
      -H "User-Agent: Chronivaro-Release" \
      --data-binary @"${asset}" \
      "${UPLOAD_URL}")" || fail "Network error while uploading asset ${asset_name} to GitHub"

    UPLOAD_HTTP_CODE="$(echo "${UPLOAD_RESP}" | tail -n1)"
    UPLOAD_RESP_BODY="$(echo "${UPLOAD_RESP}" | sed '$d')"

    if [[ "${UPLOAD_HTTP_CODE}" != "200" && "${UPLOAD_HTTP_CODE}" != "201" ]]; then
      UPLOAD_ERROR_MSG="$(echo "${UPLOAD_RESP_BODY}" | python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
    msg = data.get('message', '')
    errors = data.get('errors', [])
    err_details = '; '.join([e.get('message', str(e)) if isinstance(e, dict) else str(e) for e in errors])
    if err_details:
        print(f'{msg} ({err_details})')
    else:
        print(msg)
except Exception:
    pass
" 2>/dev/null || true)"
      if [[ -z "${UPLOAD_ERROR_MSG}" ]]; then
        UPLOAD_ERROR_MSG="${UPLOAD_RESP_BODY}"
      fi
      warn "Failed to upload asset ${asset_name} (HTTP ${UPLOAD_HTTP_CODE}): ${UPLOAD_ERROR_MSG}"
      fail "Failed to upload release asset '${asset_name}' (HTTP ${UPLOAD_HTTP_CODE})"
    else
      success "Asset '${asset_name}' uploaded successfully."
    fi
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
    MASTODON_PAYLOAD="$(MASTODON_STATUS="${MASTODON_STATUS}" MASTODON_VISIBILITY="${MASTODON_VISIBILITY}" python3 -c "
import json, os
payload = {
    'status': os.environ.get('MASTODON_STATUS', ''),
    'visibility': os.environ.get('MASTODON_VISIBILITY', 'public')
}
print(json.dumps(payload))
")"

    MASTODON_RESP="$(curl -s -w "\n%{http_code}" -X POST "${MASTODON_API_URL}" \
      -H "Authorization: Bearer ${MASTODON_TOKEN}" \
      -H "Accept: application/json" \
      -H "Content-Type: application/json" \
      -H "User-Agent: Chronivaro-Release" \
      -d "${MASTODON_PAYLOAD}")"

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

# 4. Increment Maven POM to next development snapshot and commit
info "Updating Maven POM versions to next development snapshot (${NEXT_VERSION})..."
mvn versions:set -DnewVersion="${NEXT_VERSION}" -DgenerateBackupPoms=false || fail "Failed to set next development version ${NEXT_VERSION}"
git add pom.xml */pom.xml
git commit -m "[Project] Next development version ${NEXT_VERSION}" || fail "Failed to commit next development version ${NEXT_VERSION}"
COMMITTED_NEXT_VERSION=true

info "Pushing next development version to origin (${CURRENT_BRANCH})..."
git push origin "${CURRENT_BRANCH}" || warn "Could not push next development version to origin (${CURRENT_BRANCH})"

echo
success "Release ${VERSION} completed successfully!"
info "Current development version is now ${NEXT_VERSION}"
exit 0
