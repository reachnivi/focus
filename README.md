# Focus

A calm, private Android app for digital detox and digital minimalism.

- **Today**: screen time against a daily goal, unlocks, an hour-by-hour rhythm and your most-used apps, plus a week view.
- **Block**: pause apps and websites, always or on a schedule, and set daily time limits per app.
- **Focus**: timed sessions that enforce your block list. **Strict mode** can't be ended early.
- **Mindful block screen**: a breathing prompt instead of a hard wall, with an optional 5-minute pass after a 10-second pause.

Everything runs on the phone. No accounts, no network calls.

## Install on your phone (realme Narzo 60 / any Android 8+)

**Download:** [focus.apk (latest)](https://github.com/reachnivi/focus/releases/latest/download/focus.apk). Open this link on the phone; you don't need a GitHub account.

1. Tap the downloaded `focus.apk`. If Android says it can't install unknown apps from this source, tap **Settings → Allow from this source**, go back and tap **Install** (on realme, choose **Install anyway** if a security check appears).
2. Open **Focus** and follow the setup screens:
   - **Usage access**: pick *Focus* and switch it on.
   - **Accessibility**: Android 13+ blocks this for apps installed outside the Play Store. If it's greyed out:
     *Settings → Apps → Focus → ⋮ (top right) → **Allow restricted settings***, then go back and enable it under
     *Settings → Additional settings → Accessibility → Downloaded apps → **Focus blocker***.
   - **Run in background**: allow, so realme's battery manager doesn't kill the blocker.
   - **Auto launch**: turn on for Focus (realme: *Settings → Apps → Auto launch*).
3. The Today screen shows **Blocker on** when everything works. If the blocker stops, Focus says so there and in a notification.

To update, download the link again and install over the old version; your settings and history stay.
Each merge to `main` publishes a new [release](https://github.com/reachnivi/focus/releases). Builds from other
branches are in the **Actions** tab as zipped `focus-debug-apk` artifacts (sign in to GitHub to download).

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

1. ✅ **Stage 1: Personal use.** Blocking (apps, sites, schedules, limits), focus sessions, screen-time dashboard, blocker health alerts
2. **Stage 2: Minimalism**
   - ✅ 2a Minimal home screen (launcher)
   - 2b Mindful pause: a breathing pause before opening chosen apps
   - 2c Bedtime and wind-down: nightly block list, Do Not Disturb, reminder
   - 2d Streaks, goals, weekly recap and a home-screen widget
3. **Stage 3: Competitive essentials** (what AppBlock, Opal, ScreenZen, StayFree and BlockSite users expect)
   - 3a Block only Reels and Shorts inside Instagram and YouTube, keeping the rest of the app
   - 3b Hardened strict mode: uninstall protection, guarded Accessibility and App info pages, friction to disable
   - 3c Mute notifications from blocked apps, with a digest afterwards
   - 3d Quick-settings tile, Pomodoro cycles, presets (Work, Study, Sleep)
   - 3e Open-count limits and category limits
   - 3f Keyword and adult-content blocking
   - 3g Backup and export, Hindi and regional languages
4. **Stage 4: Insights.** Opt-in backend, accounts and sync, AI weekly insights (Claude, proxied server-side)
5. **Stage 5: Play Store launch.** Free vs Pro, billing, onboarding with a personal plan, Accessibility declaration, privacy policy, release signing, store listing, crash reporting
