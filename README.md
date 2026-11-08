# Focus

A calm, private Android app for digital detox and digital minimalism.

- **Today**: screen time against a daily goal, unlocks, an hour-by-hour rhythm and your most-used apps, plus a week view.
- **Block**: pause apps and websites, always or on a schedule, and set daily time limits per app.
- **Focus**: timed sessions that enforce your block list. **Strict mode** can't be ended early.
- **Mindful block screen**: a breathing prompt instead of a hard wall, with an optional 5-minute pass after a 10-second pause.

Everything runs on the phone. No accounts, no network calls.

## Install on your phone (realme Narzo 60 / any Android 8+)

1. Open this repo's **Actions** tab → the latest green **Android build** run → download **focus-debug-apk**.
2. Unzip it and open `focus-xxxxxxx.apk` on the phone. Allow installing from your browser or file manager when asked.
3. Open **Focus** and follow the setup screens:
   - **Usage access**: pick *Focus* and switch it on.
   - **Accessibility**: Android 13+ blocks this for apps installed outside the Play Store. If it's greyed out:
     *Settings → Apps → Focus → ⋮ (top right) → **Allow restricted settings***, then go back and enable it under
     *Settings → Additional settings → Accessibility → Downloaded apps → **Focus blocker***.
   - **Run in background**: allow, so realme's battery manager doesn't kill the blocker.
   - **Auto launch**: turn on for Focus (realme: *Settings → Apps → Auto launch*).

Every CI build uses the same committed debug key, so a newer APK installs over the old one and keeps your data.

## Develop

Open the project in Android Studio (Ladybug or newer) and run the `app` configuration, or:

```bash
./gradlew testDebugUnitTest assembleDebug
```

### Layout

| Path | What |
| --- | --- |
| `core/` | Pure Kotlin, unit-tested: `BlockPolicyEngine`, `DomainMatcher`, `UsageAggregator` |
| `block/` | `FocusAccessibilityService` (detects apps and URLs), `BlockActivity`, browser URL reading |
| `usage/` | `UsageStatsManager` access and the daily snapshot worker |
| `data/` | Room, DataStore and repositories |
| `ui/` | Jetpack Compose screens and the design system (`ui/theme`) |

## Roadmap

1. ✅ **Stage 1**: blocking (apps, sites, schedules, limits, focus sessions) and a screen-time dashboard
2. Minimal launcher, grayscale and bedtime nudges, widgets, streaks
3. Opt-in backend and AI weekly insights (Claude, proxied server-side)
4. Play Store: billing tiers, Accessibility API disclosure, privacy policy, release signing
