# oxk-intellij-plugin

[![Test](https://github.com/ohos-rs/oxk-intellij-plugin/actions/workflows/test.yml/badge.svg?branch=ohos)](https://github.com/ohos-rs/oxk-intellij-plugin/actions/workflows/test.yml)

<!-- Plugin description -->

# Oxk

Oxk is an ArkTS/ArkUI linting and formatting plugin powered by [ohos-rs/oxc-ark](https://github.com/ohos-rs/oxc-ark).

## Lint

Oxk provides oxlint-compatible linting with ArkTS migration rules.

- Inline diagnostics with highlighting for warnings and errors.
- Quick fixes to resolve issues when available.
- Command to apply all auto-fixable issues in the current editor.
- Automatically apply fixes on save.
- Configurable run trigger: lint on type or on save.
- Type-aware rules support for enhanced linting.
- Custom icons for Oxk configuration files.
- JSON schema validation for `.oxlintrc.json` configuration files.
- Configurable file extensions including `.ets`, `.js`, `.jsx`, `.ts`, `.tsx`, `.vue`, `.svelte`, and `.astro`.

## Format

Oxk formats ArkTS/ArkUI and related project files.

- Format code via right-click context menu.
- Format code with the built-in actions and their shortcuts (<kbd>Code</kbd> > <kbd>Reformat Code</kbd>, <kbd>
  Code</kbd> > <kbd>Reformat File...</kbd>)
- Automatically format on save.
- JSON schema validation for `.oxfmtrc.json` configuration files.
- Configurable file formats including ArkTS, JavaScript, TypeScript, JSON, Markdown, CSS, TOML, and YAML.

<!-- Plugin description end -->

## Installation

- Manually:

  Download the [latest release](https://github.com/ohos-rs/oxk-intellij-plugin/releases/latest) and install it
  manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Local Oxk

For local development against `ohos-rs/oxc-ark`, configure both Oxk lint and Oxk format manually and point the
executable path to the native `oxk` binary, for example `../oxc-ark/target/debug/oxk`. The plugin starts linting with
`oxk lint --lsp` and formatting with `oxk format --lsp`.

You can also launch the IDE with `OXK_BINARY_PATH=/absolute/path/to/oxk` to make automatic detection prefer a local
native binary.

## Troubleshooting

IntelliJ provides log files for standard logs as well as the LSP integration. The plugin uses the regular log
directories which can be found
here https://www.jetbrains.com/help/idea/directories-used-by-the-ide-to-store-settings-caches-plugins-and-logs.html#logs-directory.
All LSP logs are output to `language-services/Oxk*`. Non-LSP logs are output to
`idea.log`.

The log level can be configured with the information available
here https://youtrack.jetbrains.com/articles/SUPPORT-A-43/How-to-enable-debug-logging-in-IntelliJ-IDEA.
`com.github.ohosrs.oxkintellijplugin:all` - Enable debug logging for the plugin.
`org.wso2.lsp4intellij:all` - Enable debug logging for DevEco LSP integrations.

Enabling both debug logging for the plugin and debug logging for LSP integrations will typically provide useful
information for investigating problems.

---
