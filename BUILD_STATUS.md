# Build status — Capture Point 0.1.1 development snapshot

## CI build workflow added

- Added `.github/workflows/build.yml` for GitHub Actions.
- Uses Temurin JDK 25 and Gradle 9.6.0.
- Runs `gradle --no-daemon clean build` on pushes, pull requests, and manual dispatch.
- Uploads generated JARs and build reports as a workflow artifact.

## Project target

- Minecraft: `26.3`.
- Fabric Loader: `0.20.1`.
- Fabric Loom: `1.19-SNAPSHOT`.
- Fabric API: `0.166.2+26.3`.
- Java: `25`.

## Verification status

The GitHub Actions workflow has been prepared but has **not yet run** because the project has not been pushed to a GitHub repository. No compiled JAR is available yet. Even after a successful CI compile, dedicated-server startup, integrated-server startup, GUI interaction, and end-to-end gameplay still require in-game testing.
