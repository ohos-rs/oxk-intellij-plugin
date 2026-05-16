## Release

1. Ensure that the UNRELEASED section of the [CHANGELOG.md](./CHANGELOG.md) is up to date with all relevant changes.
   See https://github.com/JetBrains/gradle-changelog-plugin for the changelog format.
2. Update the `pluginVersion` within [gradle.properties](./gradle.properties).
3. Run the manual [test.yml](./.github/workflows/test.yml) workflow when validation is needed.
4. Run the manual [release.yml](./.github/workflows/release.yml) workflow from the `ohos` branch with the release
   version. The workflow builds the plugin, creates the `v<version>` tag, generates release notes and a build log, and
   uploads the plugin zip plus log to GitHub Releases.
