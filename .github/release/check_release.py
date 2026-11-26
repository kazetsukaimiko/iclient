#!/usr/bin/env python3
"""Checks that run before iclient publishes a tag.

The tag, the ancestry of the tagged commit and the packages environment are
decided here. The workflow runs the reactor and the credential-free deploy
after this script exits 0. The GitHub call is injected so the tests stay
off the network.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from typing import Protocol

REGISTRY_URL = "https://maven.pkg.github.com/kazetsukaimiko/iclient"
ENVIRONMENT_PATH = "/repos/kazetsukaimiko/iclient/environments/packages"
TAG_PATTERN = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")
SOLE_REVIEWER = "kazetsukaimiko"
MODULES = (
    "iclient-parent",
    "iclient-bom",
    "iclient-api",
    "iclient-http-api",
    "iclient-http-tck",
    "iclient-validation-api",
    "iclient-jakarta-parent",
    "iclient-jakarta-bom",
    "iclient-cdi-jakarta",
    "iclient-validation-jakarta",
    "iclient-http-microprofile",
    "iclient-javax-parent",
    "iclient-javax-bom",
    "iclient-cdi-javax",
    "iclient-validation-javax",
)


class Http(Protocol):
    def get(self, path: str) -> tuple[int, str]: ...


class Git(Protocol):
    def ancestry_error(self) -> str | None: ...


def deploy_command(url: str = REGISTRY_URL) -> str:
    """The deploy line the publish runs. A local check replaces only the URL."""
    return (
        "./mvnw -B deploy -DdeployAtEnd=true "
        f"-DaltDeploymentRepository=github::{url}"
    )


def module_commands(url: str = REGISTRY_URL) -> list[str]:
    """The same deploy line, named once for each reactor module."""
    command = deploy_command(url)
    return [f"{module}: {command}" for module in MODULES]


def alt_repository(local_repo: str = "") -> str:
    if local_repo:
        return f"github::file://{local_repo}"
    return f"github::{REGISTRY_URL}"


def tag_error(tag: str | None) -> str | None:
    if tag is None or tag.strip() == "":
        return "missing ref: TAG is empty or unset"
    if TAG_PATTERN.fullmatch(tag.strip()) is None:
        return f"tag {tag.strip()} is not MAJOR.MINOR.PATCH"
    return None


def _reviewer_identity(entry: object) -> tuple[str, str] | str:
    """Return ('user', login) or ('team', label), or an error string."""
    if not isinstance(entry, dict):
        return "environment response has a reviewer that cannot be read"
    kind = entry.get("type")
    reviewer = entry.get("reviewer")
    if not isinstance(reviewer, dict):
        return "environment response has a reviewer that cannot be read"
    if kind == "User" and isinstance(reviewer.get("login"), str):
        return ("user", reviewer["login"])
    if kind == "Team":
        label = reviewer.get("slug") or reviewer.get("name") or reviewer.get("id")
        return ("team", str(label))
    return "environment response has a reviewer that cannot be read"


def environment_error(status: int, body: str) -> str | None:
    if status == 404:
        return "environment API error: HTTP 404: packages environment is absent"
    if status < 200 or status >= 300:
        return f"environment API error: HTTP {status}"
    try:
        payload = json.loads(body)
    except json.JSONDecodeError:
        return "environment response cannot be read"
    if not isinstance(payload, dict):
        return "environment response cannot be read"
    rules = payload.get("protection_rules")
    if not isinstance(rules, list):
        return "environment response cannot be read"
    reviewers: list[tuple[str, str]] = []
    saw_required = False
    for rule in rules:
        if not isinstance(rule, dict):
            return "environment response cannot be read"
        if rule.get("type") != "required_reviewers":
            continue
        saw_required = True
        entries = rule.get("reviewers")
        if not isinstance(entries, list):
            return "environment response cannot be read"
        for entry in entries:
            identity = _reviewer_identity(entry)
            if isinstance(identity, str):
                return identity
            reviewers.append(identity)
    if not saw_required:
        return "packages environment has no required-reviewers protection"
    users = [login for kind, login in reviewers if kind == "user"]
    teams = [label for kind, label in reviewers if kind == "team"]
    if teams:
        return (
            "required reviewers include a team, so someone other than "
            f"{SOLE_REVIEWER} can approve alone ({', '.join(teams)})"
        )
    if len(users) == 1 and users[0] == SOLE_REVIEWER:
        return None
    if SOLE_REVIEWER in users:
        others = [login for login in users if login != SOLE_REVIEWER]
        return (
            "required reviewers include someone other than "
            f"{SOLE_REVIEWER} ({', '.join(others)})"
        )
    shown = ", ".join(users) if users else "none"
    return f"required reviewer is {shown}, not {SOLE_REVIEWER} alone"


def evaluate(tag: str | None, git: Git, http: Http) -> tuple[bool, str]:
    """Return (ok, message). The first failing check is the message."""
    refused = tag_error(tag)
    if refused:
        return False, refused
    refused = git.ancestry_error()
    if refused:
        return False, refused
    status, body = http.get(ENVIRONMENT_PATH)
    refused = environment_error(status, body)
    if refused:
        return False, refused
    return True, f"release check passed for tag {tag.strip()}"


class RealGit:
    def ancestry_error(self) -> str | None:
        head = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            check=False,
            capture_output=True,
            text=True,
        )
        if head.returncode != 0 or not head.stdout.strip():
            return "tagged commit is missing"
        commit = head.stdout.strip()
        main = subprocess.run(
            ["git", "rev-parse", "--verify", "origin/main"],
            check=False,
            capture_output=True,
            text=True,
        )
        if main.returncode != 0:
            return "origin/main is missing"
        ancestor = subprocess.run(
            ["git", "merge-base", "--is-ancestor", commit, "origin/main"],
            check=False,
            capture_output=True,
            text=True,
        )
        if ancestor.returncode != 0:
            return f"tagged commit {commit} is not an ancestor of origin/main"
        return None


class RealHttp:
    def get(self, path: str) -> tuple[int, str]:
        token = os.environ.get("GITHUB_TOKEN", "")
        if token.strip() == "":
            return 401, ""
        base = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
        request = urllib.request.Request(
            f"{base}{path}",
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {token}",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.status, response.read().decode()
        except urllib.error.HTTPError as error:
            return error.code, error.read().decode(errors="replace")


def main(argv: list[str]) -> int:
    if "--deploy-command" in argv:
        print(deploy_command())
        return 0
    if "--module-commands" in argv:
        print("\n".join(module_commands()))
        return 0
    if "--alt-repository" in argv:
        print(alt_repository(os.environ.get("LOCAL_REPO", "")))
        return 0
    ok, message = evaluate(os.environ.get("TAG"), RealGit(), RealHttp())
    print(message, file=sys.stderr if not ok else sys.stdout)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
