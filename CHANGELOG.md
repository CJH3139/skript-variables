# Changelog

## 1.1-beta

- Profiler tab: record Skript trigger timings with `/skv profile start` and
  `/skv profile stop`, then see worst scripts, triggers and events in the web
  editor
- `/skv profile upload` retries a failed profile upload using the last
  recorded profile, so a failed upload no longer destroys the recording
- Script source is never uploaded, only paths, line numbers and timings

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
