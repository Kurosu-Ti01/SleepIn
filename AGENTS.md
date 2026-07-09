# AGENTS.md

## Build & Test (Windows)

```powershell
.\gradlew assembleDebug              # Build debug APK
.\gradlew testDebugUnitTest          # All JVM unit tests (no device needed)
.\gradlew testDebugUnitTest --tests "com.kurosu.sleepin.domain.usecase.schedule.SaveScheduleUseCaseTest"
.\gradlew testDebugUnitTest --tests "com.kurosu.sleepin.domain.usecase.schedule.SaveScheduleUseCaseTest.someMethod"
.\gradlew connectedDebugAndroidTest  # Instrumented tests (needs device/emulator)
.\gradlew lintDebug                  # Lint
```

Docs site (`docs/SleepIn-Docs`):
```powershell
cd docs/SleepIn-Docs
npm install                          # First time only
npm run docs:dev                     # Dev server
npm run docs:build                   # Production build
```

## Architecture (non-obvious)

- **Single-activity Compose**: `MainActivity` is the shell; all screens are Compose destinations.
- **Manual DI only**: Hilt is **intentionally disabled** in `build.gradle.kts`. All wiring lives in `SleepInApplication` + `di/DatabaseModule.kt` + `di/RepositoryModule.kt`. ViewModels get dependencies via `SleepInApplication` cast from Context.
- **Layer boundaries** (strict):
  - `domain/` — no Android framework imports (no Context, Room, Compose). Immutable models, repository interfaces, use cases.
  - `data/` — Room entities/DAOs, DataStore, CSV codecs, repository impls, mappers.
  - `ui/` — ViewModels, Compose screens/components, navigation, theme.
  - `widget/`, `reminder/`, `update/` — OS integrations (Glance widgets, AlarmManager, WorkManager, DownloadManager).
- **Data flow**: `Screen → ViewModel → UseCase → Repository(interface) → RepositoryImpl → DAO/DataStore/API`
- **ViewModel construction**: Manual `ViewModelProvider.Factory` pattern; ViewModels expose UseCase results as `StateFlow`.

## Key Conventions

- **Navigation**: Routes defined in `ui/navigation/Screen.kt`; graph in `ui/navigation/SleepInNavHost.kt`. For optional-ID editor screens, `-1L` is the create-mode sentinel (Navigation Long args are non-nullable).
- **UseCase API**: `operator fun invoke(...)` returning `Flow` or result types. Business validation lives here, not in Composables.
- **Error style**: Result types (e.g. `SaveScheduleResult`) for expected failures; exceptions for unexpected.
- **Repository persistence**: `save...` methods use replace-all semantics for child collections within transactions.
- **Data mapping**: Room Entity/DAO types stay inside `data/`; `data/local/mapper/EntityMappers.kt` converts to domain models at the boundary.
- **Database**: Room with `fallbackToDestructiveMigration()` — schema changes delete data. Export schema to `app/schemas/` for version history. DB triggers automatic widget refreshes via `InvalidationTracker`.
- **Settings**: `SettingsRepositoryImpl` backed by DataStore; `AppSettings` is the cross-feature shared state source (theme, update checks, reminders).

## Language Split (critical)

| What | Language | Source |
|------|----------|--------|
| Kotlin source comments | English (detailed, onboarding-friendly KDoc) | `.github/instructions/android-commenting.instructions.md` |
| Developer docs (`docs/SleepIn-Docs/docs/dev/`) | Simplified Chinese | `.github/instructions/dev-docs-cn.instructions.md` |
| Communication with user in terminal | Simplified Chinese |  |

When editing code: add KDoc on every public type/function, explain Android-specific concepts (StateFlow, recomposition, Room transactions, WorkManager constraints). Do not add trivial restatement comments.

When editing developer docs: write in Chinese, beginner-friendly progression, always cite real file paths from the project.

When thinking and communicating with user: always use Chinese.

## Docs Structure

- User docs: `docs/SleepIn-Docs/docs/user/`
- Developer docs: `docs/SleepIn-Docs/docs/dev/` (architecture, build/debug, business flows, features, troubleshooting)
- Sample CSV files: `docs/temp/example/`

## Do Not Edit

- `app/build/` — generated artifacts
- `app/schemas/` — Room export; update only with Migrations testing
