* _2026-10-08 16:27:10 +0800 (gpt-6.1-sol/low)_

# Floating companion UI

- **Implemented:** Pure Compose panel with five pages, draggable collapsed bubble, current/selected coordinate separation and explicit route replacement confirmation. UI operations call only supplied actions.

- **Checked:** All four XML resources parse, contain the same 43 floating keys and contain no duplicate string names. Scoped `git diff --check` passed.

- **Not verified:** No Gradle or device run, as instructed. Host must compile and verify keyboard release, Maps owner behavior, available screen bounds and original joystick gestures.

## Changed paths

Within `.worktrees/floating-companion`:

- `app/src/main/java/com/moooo_works/letsgogps/ui/map/FloatingCompanionView.kt`

- `app/src/main/res/values/strings.xml`

- `app/src/main/res/values-en/strings.xml`

- `app/src/main/res/values-ja/strings.xml`

- `app/src/main/res/values-ko/strings.xml`

Resources were appended in two language batches without reformatting previous entries. This report is the only root artifact written by the worker.

## Behavior and boundaries

- Search has explicit search and paste actions. Saved results and general results are separate. Folder and favorite filters consume controller-provided saved locations.

- Selection shows name, description and fixed-format coordinates; locate, save, queue and copy remain separate actions. The queue is explicitly separate from the active route, with a count and apply button.

- Routes expose saved route selection, play/pause/stop, speed and loop mode. Control retains the supplied joystick slot and exploration action.

- Only the map page creates GoogleMap. Initialization uses existing Maps SDK; map clicks and use-center call selection only. A crosshair is centered in the map container, with no new markers or location layer.

- Search focus reports through onInputFocus. Collapse, close, App return and page switches clear Compose focus, hide the keyboard and send false. Usage reads real supplied state, and unknown current mock coordinates disable copy.

- No new dependencies, ViewModels, service intents, controller edits, main map edits, Git commits or full test runs. Host handles integrated compilation and required verification.

## Usability follow-up

- Header actions and page tabs now remain outside scrolling content. Saved places and routes use ID-keyed LazyColumn lists capped at 180 dp; remote results use coordinate/index keys. Long lists no longer compose every row at once.

- Saved places have the shared query field and input-focus callback. Search and Saved both allow input focus. Paused routes use the existing Resume label.

- Panel height is capped at min(600, screen height minus 100) dp with a 200 dp lower bound. The map sits outside the vertical scrolling area, with a fixed 140 dp container and four-language selection-only guidance.

- Scoped diff checks and resource checks pass. These follow-up source changes have not been compiled or tested on a device; host performs the next combined Gradle run and verifies map drag and small-screen fit.

Self-check: Only the authorized UI, four append-only resource files and this report were written; callbacks preserve selection/execution separation; runtime acceptance remains unverified.
