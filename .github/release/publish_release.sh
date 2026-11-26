#!/usr/bin/env bash
# Set the reactor to $TAG and deploy it.
# LOCAL_REPO swaps only the deploy URL for a file:// repository. The command
# otherwise matches the registry publish: deployAtEnd and repository id github.
set -euo pipefail

cd "$(dirname "$0")/../.."

tag="${TAG:-}"
if [[ -z "${tag// }" ]]; then
  echo "missing ref: TAG is empty or unset" >&2
  exit 1
fi
if [[ ! "$tag" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "tag ${tag} is not MAJOR.MINOR.PATCH" >&2
  exit 1
fi

if [[ -n "${LOCAL_REPO:-}" ]]; then
  case "$LOCAL_REPO" in
    /*) ;;
    *)
      echo "LOCAL_REPO must be an absolute path" >&2
      exit 1
      ;;
  esac
  mkdir -p "$LOCAL_REPO"
fi

alt="$(LOCAL_REPO="${LOCAL_REPO:-}" python3 .github/release/check_release.py --alt-repository)"

./mvnw -B org.codehaus.mojo:versions-maven-plugin:2.18.0:set \
  -DnewVersion="${tag}" \
  -DprocessAllModules=true \
  -DgenerateBackupPoms=false

# One reactor command. check_release.deploy_command is this line; the local
# check substitutes the URL inside alt and leaves every other token as it is.
./mvnw -B deploy -DdeployAtEnd=true -DaltDeploymentRepository="${alt}"
