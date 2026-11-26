#!/usr/bin/env python3
"""Assert a file:// deploy of the iclient reactor.

Versions come from the deployed models and from Maven resolving the three
BOMs, because the jakarta and javax BOMs keep ${project.version} on the
iclient-bom import.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import tempfile
import textwrap
import xml.etree.ElementTree as ET
from pathlib import Path

GROUP = "io.freedriver.iclient"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
PARENTLESS = {
    "iclient-parent",
    "iclient-bom",
    "iclient-jakarta-bom",
    "iclient-javax-bom",
}
PARENTS = {
    "iclient-api": "iclient-parent",
    "iclient-http-api": "iclient-parent",
    "iclient-http-tck": "iclient-parent",
    "iclient-validation-api": "iclient-parent",
    "iclient-jakarta-parent": "iclient-parent",
    "iclient-javax-parent": "iclient-parent",
    "iclient-cdi-jakarta": "iclient-jakarta-parent",
    "iclient-validation-jakarta": "iclient-jakarta-parent",
    "iclient-http-microprofile": "iclient-jakarta-parent",
    "iclient-cdi-javax": "iclient-javax-parent",
    "iclient-validation-javax": "iclient-javax-parent",
}
BOM_MANAGED = {
    "iclient-bom": [
        "iclient-api",
        "iclient-http-api",
        "iclient-http-tck",
        "iclient-validation-api",
    ],
    "iclient-jakarta-bom": [
        "iclient-api",
        "iclient-http-api",
        "iclient-http-tck",
        "iclient-validation-api",
        "iclient-cdi-jakarta",
        "iclient-validation-jakarta",
        "iclient-http-microprofile",
    ],
    "iclient-javax-bom": [
        "iclient-api",
        "iclient-http-api",
        "iclient-http-tck",
        "iclient-validation-api",
        "iclient-cdi-javax",
        "iclient-validation-javax",
    ],
}
DEPLOY_PLUGIN = re.compile(r"--- (?:maven-deploy-plugin:)?deploy:([^:\s]+):deploy ")


def fail(message: str) -> None:
    sys.exit(message)


def child_text(element: ET.Element, name: str) -> str | None:
    found = element.find(f"m:{name}", NS)
    if found is None or found.text is None:
        return None
    return found.text.strip()


def pom_path(repo: Path, artifact: str, version: str) -> Path:
    return repo / "io" / "freedriver" / "iclient" / artifact / version / f"{artifact}-{version}.pom"


def check_deployed_poms(repo: Path, version: str) -> None:
    artifacts = list(PARENTLESS) + list(PARENTS)
    if len(artifacts) != 15:
        fail(f"module list has {len(artifacts)} entries")
    for artifact in artifacts:
        path = pom_path(repo, artifact, version)
        if not path.is_file():
            fail(f"missing deployed pom {path}")
        tree = ET.parse(path)
        root = tree.getroot()
        parent = root.find("m:parent", NS)
        own = child_text(root, "version")
        if artifact in PARENTLESS:
            if parent is not None:
                fail(f"{artifact} declares a parent")
            if own != version:
                fail(f"{artifact} version is {own}, expected {version}")
        else:
            if parent is None:
                fail(f"{artifact} has no parent")
            parent_artifact = child_text(parent, "artifactId")
            parent_version = child_text(parent, "version")
            if parent_artifact != PARENTS[artifact] or parent_version != version:
                fail(
                    f"{artifact} parent is {parent_artifact}:{parent_version}, "
                    f"expected {PARENTS[artifact]}:{version}"
                )
            # A module that omits <version> inherits it from the parent.
            resolved = own if own is not None else parent_version
            if resolved != version:
                fail(f"{artifact} version is {resolved}, expected {version}")
        packaging = child_text(root, "packaging") or "jar"
        if packaging == "jar":
            jar = path.with_suffix(".jar")
            # pom path ends in .pom; the jar sits beside it with .jar
            jar = path.parent / f"{artifact}-{version}.jar"
            if not jar.is_file():
                fail(f"missing deployed jar {jar}")
    import_at_property = re.compile(
        r"<artifactId>\s*iclient-bom\s*</artifactId>\s*<version>\s*\$\{project\.version\}\s*</version>"
    )
    for bom in ("iclient-jakarta-bom", "iclient-javax-bom"):
        text = pom_path(repo, bom, version).read_text()
        if import_at_property.search(text) is None:
            fail(f"{bom} no longer imports iclient-bom at ${{project.version}}")


def check_deploy_plugin(log_text: str) -> None:
    found = DEPLOY_PLUGIN.findall(log_text)
    if len(found) != 15:
        fail(f"maven-deploy-plugin deploy executions: {len(found)}, expected 15 ({found})")
    if len(set(found)) != 1:
        fail(f"maven-deploy-plugin versions differ: {sorted(set(found))}")
    print(f"all 15 modules used maven-deploy-plugin {found[0]}")


def managed_versions(effective_pom: Path) -> dict[str, str]:
    tree = ET.parse(effective_pom)
    root = tree.getroot()
    found: dict[str, str] = {}
    for dep in root.findall(".//m:dependencyManagement/m:dependencies/m:dependency", NS):
        group = child_text(dep, "groupId")
        artifact = child_text(dep, "artifactId")
        version = child_text(dep, "version")
        if group == GROUP and artifact and version:
            found[artifact] = version
    return found


def check_bom_resolution(repo: Path, version: str) -> None:
    root = Path(__file__).resolve().parents[2]
    mvnw = root / "mvnw"
    for bom, expected in BOM_MANAGED.items():
        with tempfile.TemporaryDirectory() as work:
            consumer = Path(work) / "pom.xml"
            deps = "\n".join(
                textwrap.dedent(
                    f"""
                    <dependency>
                      <groupId>{GROUP}</groupId>
                      <artifactId>{artifact}</artifactId>
                    </dependency>
                    """
                ).strip()
                for artifact in expected
            )
            consumer.write_text(
                textwrap.dedent(
                    f"""
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>io.freedriver.iclient.check</groupId>
                      <artifactId>bom-import-check</artifactId>
                      <version>1</version>
                      <dependencyManagement>
                        <dependencies>
                          <dependency>
                            <groupId>{GROUP}</groupId>
                            <artifactId>{bom}</artifactId>
                            <version>{version}</version>
                            <type>pom</type>
                            <scope>import</scope>
                          </dependency>
                        </dependencies>
                      </dependencyManagement>
                      <dependencies>
                        {deps}
                      </dependencies>
                      <repositories>
                        <repository>
                          <id>published</id>
                          <url>file://{repo}</url>
                          <releases><enabled>true</enabled></releases>
                          <snapshots><enabled>false</enabled></snapshots>
                        </repository>
                      </repositories>
                    </project>
                    """
                ).strip()
            )
            local = Path(work) / "m2"
            effective = Path(work) / "effective.xml"
            subprocess.run(
                [
                    str(mvnw),
                    "-B",
                    f"-Dmaven.repo.local={local}",
                    "-f",
                    str(consumer),
                    "dependency:resolve",
                    "help:effective-pom",
                    f"-Doutput={effective}",
                ],
                check=True,
                cwd=root,
            )
            resolved = managed_versions(effective)
            for artifact in expected:
                if resolved.get(artifact) != version:
                    fail(
                        f"{bom} manages {artifact} at {resolved.get(artifact)}, expected {version}"
                    )
            print(f"{bom} resolves {len(expected)} io.freedriver.iclient entries at {version}")
            parent_pom = local / "io" / "freedriver" / "iclient" / "iclient-parent" / version
            if not any(parent_pom.glob("*.pom")):
                fail(f"resolving {bom} left iclient-parent out of the local repository")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--log", required=True)
    args = parser.parse_args()
    repo = Path(args.repo)
    check_deploy_plugin(Path(args.log).read_text())
    check_deployed_poms(repo, args.version)
    check_bom_resolution(repo, args.version)
    print(f"local publish of {args.version} matches the release")


if __name__ == "__main__":
    main()
