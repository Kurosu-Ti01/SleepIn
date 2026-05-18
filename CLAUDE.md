# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SleepIn is an Android timetable app built with Kotlin, Jetpack Compose, Material 3, Room, Navigation Compose, DataStore, and WorkManager. It provides schedule template management, timetable/course management, CSV import/export, and home-screen widgets.

**Language Requirements:**
- All code comments and KDoc: English
- Developer documentation (`docs/SleepIn-Docs/docs`): Simplified Chinese
- User-facing strings: Chinese (no internationalization planned yet)

## Build and Test Commands

Always use the Gradle Wrapper from repo root. The shell is bash (use Unix syntax even on Windows).

**Build:**
```bash
./gradlew assembleDebug
```

**Testing:**
```bash
# Run all JVM unit tests (app module)
./gradlew testDebugUnitTest

# Run one test class
./gradlew testDebugUnitTest --tests "com.kurosu.sleepin.domain.usecase.schedule.SaveScheduleUseCaseTest"

# Run one test method
./gradlew testDebugUnitTest --tests "com.kurosu.sleepin.domain.usecase.schedule.SaveScheduleUseCaseTest.<methodName>"

# Run instrumented tests on connected device/emulator
./gradlew connectedDebugAndroidTest

# Run one instrumented test class
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kurosu.sleepin.ExampleInstrumentedTest
```

**Lint:**
```bash
./gradlew lintDebug  # debug variant
./gradlew lint       # default task
```

**Developer Documentation Site** (`docs/SleepIn-Docs`):
```bash
cd docs/SleepIn-Docs
npm install
npm run docs:dev    # dev server
npm run docs:build  # production build
```

## Architecture

SleepIn follows **MVVM + Clean Architecture** with strict layer boundaries:

```
app/src/main/java/com/kurosu/sleepin/
├─ MainActivity.kt              # Single-activity Compose shell
├─ SleepInApplication.kt        # Manual DI root + startup pipelines
├─ data/                        # Room/DataStore/CSV/RepositoryImpl
├─ domain/                      # Models/Repository interfaces/UseCases
├─ ui/                          # Navigation/ViewModels/Compose screens
├─ di/                          # Manual DI modules
├─ widget/                      # App Widget + refresh scheduling
├─ update/                      # Update check pipeline
└─ reminder/                    # Class reminder (AlarmManager + WorkManager)
```

### Dependency Rules (Strictly Enforced)

- **UI → Domain**: ViewModel calls UseCase ✅
- **Data → Domain**: RepositoryImpl implements Repository interface ✅
- **Domain → Data**: FORBIDDEN ❌ (Domain only depends on interfaces)
- **UI → Data**: FORBIDDEN ❌ (UI cannot directly access DAO/Entity)
- **Widget → Domain**: Reuse UseCases ✅

### Manual Dependency Injection

**Hilt is intentionally disabled.** All wiring happens in:
- `SleepInApplication.kt`: Creates Database → Repository → UseCase chain
- `di/DatabaseModule.kt`: Provides Room Database and DAOs
- `di/RepositoryModule.kt`: Provides Repository implementations and CSV components

When adding new dependencies:
1. Define interface in `domain/repository`
2. Implement in `data/repository`
3. Wire in `SleepInApplication` or appropriate `di` module
4. Pass to ViewModel via manual `ViewModelProvider.Factory` or `remember...ViewModel` helpers

### Data Flow Pattern

```
Screen → ViewModel → UseCase → Repository(interface) → RepositoryImpl → DAO/DataStore
         ↑                                                                    ↓
         └────────────────── Flow/StateFlow ←─────────────────────────────────┘
```

- **UseCases** expose `operator fun invoke(...)` for uniform API
- **Repositories** return `Flow<T>` for reactive data
- **ViewModels** collect flows and expose `StateFlow` to UI
- **Screens** subscribe to `StateFlow` and trigger recomposition

### Navigation Convention

- Routes defined in `ui/navigation/Screen.kt`
- Graph wiring in `ui/navigation/SleepInNavHost.kt`
- **Create/edit route pattern**: For Long nav arguments, `-1L` is the sentinel for create mode (Navigation Long args are non-nullable)

### Data Mapping Boundary

- Keep Room `Entity` types inside `data` layer
- Convert using `data/local/mapper` before crossing into domain/UI
- Never expose `Entity` to ViewModel or Composable

### Settings as Shared State

`AppSettings` from `SettingsRepositoryImpl` is the cross-feature state source for:
- Theme selection
- Update check scheduling
- Reminder scheduling
- Related UI behavior

### Repository Persistence Semantics

`save...With...` flows are transaction-based and use **replace-all semantics** for child collections (e.g., schedule periods and course sessions).

## Background Pipelines

`SleepInApplication` wires three startup/background pipelines:

1. **Widget refresh** (`widget/WidgetRefreshScheduler`): Periodic WorkManager task
2. **Update checks** (`update/UpdateCheckScheduler`): DownloadManager-based GitHub release check
3. **Class reminders** (`reminder/CourseReminderScheduler`): AlarmManager-first + WorkManager fallback
   - Uses `AlarmManager` for exact timing (schedules only the next upcoming reminder)
   - Uses `WorkManager` every 15 minutes as reliability fallback
   - Triggers on settings change, Room table changes, and system events (BOOT_COMPLETED, TIME_CHANGED, etc.)
   - Reminder evaluation uses shared week calculation logic from `HomeWeekDateCalculator`

## Code Comment Standards

Apply when modifying Kotlin source files under `app/src/main/java`.

**Required Coverage:**
- KDoc for every public class, data class, interface, object, and public function
- Function-level comments for non-trivial internal/private functions
- Inline comments before each non-trivial code block
- Line-level comments for key lines where intent is not obvious

**Required KDoc Content:**
- Purpose and business context
- Parameter meaning and constraints
- Return value meaning
- Side effects (database writes, state mutation, navigation, IO, scheduling, widget refresh)
- Threading/lifecycle assumptions when relevant

**Android-Specific Explanations** (when relevant):
- Compose state and recomposition behavior
- `StateFlow`/`Flow` collection and lifecycle-aware observation
- ViewModel coroutine scope usage and cancellation boundaries
- Room entity/DAO mapping and transaction boundaries
- DataStore read/write flow and default value behavior
- WorkManager scheduling constraints and retry behavior
- App Widget update triggers and data snapshot flow

**Quality Bar:**
- Do not add trivial comments that only restate the code
- Explain intent, invariants, preconditions, and edge-case handling
- Keep comments concise but specific enough for onboarding readers
- Prefer stable reasoning comments over UI text duplication

## Developer Documentation Standards

Apply when creating or editing files under `docs/SleepIn-Docs/docs`.

**Language:** Simplified Chinese

**Audience:** Beginners in Android/Compose with basic programming knowledge

**Required Coverage for Core Docs:**
- Overall architecture: MVVM + Clean Architecture
- Module/package responsibilities: `data`, `domain`, `ui`, `di`, `widget`
- Room data model and key relationships
- Main feature flows and Screen/ViewModel interactions
- Repository and UseCase call chain
- Windows build/run/debug workflow
- Common pitfalls and troubleshooting

**Writing Style:**
- Use clear section hierarchy and beginner-friendly progression
- Prefer "概念 → 代码位置 → 调用链 → 常见错误" structure
- Explain not only "what", but also "why" and "when"
- Provide concrete examples from real files rather than abstract descriptions

**File References:**
- Always include real project file paths when describing implementation
- Ensure file references remain valid after code changes

**Recommended Page Blueprint:**
1. 背景与目标
2. 相关模块与文件位置
3. 核心流程（可配 Mermaid）
4. 关键实现细节（含代码片段/路径）
5. 调试与排错建议
6. 延伸阅读与下一步

## Key Documentation References

- Architecture overview: `docs/SleepIn-Docs/docs/dev/2.architecture/1.outline.md`
- Module responsibilities: `docs/SleepIn-Docs/docs/dev/2.architecture/2.module-responsibilities.md`
- Build/debug onboarding: `docs/SleepIn-Docs/docs/dev/1.build-debug/1.build-debug-windows.md`
- Business flows: `docs/SleepIn-Docs/docs/dev/3.business`
- Feature pipelines: `docs/SleepIn-Docs/docs/dev/4.feature`
- Troubleshooting: `docs/SleepIn-Docs/docs/dev/5.others/1.troubleshooting.md`

## Safety Rules

- Never edit generated artifacts under `app/build`
- Room schema output is `app/schemas` — keep schema snapshots consistent when schema changes
- Keep behavior-preserving changes unless behavior change is explicitly requested
- Avoid destructive Git operations unless explicitly requested
