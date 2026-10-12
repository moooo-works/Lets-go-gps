# Floating appearance correction

Owner: A-050 exact request in root devlog. Outcome: readable panel and bubble in light/dark app themes and localized labels matching app language.
Scope: overlay presentation/context only. No engine, permissions, ads, DB, or existing PR58/59/60 changes. Worktree fix/floating-theme-locale starts latest origin/main b1590f0 then inherits unmerged PR59 ba2541f; correction PR stacked on59.
Cause: MapVM calls MockGpsTheme default SYSTEM, manager ComposeView uses unlocalized ApplicationContext. Existing dark secondary color also too weak on panel.
Approach: manager owns a small preference-aware Compose wrapper; listen to theme/language preferences with disposal, supply localized Context/Configuration/Resources, reuse app theme with overlay-only contrast colors. Remove inner system-only theme. Opaque panel/bubble with visible border and elevation.
Minimality check: reuse mockgps_prefs and existing translations; no shared Settings rewrite or duplicated resource strings. Window owner/focus/geometry unchanged.
## Invariants
INV-1: Theme/locale changes only presentation; selected/current/route state and commands unchanged.
INV-2: Preference listener unregisters with composition disposal; no extra window or owner.
INV-3: System language/theme fallback preserved; copied Configuration never mutates Application resources.
## Acceptance criteria
AC1: Forced LIGHT/DARK while opposite system setting uses app selection, readable foreground/surface/action contrast >=4.5 for enabled labels; outline >=3.
AC2: Chinese/English/Japanese/Korean and system locale load existing labels; change persisted preference refreshes visible composition, reopening respects setting.
AC3: Collapse/expand/search keyboard and window close still work; no mock start from presentation.
Checks: relevant executed unit/context tests; mandatory gradle test/lintDebug/assembleDebug; backed-up Pixel7Pro appearance smoke and settings restore; independent bounded review.
