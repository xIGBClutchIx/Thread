# Thread Development Standards

This document defines the V1 engineering baseline for source quality, documentation, formatting, linting, local validation, and GitHub automation.

The goal is not to maximize tooling. The goal is to make changes from humans or coding agents predictable, reviewable, and easy to validate from a clean checkout.

## Java and source conventions

- V1 is Java only.
- Use the Java version required by the pinned Minecraft target selected in Slice 0.
- Do not add Kotlin or a Kotlin runtime dependency.
- Do not create `package-info.java` files.
- Prefer small classes and interfaces with explicit responsibilities.
- Prefer immutable DTOs/records where they fit the contract.
- Avoid wildcard imports.
- Avoid hidden global state when explicit wiring is practical.
- Keep Minecraft/Fabric and MCP-specific types behind their documented boundaries.

## Comments and Javadocs

Thread should be documented, but comments must add information rather than narrate syntax.

### Javadocs

Add Javadocs to public Thread contracts and extension points where callers or future integrations need to understand semantics. In V1 this especially includes:

- public core interfaces
- provider contracts
- tool/integration/context extension points
- registries and public registry operations
- public error/result contracts whose behavior is not self-evident
- threading abstractions
- transport-facing abstractions that have lifecycle or compatibility requirements

Javadocs should explain relevant contracts such as:

- what the API represents
- whether a value is live or snapshot data
- side/thread requirements
- nullability/absence semantics
- bounds and truncation behavior
- error behavior
- important invariants

Use `@param`, `@return`, and `@throws` when they communicate useful behavior. Do not add boilerplate tags that merely repeat the type or method name.

Javadocs are not required for obvious record accessors, trivial private helpers, straightforward overrides, or implementation details whose behavior is already clear from the code.

### Implementation comments

Comments are expected around non-obvious behavior, especially:

- Minecraft client vs integrated-server ownership
- game-thread dispatch and concurrency assumptions
- lifecycle races such as world unload while a request is in flight
- MCP/SDK compatibility workarounds
- query caps and safety decisions that are not obvious from the code
- serialization compatibility decisions
- unusual Minecraft/Fabric behavior

Prefer comments that explain **why** a choice exists. Avoid comments such as `// increment count` or `// get player inventory` when the code already says that.

Do not keep commented-out code. TODOs must not hide acceptance-critical work. A TODO that survives a slice should state why it remains and should preferably reference a tracked issue once the repository uses issues.

## Formatting

Use **Spotless** as the Gradle formatting entry point.

For Java, use **google-java-format** through Spotless unless Slice 0 finds a concrete compatibility problem with the selected Java/Minecraft toolchain. Keep the formatter version pinned in the build.

Spotless should also enforce basic repository hygiene where practical:

- remove unused Java imports
- forbid wildcard imports
- trim trailing whitespace in supported text files
- ensure final newlines

Expected commands:

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
```

`spotlessApply` is a developer/agent convenience command. CI must use `spotlessCheck` and fail rather than rewriting source.

Add a root `.editorconfig` for editor-neutral basics such as UTF-8, LF line endings, final newlines, and whitespace rules. Do not try to duplicate the Java formatter in `.editorconfig`.

## Linting and static checks

Use the Gradle **Checkstyle** plugin with a small Thread-owned configuration.

The linter should focus on useful consistency and correctness-oriented rules rather than fighting the formatter. Avoid a giant inherited ruleset that produces noise around Fabric/Minecraft patterns.

At minimum, the V1 setup should catch or enforce appropriate rules for:

- invalid/problematic imports
- obvious naming/structure mistakes
- malformed Javadocs where a Javadoc exists
- accidental style patterns not handled by Spotless

Do not use Checkstyle to require meaningless comments or Javadocs on every getter, record component, override, or private helper.

Where compatible, enable useful Java compiler warnings. Do not make warnings fatal if Minecraft/Fabric-generated or external patterns create unavoidable noise; document any suppressed warning class/rule rather than globally hiding unexplained problems.

Expected checks should be reachable through normal Gradle lifecycle tasks. `./gradlew build` or `./gradlew check` must run the required quality gates rather than requiring developers to know a hidden command sequence.

## Tests

JUnit-based unit tests should run through Gradle and remain part of the normal `check`/`build` lifecycle.

Every slice must add tests for important behavior it introduces. Core and transport tests should not require launching Minecraft when a fake provider/registry can prove the behavior.

See `TESTING.md` for the V1 test strategy and manual smoke tests.

## Local preflight

Before considering a slice complete, run:

```bash
./gradlew spotlessCheck check build
```

If the configured Gradle lifecycle makes some of these tasks redundant, that is fine. Keep the explicit tasks available and ensure a plain `./gradlew build` from a clean checkout performs the complete required validation.

Codex should normally run `spotlessApply` before the final check when it has changed Java or other Spotless-managed files.

The MCP adapter uses `com.sun.net.httpserver`, the supported API exported by the JDK's
`jdk.httpserver` module. The architecture test confines any `com.sun` production import to that
specific API under `transport.mcp`; other internal JDK namespaces remain forbidden.

## GitHub Actions

V1 should include GitHub Actions under `.github/workflows/`.

### Required: CI workflow

A build/CI workflow must run on:

- pull requests
- pushes to the primary branch
- manual `workflow_dispatch`

The workflow should:

1. check out the repository
2. install the pinned/required Java toolchain, preferably Temurin
3. configure Gradle using the Gradle Wrapper and the official Gradle setup action
4. run the normal clean validation/build path
5. fail on formatting, lint, compile, or test failures
6. upload useful build reports on failure when practical
7. upload the built mod JAR as a workflow artifact on successful primary-branch builds when useful

Use Gradle caching supplied by the official Gradle action rather than inventing a custom cache unless there is a measured reason to change it.

Keep workflow permissions minimal. Prefer pinned stable action versions; for security-sensitive or release workflows, pin third-party actions to immutable commit SHAs when practical.

### Required by V1 release readiness: tag/release build

By Slice 6, add a tag-driven release workflow for tags such as `v*` that:

- performs the same full validation as CI
- builds the release JAR from a clean checkout
- uploads the JAR as a GitHub workflow/release artifact
- produces a checksum when practical

Publishing automatically to Modrinth, CurseForge, Maven repositories, or other distribution services is not required for V1. Keep publishing credentials and platform automation out until distribution requirements are intentionally designed.

## Dependency maintenance

During Slice 0, add dependency update automation if it can be kept low-noise. Prefer Dependabot for Gradle and GitHub Actions with a modest cadence rather than custom scripts.

Dependency updates must still pass the same CI gates. Do not automatically merge Minecraft/Fabric/MCP dependency updates without validation because protocol/game compatibility is part of Thread's behavior.

## Repository files expected from the foundation

Slice 0 should establish, as applicable:

```text
.editorconfig
.github/
  workflows/
    ci.yml
  dependabot.yml           # recommended if enabled
config/
  checkstyle/
    checkstyle.xml
build.gradle(.kts)
gradle.properties
settings.gradle(.kts)
gradlew
gradlew.bat
```

A release workflow may be added in Slice 6 rather than Slice 0.

## Agent expectations

Coding agents must not bypass quality gates to make a slice pass.

Do not:

- disable Checkstyle globally because of one violation
- skip tests in CI
- remove formatter checks from `build`
- suppress warnings/checks without a specific rationale
- add generated boilerplate comments solely to satisfy a rule
- create `package-info.java`

When a tool produces unreasonable noise for legitimate Fabric/Minecraft code, narrow the rule or exclusion and document why.
