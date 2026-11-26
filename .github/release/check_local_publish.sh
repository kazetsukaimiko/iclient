#!/usr/bin/env bash
# Deploy the release command at $TAG into a temporary file repository and
# check the artifacts. The registry URL is the only thing that changes.
set -euo pipefail

cd "$(dirname "$0")/../.."

export TAG="${TAG:-0.1.0}"

commands="$(python3 .github/release/check_release.py --module-commands)"
PYTHONPATH=.github/release python3 - "$commands" <<'PY'
import sys
from check_release import MODULES, REGISTRY_URL

text = sys.argv[1]
lines = [line for line in text.splitlines() if line]
if len(lines) != len(MODULES):
    sys.exit(f"release command lines: {len(lines)}, expected {len(MODULES)}")
for module, line in zip(MODULES, lines):
    if not line.startswith(module + ":"):
        sys.exit(f"release command line for {module} is missing")
    if REGISTRY_URL not in line:
        sys.exit(f"release command line for {module} does not name {REGISTRY_URL}")
    if "-DdeployAtEnd=true" not in line or "-DaltDeploymentRepository=github::" not in line:
        sys.exit(f"release command line for {module} is not the publish deploy")
print(f"release command names {REGISTRY_URL} for all {len(MODULES)} modules")
PY

repo="$(mktemp -d)"
log="$(mktemp)"
export LOCAL_REPO="$repo"
bash .github/release/publish_release.sh 2>&1 | tee "$log"
python3 .github/release/verify_local_repo.py --repo "$repo" --version "$TAG" --log "$log"
