#!/usr/bin/env python3
"""Canned checks for check_release. No network and no git."""

from __future__ import annotations

import json
import unittest

import check_release


class FakeGit:
    def __init__(self, error: str | None = None) -> None:
        self.error = error

    def ancestry_error(self) -> str | None:
        return self.error


class FakeHttp:
    def __init__(self, status: int, body: str) -> None:
        self.status = status
        self.body = body
        self.calls: list[str] = []

    def get(self, path: str) -> tuple[int, str]:
        self.calls.append(path)
        return self.status, self.body


def environment(reviewers: list[dict], *, rules: list[dict] | None = None) -> str:
    if rules is None:
        rules = [
            {
                "type": "required_reviewers",
                "prevent_self_review": False,
                "reviewers": reviewers,
            }
        ]
    return json.dumps({"name": "packages", "protection_rules": rules})


def user(login: str) -> dict:
    return {"type": "User", "reviewer": {"login": login, "id": 1, "type": "User"}}


def team(slug: str) -> dict:
    return {"type": "Team", "reviewer": {"slug": slug, "id": 2, "name": slug}}


class CheckReleaseTest(unittest.TestCase):
    def evaluate(self, tag: str | None, git: FakeGit, http: FakeHttp):
        return check_release.evaluate(tag, git, http)

    def test_missing_tag_unset_and_empty(self) -> None:
        for tag in (None, "", "   "):
            ok, message = self.evaluate(tag, FakeGit(), FakeHttp(200, "{}"))
            self.assertFalse(ok)
            self.assertIn("missing ref", message)

    def test_tag_not_in_full_form(self) -> None:
        for tag in ("0.1", "v0.1.0", "0.1.0-rc1"):
            ok, message = self.evaluate(tag, FakeGit(), FakeHttp(200, "{}"))
            self.assertFalse(ok, tag)
            self.assertIn("MAJOR.MINOR.PATCH", message)
            self.assertIn(tag, message)

    def test_tagged_commit_not_on_main(self) -> None:
        ok, message = self.evaluate(
            "0.1.0",
            FakeGit("tagged commit abc is not an ancestor of origin/main"),
            FakeHttp(200, environment([user("kazetsukaimiko")])),
        )
        self.assertFalse(ok)
        self.assertIn("not an ancestor of origin/main", message)

    def test_api_errors_are_fatal(self) -> None:
        for status in (403, 404, 500):
            ok, message = self.evaluate("0.1.0", FakeGit(), FakeHttp(status, ""))
            self.assertFalse(ok, status)
            self.assertIn(f"HTTP {status}", message)
        self.assertIn("absent", self.evaluate("0.1.0", FakeGit(), FakeHttp(404, "nope"))[1])

    def test_unreadable_response(self) -> None:
        ok, message = self.evaluate("0.1.0", FakeGit(), FakeHttp(200, "not-json"))
        self.assertFalse(ok)
        self.assertIn("cannot be read", message)

    def test_absent_protection_rule(self) -> None:
        body = environment([], rules=[{"type": "wait_timer", "wait_timer": 5}])
        ok, message = self.evaluate("0.1.0", FakeGit(), FakeHttp(200, body))
        self.assertFalse(ok)
        self.assertIn("no required-reviewers", message)
        empty = json.dumps({"protection_rules": []})
        ok, message = self.evaluate("0.1.0", FakeGit(), FakeHttp(200, empty))
        self.assertFalse(ok)
        self.assertIn("no required-reviewers", message)

    def test_other_reviewer_configurations(self) -> None:
        cases = [
            ([user("someone-else")], "not kazetsukaimiko alone"),
            ([user("kazetsukaimiko"), user("someone-else")], "someone other than kazetsukaimiko"),
            ([user("kazetsukaimiko"), team("release")], "include a team"),
        ]
        for reviewers, expected in cases:
            ok, message = self.evaluate(
                "0.1.0", FakeGit(), FakeHttp(200, environment(reviewers))
            )
            self.assertFalse(ok, expected)
            self.assertIn(expected, message)

    def test_pass_is_sole_reviewer_on_main(self) -> None:
        http = FakeHttp(200, environment([user("kazetsukaimiko")]))
        ok, message = self.evaluate("0.1.0", FakeGit(), http)
        self.assertTrue(ok)
        self.assertIn("passed", message)
        self.assertEqual(http.calls, [check_release.ENVIRONMENT_PATH])

    def test_module_commands_name_the_registry_for_every_module(self) -> None:
        lines = check_release.module_commands()
        self.assertEqual(len(lines), 15)
        self.assertEqual([line.split(":", 1)[0] for line in lines], list(check_release.MODULES))
        for line in lines:
            self.assertIn(check_release.REGISTRY_URL, line)
            self.assertIn("-DdeployAtEnd=true", line)
            self.assertIn("-DaltDeploymentRepository=github::", line)
        self.assertEqual(check_release.alt_repository(), f"github::{check_release.REGISTRY_URL}")
        self.assertEqual(
            check_release.alt_repository("/tmp/repo"),
            "github::file:///tmp/repo",
        )


if __name__ == "__main__":
    unittest.main()
