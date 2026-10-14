# AGENTS.md — Standing Rules (Mosque Tracker)

Rules every agent working in **this** repository (`C:\Users\devtips\Documents\MosqueTracker`) MUST follow.

> This repo is independent of the sibling `Gharib_Qoran_App` repository. The Quran repo's rules do **not**
> apply here, and the rules below do not apply there.

## 1. Keep the project context file always up to date

- `AI Agent Help context project.md` (repo root) is the master context a new agent needs to onboard.
- **Whenever the project changes** (code, scripts, commands, versioning, signing, assets, structure,
  tooling, workflows, or any quirk/decision), UPDATE this file in the same pass — add, modify, or
  delete sections as reality changes.
- If a section no longer reflects the project, DELETE or rewrite it; do not leave stale content.
- **NEVER delete the file itself.**

## 2. Keep `README.md` and the context file consistent

`README.md` is the human-facing summary. When behaviour, versions, signing, or commands change, update
both `README.md` and `AI Agent Help context project.md` in the same pass.

## 3. Never commit secrets

- `keystore.properties`, `*.jks`, `.key_store/`, `local.properties`, and `app/build/` are gitignored. Keep them that way.
- Never print or commit keystore passwords or key material.

## 4. Build verification bar

Before calling any change done, all of these must pass from the repo root:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
```

- `lintDebug` must exit 0 (version-currency warnings are acceptable; `MissingPermission`-class errors are not).
- Kotlin warnings must stay at zero for `:app:compileDebugKotlin`.

## 5. Stay modern — nothing deprecated

- Do not introduce deprecated Android/AndroidX APIs. If a fix requires one, document the deviation in the
  context file and `README.md` (see "Documented deviations") instead of silently using it.
- Dependencies are pinned in `gradle/libs.versions.toml`; bump deliberately, not incidentally.
