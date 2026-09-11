# Changelog

## 1.2.2-beta

### Fixes

- `/skv editor` no longer fails when one variable cannot be serialized by
  Skript, for example an item stack with an amount above 99 on newer Paper
  builds. That variable is uploaded by type only so it can still be viewed
  and deleted, and a console warning reports how many were affected

### Faster mass deletion

- Deleting a whole list from the web editor now runs the same calls as
  Skript's own `delete {x::*}`: one per direct index and one for the list,
  instead of one call per variable, so it is as fast as deleting the list
  from a script
- "Delete all" on a category in the website editor sends one list delete, so
  the apply diff stays small no matter how many variables are inside
- Plain variable deletes in the same diff still delete only that variable

### Less work on the main thread

- The apply diff is parsed once with Gson off the main thread. The main
  thread only writes the changes
- Replaced the regex diff parser with Gson

### Preview

- `/skv apply <sessionId> <code> --preview` labels list deletes as
  "delete whole list"

## 1.2.1-beta

### Profiler measures commands, functions and periodicals

- Script commands, script functions, `every X` and `at HH:MM` triggers are now
  timed alongside event triggers
- Each trigger reports self time as well as inclusive time. Self time excludes
  time spent in nested profiled triggers, so a slow function shows up once
  instead of inflating every caller
- Profile payload is now version 2 with `kind` and `selfNs` per trigger. The
  website still renders version 1 profiles
- Hooking commands, functions or periodicals uses reflection on Skript
  internals. If a hook fails on your Skript version the profiler logs a
  warning and records the other kinds

### Works without variables.csv

- `/skv editor` and the profiler no longer refuse to run when
  `plugins/Skript/variables.csv` is missing. Servers using MySQL or SQLite
  variable storage are now supported
- Variables are read from Skript's in-memory map

### Preview before applying

- `/skv apply <sessionId> <code> --preview` prints what would be set, deleted,
  or skipped, without writing anything. The apply code stays valid
- Values that cannot be parsed are listed so you can fix them in the editor
  before applying
- Tab completion offers `--preview` next to `--force`

### Skript syntax

- Effect `start [a|the] skript profile [for %timespan%]`
- Effect `stop [the] skript profile [and send [the] link to %commandsender%]`
- Effect `open [the] variable editor for %commandsenders%`
- Condition `[the] skript profiler is [not] recording`
- Expression `[the] skript profiler elapsed time` (timespan)
- Expression `[the] skript profiler execution count` (number)
- Syntax is registered through Skript's addon registry introduced in 2.14

### Internals

- Command logic moved into `EditorService` so the command and the Skript
  syntax share one implementation
- `/skv help` now lists the profile subcommands

## 1.1-beta

Adds a session based Skript profiler and a new Profiler tab in the web editor.

### Profiler

- New Profiler tab at skript-variables.com showing which of your scripts,
  triggers and events cost the most server time
- Rank by script, by individual trigger, or by event, each with total time,
  share of the recorded window, run count, average and worst single run
- Spikes list: the 50 slowest individual trigger executions in the window,
  with a timestamp so you can line them up against lag reports
- Summary header with the recording duration, total measured time and the
  Skript version the profile was taken on

### Commands

- `/skv profile start [seconds]`: begins recording. Defaults to 60 seconds,
  maximum 600. Recording stops itself when the duration elapses
- `/skv profile stop`: stops early, then uploads the profile and gives you a
  clickable link that opens straight on the Profiler tab
- `/skv profile status`: shows how long the current recording has been going
  and how many trigger executions have been measured so far
- `/skv profile upload`: re-sends the last recorded profile. A failed upload
  no longer destroys the recording, and a profile cut short by a reload can
  still be sent
- Starting a new recording cancels any pending auto stop from a previous one,
  so back to back recordings no longer stop each other early
- Tab completion for `profile` and its subcommands
- `/skv editor` now attaches the last recorded profile to the session
  automatically, so the Profiler tab is populated without a second upload

### Safety and correctness

- Recording stops automatically on `/sk reload` and `/skript reload` before
  Skript can rebuild its triggers underneath the profiler
- Recording stops automatically on plugin disable and server shutdown
- An early stop logs a console note telling you to run `/skv profile upload`
  to send the partial profile
- If the trigger registry cannot be reached, the profiler still unwraps what
  it can and clears its own state instead of staying stuck in recording mode
- Timing is per trigger with the original trigger left untouched, so a
  recording that ends for any reason restores Skript to its original state

### Privacy

- Script source is never uploaded. The payload carries only script paths, line
  numbers, event descriptions and timings
- Profiles are deleted when their editor session expires

### Notes

- New permission `skriptvariables.profile` (default: op), separate from
  `skriptvariables.editor`
- Commands, functions and periodicals are not measured in this version. Only
  event triggers are
- Internals now covered by a JUnit 5 test suite

## 1.0-alpha

First release of skript-variables.

- Browse all Skript variables in a web editor at skript-variables.com, no setup required
- Variables are displayed in a collapsible namespace tree (e.g. `{playerdata::uuid::kills}` shows as nested folders)
- Inline editing for strings, numbers, booleans, item types, block data and text components
- Dedicated modals for editing locations (world, X, Y, Z, yaw, pitch) and vectors (X, Y, Z with live magnitude and normalize)
- oopsk struct support: edit individual struct fields directly (requires oopsk)
- Delete variables, rename them, or copy them to a new name
- Batch-edit multiple selected variables to the same value at once
- Delete an entire namespace in one click
- Search by name with username-to-UUID resolution, regex mode, and filters by type or pending change
- Sidebar with per-namespace statistics and sort/filter controls
- Three colour themes: Ocean, Slate and Ember
- Variables held only in memory (not yet flushed to variables.csv) are automatically included when opening the editor
- `/skv editor`: uploads your variables and gives you a clickable editor link in chat
- `/skv apply <sessionId> <code>`: applies the changes you made in the editor to the live server
- `--force` flag on apply keeps the code reusable instead of expiring after one use
- `VariablesApplyEvent` fired before any changes are written, cancellable from Skript or another plugin
- Anonymous usage stats via bStats
