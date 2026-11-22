---
applyTo: ".github/workflows/*.yml,.github/workflows/*.yaml"
---

# GitHub Actions instructions

- Preserve the repository's CI, artifact, dependency-submission, and manual release contracts.
- Use the Java 25 toolchain and the Maven wrapper when changing build or test execution.
- Grant only the `GITHUB_TOKEN` permissions each job needs. Prefer `contents: read` unless the task explicitly writes repository content.
- Prefer commit-SHA-pinned actions with the release tag in a comment when adding or upgrading an action. If a mutable tag must remain, document why.
- Keep workflow paths, artifact names, and the root `<revision>` contract synchronized. The release workflow must not be able to publish a version that differs from the committed revision.
- Do not weaken required checks or make release/tag behavior less strict without an explicit request.
- Add concurrency and timeouts where they prevent redundant or stuck runs.
- Validate YAML syntax and workflow-specific behavior in addition to running the smallest relevant Maven build.
