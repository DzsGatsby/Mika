# FlashPhone v1.1.6–v1.1.12 call port

MIKA base: `9fd0be4ab7aec2e1167af4bb8583728b78c384ec` (main, 0.1.45).
Previously handled baseline: FlashPhone v1.1.5, merged MIKA PR [#607](https://github.com/wangzan101/Mika/pull/607).
Source: [DzsGatsby/FlashPhone](https://github.com/DzsGatsby/FlashPhone), Apache-2.0.

## Duplicate and repository checks

Before editing, checked the latest main, 50 most recently updated PRs, a repository-wide
FlashPhone PR search, all MIKA upstream/fork branch names and the existing synchronization
and follow-up tasks. No later synchronization PR or competing port branch was found.
The old staged v1.1.5 workspace was left untouched; this port uses a separate clone.
Neither repository has AGENTS.md or .agents/skills instructions. Followed MIKA's
.github/CONTRIBUTING.md, developer architecture/roadmap and CI build/test instructions.

## Version coverage

| Release and source | MIKA disposition | Independent FlashPhone changes excluded |
| --- | --- | --- |
| [v1.1.6](https://github.com/DzsGatsby/FlashPhone/releases/tag/v1.1.6) `b080e464adb1c62afbf2c65b60ade1f8349e1c14` | Port foreground microphone/speaker briefing, exact complete current-owner authorization, preserved owner instructions, cancellation, audio focus and number/SIM/config/lifecycle gates. Use the selected MIKA endpoint/model/speaker/credentials. Existing task form, text confirmation, SIM choice and locked dial remain. Add an optional voice button to that form. | iPhone-style dialer, default navigation, standalone workspace and encrypted app-specific draft persistence. MIKA retains its task form and saveable draft. |
| [v1.1.7](https://github.com/DzsGatsby/FlashPhone/releases/tag/v1.1.7) `af76f180df7798c981431ed60f17e016ef4c268f` | Port AI and manual call-bound DTMF, strict protocol validation/deduplication, finite serial pulses, cancellation, per-call AI budget and local-submission-only results. Add keypad to MIKA's existing current-call card. Tools stay disabled during briefing/connection tests. | Standalone call layout. |
| [v1.1.8](https://github.com/DzsGatsby/FlashPhone/releases/tag/v1.1.8) `7032d933a5d0dfcc7412df815f2828c2ba4b87f4` | Reviewed; no call engine change. Existing MIKA receiving rules and navigation remain. | FlashPhone page header/navigation renaming and design preview. |
| [v1.1.9](https://github.com/DzsGatsby/FlashPhone/releases/tag/v1.1.9) `85817240a76d075f951fb51805322375393783f3` | Port briefing playback budget (12 seconds, including in-flight blocks), interruption invalidation, deliberate-stop cancellation handling, ASR full snapshots and payload-free completion. Silence does not authorize or end briefing. | Duplicate text-button removal in FlashPhone's dialer. MIKA keeps its existing text form. |
| [v1.1.10](https://github.com/DzsGatsby/FlashPhone/releases/tag/v1.1.10) `39cbfdb81acbb8ceec680e1c4df2f5d2e64719e4` | Port pre-start/cleanup cancellation races and unresolved-prior-turn checks for both briefing authorization and natural farewell hangup. | Remote progress/final-result outbox and wider remote brief summary envelope: MIKA has none of FlashPhone's calls/remote, CallDeviceAgent/Store/Results or CallSummaryEngine. Its own transcript summary/follow-up-draft implementation remains. |
| [v1.1.11](https://github.com/DzsGatsby/FlashPhone/releases/tag/v1.1.11) `c935303595d1fc878a08f83ed8466e1658a7abfb` | Expose only the immutable brief bound to the current call as activeTask; open its complete instructions from the existing MIKA call card. Existing transcript/takeover/hangup remain. | Full standalone native redesign, recording player, bottom navigation, voice-cloning settings, remote settings and design documentation. |
| [v1.1.12](https://github.com/DzsGatsby/FlashPhone/releases/tag/v1.1.12) `8bcd8d89a20141f47d4d24c61debc42dabaa2676` | Reviewed; release explicitly does not change audio, answering or dialing. MIKA uses its own themed dialogs/system bars. | White-sheet system-bar fix and disclosure for FlashPhone recording/remote uploads: those services are absent from MIKA. |

The v1.1.6 source range also contains an upstream startup compatibility test for the
existing v1.1.5 behavior; no startup audio replacement is needed. MIKA's provider-aware
PCM input/output rates and its configurable realtime model/address are retained.
New system DTMF status lines are shown as device status and excluded from summary input,
so a submission is never attributed to the caller. No raw DTMF digits are persisted.

## Validation

Final local validation on Linux, JDK 17.0.21, pinned Gradle 8.11.1 and Android SDK 36:

| Check | Actual result |
| --- | --- |
| `:app:testDebugUnitTest` | 2,740 tests, 0 failures/errors/skips |
| `:core:test` | 3,853 tests, 0 failures/errors; 2 existing explicit rescore skips (`RescoreEvalRun`, `RescoreRecordedRuns`) |
| `:app:assembleDebug` | Passed; debug APK generated |
| `:app:compileDebugAndroidTestKotlin` | Passed, including the synthetic DTMF/current-task Compose tests |
| `python3 tools/i18n/translations.py check` | Passed: 3,319 resources across 11 UI languages |
| `python3 -m unittest discover -s tools/i18n -v` | 12 tests passed |
| `git diff --check` / staged diff check | Passed |

The four Gradle tasks completed in one successful invocation (13m 28s). The first
2GB shared-compiler attempt exhausted memory; the successful run used local command-line
options `--max-workers=1 -Dorg.gradle.jvmargs="-Xmx6g -Dfile.encoding=UTF-8"
-Pkotlin.compiler.execution.strategy=in-process`. No project build settings were changed.
Network setup used the existing proxy and system Java trust store without altering either.

Device test compilation does not execute instrumentation. No device installation, real carrier calls,
provider credentials, remote deployment, merge, release or SMS-login changes are part of
this port. Foreground microphone → live provider → carrier handoff and real IVR reception
remain unverified on physical phones, including OEM routing, interruption and dual-SIM
behavior. Offline protocol and cancellation tests cannot establish those outcomes.
