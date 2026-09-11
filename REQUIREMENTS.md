# Setup Requirements

**Read this before you start the timer.** This is a build-from-scratch assessment. You build a Next.js backend with the Vercel AI SDK and an external web-search tool. You also build a **native Android (Kotlin/Jetpack Compose)** chat frontend. The backend runs in Docker Compose. Android Studio setup is the item most likely to cost you time, so set it up in advance. Plan about 30 minutes. Plan more if you have never created an Android Virtual Device or done a clean Android Studio install.

## Hardware & OS

- **macOS, Windows, or Linux.** Android Studio runs on all three. Hardware acceleration for the emulator (Apple Silicon, Intel HAXM/HVF, or KVM on Linux) is strongly recommended; an unaccelerated emulator is painfully slow.
- About 15 GB free disk. Android Studio, the Android SDK, and emulator system images are large.

## Required tools

| Tool | Version | Check | Install |
|------|---------|-------|---------|
| Android Studio | current stable | Help > About (or Android Studio > About on macOS) | https://developer.android.com/studio |
| JDK | 17 or newer (bundled with Android Studio) | `java --version` | Installed with Android Studio |
| Kotlin | bundled via the Gradle plugin | Resolved by Gradle on first build | None needed |
| Android SDK Platform Tools | current | `adb --version` | Android Studio > SDK Manager |
| Node.js | 20 LTS or newer | `node --version` | https://nodejs.org (or `nvm`) |
| A package manager | npm (bundled), or pnpm/yarn | `npm --version` | None needed |
| Docker Desktop / Engine | Compose v2 (`docker compose`) | `docker compose version` | https://docs.docker.com/get-docker/ |
| Git | any recent | `git --version` | https://git-scm.com |

**Set up the Android Emulator now, not during the assessment:**
- Install Android Studio, then open it once so the setup wizard finishes downloading the SDK and build tools.
- Open Android Studio > Device Manager and create an Android Virtual Device (any recent Pixel image with a current API level is fine). Boot it once so the system image finishes its first-run setup.
- Running on a physical Android phone is fine too, and requires only Developer Options and USB debugging enabled on the device; no developer account or signing setup is needed for local debug builds. The emulator is simpler and sufficient for this assessment.
- Networking heads-up: inside the emulator, `localhost` is the emulator itself, not your machine. To reach your Docker Compose backend, use `10.0.2.2` in place of `localhost`, or run `adb reverse tcp:3000 tcp:3000` (adjust the port) to forward the emulator's localhost to your machine. A physical phone needs `adb reverse` or your machine's LAN IP.

## Accounts / keys (required)

- **An LLM API key** for the Vercel AI SDK, either **Anthropic** (https://console.anthropic.com) or **OpenAI** (https://platform.openai.com). You must bring your own key. PressW does not supply one. The key is paid, but it costs only cents of usage.
- **A web-search tool** for the external-tool requirement. Decide your approach and get a key if it needs one, for example [Tavily](https://tavily.com) or [Brave Search API](https://brave.com/search/api/). This key is also your own.
- If you add a database, the brief allows a local Postgres in Docker Compose. No external account is needed.

Put keys in `.env` files. Never commit them.

## Pre-flight check

Run this the day before, not when the timer starts:

```bash
node --version                         # expect v20+
docker compose version                 # expect Compose v2.x
adb --version                          # confirms Android SDK platform tools are on your PATH
emulator -list-avds                    # expect at least one AVD; or just launch one from Android Studio > Device Manager
adb devices                            # expect your booted emulator or phone listed
```

Then create a throwaway app in Android Studio (File > New > New Project > Empty Activity, which uses Jetpack Compose) and hit Run against the emulator. If a blank app launches, your setup works. Delete the throwaway project. Confirm that your LLM key returns a valid response too.
