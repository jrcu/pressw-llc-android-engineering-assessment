# PantryPal Android Client

Native Android chat frontend for PantryPal, built with Kotlin and Jetpack Compose. It talks to
the Next.js backend in [`../backend`](../backend) via a streaming `POST /api/chatbot` request.

## Requirements

- Android Studio (current stable)
- JDK 17 (bundled with Android Studio)
- The backend running locally — see [`../backend/README.md`](../backend/README.md) for setup
  (`docker compose up` or `pnpm dev`)

## Running the app

1. Open the `android/` directory in Android Studio and let it sync Gradle.
2. Start the backend first (`docker compose up` from `backend/`, or `pnpm dev`) so it's listening
   on `http://localhost:3000`.
3. Run the `app` configuration on an **Android emulator**, not a physical device.
4. Send a message in the chat UI to confirm it reaches the backend.

## ⚠️ Must run on an emulator

This app is hardcoded to talk to the backend at `http://10.0.2.2:3000`
(`ChatRepository.kt`), which is the Android emulator's special alias for the host
machine's `localhost`. **It will not work on a physical device or a different emulator/host
setup without changes**, because:

- `10.0.2.2` only resolves inside the emulator's virtual network — a physical device on the
  same Wi-Fi has no route to it.
- The network security config (`app/src/main/res/xml/network_security_config.xml`) only allows
  plaintext HTTP to `10.0.2.2` and `localhost`; any other host is blocked by Android's default
  HTTPS-only policy.

If you need to run on a physical device or a different host, update **both**:

1. The `baseUrl` default in
   [`ChatRepository`](app/src/main/java/com/pantrypal/chatbot/data/ChatRepository.kt) to your
   machine's LAN IP (e.g. `http://192.168.1.23:3000`), or use `adb reverse tcp:3000 tcp:3000` and
   keep `http://localhost:3000`.
2. The allowed cleartext domain in
   [`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml) to match.

## Project structure

- `MainActivity.kt` / `ChatScreen.kt` — Compose UI
- `ChatViewModel.kt` — chat state and message handling
- `data/ChatRepository.kt` — OkHttp client that streams the backend's response
- `data/ChatMessage.kt` — message/role model
