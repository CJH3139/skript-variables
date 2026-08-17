# skript-variables

A Skript addon that lets server operators browse, edit, and delete Skript variables through a live web UI at [skript-variables.com](https://skript-variables.com).

Need help or want to report a bug? Join the [Discord](https://discord.gg/m4H55rQJzC).

## Requirements

- Paper 1.21.4+
- Skript 2.14.x – 2.16.x
- Java 21+

## Installation

Download the latest release from [Modrinth](https://modrinth.com/plugin/skript-variables).

1. Drop the plugin JAR into your `plugins/` folder.
2. Start or reload your server.
3. Run `/skv editor` in-game to open the web editor.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/skv editor` | Upload variables and get a link to the web editor | `skriptvariables.editor` |
| `/skv apply <sessionId> <code>` | Apply changes made in the editor back to the server | `skriptvariables.editor` |
| `/skv apply <sessionId> <code> --force` | Apply changes without expiring the code | `skriptvariables.editor` |
| `/skv profile start [seconds]` | Record Skript timings, default 60s, max 600s | `skriptvariables.profile` |
| `/skv profile stop` | Stop recording, upload the results and open the report | `skriptvariables.profile` |
| `/skv profile status` | Show whether recording is active | `skriptvariables.profile` |
| `/skv profile upload` | Retry uploading the last recorded profile after a failed upload | `skriptvariables.profile` |
| `/skv help` | Show command help | op only |

All commands default to **op only**.

## How It Works

1. `/skv editor` reads your server's Skript variables, uploads them to the skript-variables API, and sends you a clickable link.
2. You edit variables in the browser. Changes are queued but not applied yet.
3. The editor generates an apply code. Run `/skv apply <sessionId> <code>` in-game to write the changes back to Skript's variable storage instantly.

## Profiler

`/skv profile start` records how long each Skript trigger takes, then
`/skv profile stop` uploads the results and opens them in the Profiler tab of
the web editor. It ranks your worst scripts, triggers and events, and lists
the slowest individual executions.

If the upload fails, the recording is not lost. Run `/skv profile upload` to
retry the upload using the last recorded profile, without having to record
again.

Your script source is never uploaded. The report contains only script paths,
line numbers, event descriptions and timings. Commands, functions and
periodicals are not measured in this version.

## Building

```bash
./gradlew shadowJar
```

Output: `build/libs/skript-variables-<version>.jar`

## Support

- [Discord](https://discord.gg/m4H55rQJzC) for questions, suggestions and bug reports
- [GitHub issues](https://github.com/CJH3139/skript-variables/issues) for anything you would rather track in the open

## License

GNU General Public License v3.0. See [LICENSE](LICENSE).
