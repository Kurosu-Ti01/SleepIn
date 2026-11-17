# AGENTS.md

Guidance for AI coding agents and contributors working in this repository.
Claude Code reads this file through `CLAUDE.md`, which imports it — keep the source of truth here.

## Project Overview

SleepIn is an Android timetable app built with Kotlin, Jetpack Compose, Material 3, Room, Navigation Compose, DataStore, and WorkManager. It provides schedule template management, timetable/course management, CSV import/export, portal (教务系统) import, and home-screen widgets.

**Language requirements:**

| What | Language | Source |
|------|----------|--------|
| Kotlin comments and KDoc | English (detailed, onboarding-friendly) | `.github/instructions/android-commenting.instructions.md` |
| Developer docs (`docs/SleepIn-Docs/docs/dev/`) | Simplified Chinese | `.github/instructions/dev-docs-cn.instructions.md` |
| User-facing strings | Chinese (no internationalization planned yet) |  |
| Communication with the user in the terminal | Simplified Chinese |  |

## Build & Test (Windows)

Run commands from the repository root with the Gradle Wrapper. On Windows the baseline shell is PowerShell 7; with Git Bash/WSL the equivalent is `./gradlew`.

```powershell
.\gradlew assembleDebug              # Build debug APK
.\gradlew testDebugUnitTest          # All JVM unit tests (no device needed)
.\gradlew testDebugUnitTest --tests "com.kurosu.sleepin.domain.usecase.schedule.SaveScheduleUseCaseTest"
.\gradlew testDebugUnitTest --tests "com.kurosu.sleepin.domain.usecase.schedule.SaveScheduleUseCaseTest.<methodName>"
.\gradlew connectedDebugAndroidTest  # Instrumented tests (needs device/emulator)
.\gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kurosu.sleepin.ExampleInstrumentedTest
.\gradlew lintDebug                  # Lint, debug variant
.\gradlew lint                       # Lint, default variant
```

Docs site (`docs/SleepIn-Docs`):

```powershell
cd docs/SleepIn-Docs
npm install                          # First time only
npm run docs:dev                     # Dev server
npm run docs:build                   # Production build
```

## Architecture

SleepIn follows **MVVM + Clean Architecture** with strict layer boundaries.

```
app/src/main/java/com/kurosu/sleepin/
├─ MainActivity.kt              # Single-activity Compose shell
├─ SleepInApplication.kt        # Manual DI root + startup pipelines
├─ data/                        # Room/DataStore/CSV/RepositoryImpl/mappers
├─ domain/                      # Models/Repository interfaces/UseCases
├─ ui/                          # Navigation/ViewModels/Compose screens/theme
├─ di/                          # Manual DI modules
├─ importer/                    # Portal import refresh scheduling
├─ widget/                      # App Widget + refresh scheduling
├─ update/                      # Update check pipeline
└─ reminder/                    # Class reminder (AlarmManager + WorkManager)
```

### Dependency Rules (strictly enforced)

- **UI → Domain**: ViewModel calls UseCase ✅
- **Data → Domain**: RepositoryImpl implements Repository interface ✅
- **Domain → Data**: FORBIDDEN ❌ (Domain depends only on interfaces)
- **UI → Data**: FORBIDDEN ❌ (UI cannot access DAO/Entity directly)
- **Widget → Domain**: Reuse UseCases ✅

`domain/` must not import the Android framework (no Context, Room, Compose).

### Manual Dependency Injection

**Hilt is intentionally disabled** in `build.gradle.kts`. All wiring lives in:

- `SleepInApplication.kt` — creates the Database → Repository → UseCase chain
- `di/DatabaseModule.kt` — provides Room Database and DAOs
- `di/RepositoryModule.kt` — provides Repository implementations and CSV components

When adding a dependency:

1. Define the interface in `domain/repository`
2. Implement it in `data/repository`
3. Wire it in `SleepInApplication` or the appropriate `di` module
4. Pass it to the ViewModel via a manual `ViewModelProvider.Factory` or `remember...ViewModel` helper

### Data Flow

```
Screen → ViewModel → UseCase → Repository(interface) → RepositoryImpl → DAO/DataStore
         ↑                                                                    ↓
         └────────────────── Flow/StateFlow ←─────────────────────────────────┘
```

- **UseCases** expose `operator fun invoke(...)` for a uniform API; business validation lives here, not in Composables.
- **Repositories** return `Flow<T>` for reactive data.
- **ViewModels** collect flows and expose `StateFlow`; they are the only place that composes UseCases for the UI.
- **Screens** subscribe to `StateFlow` and trigger recomposition.

### Navigation Convention

- Routes defined in `ui/navigation/Screen.kt`
- Graph wiring in `ui/navigation/SleepInNavHost.kt`
- **Create/edit pattern**: for `Long` navigation arguments, `-1L` is the sentinel for create mode (Navigation `Long` args are non-nullable)

### Data Mapping Boundary

- Keep Room `Entity`/DAO types inside `data/`
- Convert with `data/local/mapper/EntityMappers.kt` before crossing into domain/UI
- Never expose `Entity` to a ViewModel or Composable

### Error Style

- Result types (e.g. `SaveScheduleResult`) for expected failures
- Exceptions for unexpected failures

### Repository Persistence Semantics

`save...With...` flows are transaction-based and use **replace-all semantics** for child collections (e.g. schedule periods and course sessions).

### Settings as Shared State

`SettingsRepositoryImpl` is backed by DataStore; `AppSettings` is the cross-feature state source for theme selection, update check scheduling, reminder scheduling, and related UI behavior.

### Database

Room with `fallbackToDestructiveMigration()` — schema changes delete existing data. Export schema to `app/schemas/` for version history. DB changes trigger automatic widget refreshes via `InvalidationTracker`.

## Background Pipelines

`SleepInApplication` wires three startup/background pipelines:

1. **Widget refresh** (`widget/WidgetRefreshScheduler`) — periodic WorkManager task
2. **Update checks** (`update/UpdateCheckScheduler`) — DownloadManager-based GitHub release check
3. **Class reminders** (`reminder/CourseReminderScheduler`) — AlarmManager-first with WorkManager fallback
   - `AlarmManager` for exact timing (schedules only the next upcoming reminder)
   - `WorkManager` every 15 minutes as a reliability fallback
   - Triggered by settings changes, Room table changes, and system events (`BOOT_COMPLETED`, `TIME_CHANGED`, etc.)
   - Reminder evaluation reuses the shared week calculation logic from `HomeWeekDateCalculator`

## Code Comment Standards

Apply when modifying Kotlin sources under `app/src/main/java`.

**Required coverage:**

- KDoc for every public class, data class, interface, object, and public function
- Function-level comments for non-trivial internal/private functions
- Inline comments before each non-trivial code block
- Line-level comments where intent is not obvious from the code

**Required KDoc content:** purpose and business context, parameter meaning and constraints, return value meaning, side effects (database writes, state mutation, navigation, IO, scheduling, widget refresh), and threading/lifecycle assumptions.

**Android-specific explanations when relevant:** Compose state and recomposition, `StateFlow`/`Flow` collection lifecycle, ViewModel coroutine scope and cancellation boundaries, Room entity/DAO mapping and transaction boundaries, DataStore read/write flow and default values, WorkManager constraints and retry behavior, App Widget update triggers and data snapshot flow.

**Quality bar:** no trivial comments that restate the code; explain intent, invariants, preconditions, and edge-case handling; keep comments specific enough for onboarding readers.

## Developer Documentation Standards

Apply when creating or editing files under `docs/SleepIn-Docs/docs/dev`.

- **Language**: Simplified Chinese
- **Audience**: beginners in Android/Compose with basic programming knowledge
- **Required coverage for core docs**: MVVM + Clean Architecture overview, package responsibilities (`data`, `domain`, `ui`, `di`, `widget`), Room data model and key relationships, main feature flows and Screen/ViewModel interactions, Repository/UseCase call chains, Windows build/run/debug workflow, common pitfalls and troubleshooting
- **Writing style**: clear section hierarchy and beginner-friendly progression; prefer 概念 → 代码位置 → 调用链 → 常见错误; explain *why* and *when*, not only *what*; use concrete examples from real files instead of abstract descriptions
- **File references**: always cite real project paths, and keep them valid after code changes
- **Recommended page blueprint**: 背景与目标 → 相关模块与文件位置 → 核心流程（可配 Mermaid）→ 关键实现细节（含代码片段/路径）→ 调试与排错建议 → 延伸阅读与下一步

## Docs Structure

| Area | Path |
|------|------|
| User docs | `docs/SleepIn-Docs/docs/user/` |
| Developer docs | `docs/SleepIn-Docs/docs/dev/` (architecture, build/debug, business flows, features, troubleshooting) |
| Sample CSV files | `docs/temp/example/` |

Key developer doc references:

- Architecture overview: `docs/SleepIn-Docs/docs/dev/2.architecture/1.outline.md`
- Module responsibilities: `docs/SleepIn-Docs/docs/dev/2.architecture/2.module-responsibilities.md`
- Build/debug onboarding: `docs/SleepIn-Docs/docs/dev/1.build-debug/1.build-debug-windows.md`
- Business flows: `docs/SleepIn-Docs/docs/dev/3.business/`
- Feature pipelines: `docs/SleepIn-Docs/docs/dev/4.feature/`
- Troubleshooting: `docs/SleepIn-Docs/docs/dev/5.others/1.troubleshooting.md`

## Do Not Edit

- `app/build/` — generated build artifacts
- `app/schemas/` — Room schema export; update only together with Migrations testing

## Safety Rules

- Never edit generated artifacts under `app/build`
- Keep Room schema snapshots under `app/schemas` consistent when the schema changes
- Keep changes behavior-preserving unless a behavior change is explicitly requested
- Avoid destructive Git operations unless explicitly requested
