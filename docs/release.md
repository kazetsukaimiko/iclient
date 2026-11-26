# Releasing iclient

kazetsukaimiko calls each release: the tag, the GitHub Release, the `packages` environment and its approval, and any deploy from a workstation.

## Before this workflow is on main

The `packages` environment has to exist first. GitHub otherwise creates it with no protection the first time a job names it, and a tag would publish with no approval.

kazetsukaimiko creates the environment (Settings, Environments) with:

- Required reviewer: kazetsukaimiko, and nobody else. A second user or a team can approve alone, and the release check rejects that.
- Deployment branches and tags: a tag rule that allows `MAJOR.MINOR.PATCH` (for example `*.*.*`, which the release check then narrows to three numeric parts).
- Prevent self-review: off. The person who pushes the tag is the person who approves the deployment.

The release check reads the required reviewers through the API. The pull request records the environment's tag rule and its prevent-self-review setting, also read through the API, before merge.

## Calling a release

1. `main` contains the commit to publish. The commit is `66b24c1` or a later commit on `main`.
2. kazetsukaimiko pushes a tag `MAJOR.MINOR.PATCH` with no `v` prefix, on that commit. The first release is `0.1.0`.
3. `.github/workflows/publish-release.yml` runs on that tag.
4. The `check` job has `contents: read` and `actions: read`. It refuses a tag that is not three numeric parts, a tagged commit that is not an ancestor of `origin/main`, and a `packages` environment whose required reviewers are anything other than kazetsukaimiko alone. It then runs `./mvnw -B verify` and the same deploy against a temporary `file://` repository.
5. When that job is green, the `deploy` job asks kazetsukaimiko to approve the `packages` deployment. That job has `contents: read` and `packages: write`, and it is the job that uploads. Its token is the job's own `GITHUB_TOKEN`.
6. The deploy sets every module to the tag with `versions-maven-plugin` `2.18.0` (`-DprocessAllModules=true`) and uploads with:

   ```
   ./mvnw -B deploy -DdeployAtEnd=true -DaltDeploymentRepository=github::https://maven.pkg.github.com/kazetsukaimiko/iclient
   ```

   `deployAtEnd` holds every upload until the reactor has built, so a failed build or verify uploads nothing. The repository id is `github` for all 15 modules, including the three BOMs that have no parent. The root pom's `distributionManagement` block is the same URL, kept for reference; the command line is what publishes.

Pull requests and pushes to `main` run `.github/workflows/ci.yml` only. That workflow's permission is `contents: read`.

## Coordinates

Every module is `io.freedriver.iclient` at the tag version:

| Artifact | Packaging |
| --- | --- |
| `iclient-parent` | pom |
| `iclient-bom` | pom |
| `iclient-api` | jar |
| `iclient-http-api` | jar |
| `iclient-http-tck` | jar |
| `iclient-validation-api` | jar |
| `iclient-jakarta-parent` | pom |
| `iclient-jakarta-bom` | pom |
| `iclient-cdi-jakarta` | jar |
| `iclient-validation-jakarta` | jar |
| `iclient-http-microprofile` | jar |
| `iclient-javax-parent` | pom |
| `iclient-javax-bom` | pom |
| `iclient-cdi-javax` | jar |
| `iclient-validation-javax` | jar |

A consumer adds:

```xml
<repository>
  <id>iclient</id>
  <url>https://maven.pkg.github.com/kazetsukaimiko/iclient</url>
  <releases><enabled>true</enabled></releases>
  <snapshots><enabled>false</enabled></snapshots>
</repository>
```

Reading the registry takes a token with `read:packages`. GitHub Packages asks for that token on a public repository too. The repository's own `GITHUB_TOKEN` cannot read another repository's packages, so a consumer's build supplies its own token for server id `iclient`.

A release is done when `iclient-jakarta-bom` and `iclient-http-microprofile` at that version resolve, with their iclient dependencies, from an empty local repository.

## An interrupted release

A published version stays as it was uploaded. GitHub Packages refuses a second upload of the same version.

- A failed build, test or verify uploads nothing: the check job never reaches the deploy job, and `deployAtEnd` waits for the whole reactor before the first upload.
- A failure during the upload (network, 401, 409) can leave a partial version. Look at the deploy log's `Uploading to github` lines, and at the package versions listed for `io.freedriver.iclient` under the repository's packages.
- Recovery is the next patch tag (`0.1.1` after a partial `0.1.0`). A second run of the same tag leaves the uploaded files as they are. An admin can instead delete the partial package version.

The poms on `main` stay at `0.1.0-SNAPSHOT`. The tag is the version that gets published.
