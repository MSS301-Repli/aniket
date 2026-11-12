# Animal Locket Mobile

Flutter client for Android and iOS. The app can run with local demo
repositories or connect to a configured Animal Locket backend.

## Requirements

- Flutter SDK with a Dart SDK compatible with the constraint in `pubspec.yaml`
- Android Studio and an Android emulator/device for Android
- Xcode and an iOS simulator/device for iOS (macOS only)

## Install and run the demo

From the repository root:

```powershell
Set-Location mobile
flutter pub get
flutter run
```

At the sign-in screen, choose **Open demo account** (Vietnamese UI:
**Mở tài khoản demo**). Demo mode uses local demo repositories and does not
require backend services, Keycloak, or real credentials.

## Connect to a live backend

Pass backend settings as Flutter compile-time defines. Do not put passwords,
client secrets, access tokens, or production credentials in source control.
The mobile app uses a public OAuth client; authentication is performed through
the system browser and the configured redirect URI.

Example for an Android emulator when the backend runs on the development host:

```powershell
flutter run `
  --dart-define=API_BASE_URL=http://10.0.2.2:8080 `
  --dart-define=WS_URL=ws://10.0.2.2:8080/ws/chat `
  --dart-define=KEYCLOAK_ISSUER=http://10.0.2.2:8080/realms/animal-locket `
  --dart-define=KEYCLOAK_CLIENT_ID=mobile `
  --dart-define=OAUTH_REDIRECT_URL=com.animallocket.app:/oauth2redirect
```

For an iOS simulator, use a host address reachable from the simulator (often
`127.0.0.1`). For a physical device, use the development machine's LAN address
or a reachable HTTPS endpoint. The API and WebSocket endpoints must be
reachable from the device. Use `https://` and `wss://` for deployed
environments; plain HTTP is for local development only.

| Define | Purpose | Default |
| --- | --- | --- |
| `API_BASE_URL` | Backend API origin; the app appends `/api/v1` | `http://api.localhost:8080` |
| `WS_URL` | Chat WebSocket endpoint | `ws://api.localhost:8080/ws/chat` |
| `KEYCLOAK_ISSUER` | Keycloak realm issuer URL | `http://auth.localhost:8080/realms/animal-locket` |
| `KEYCLOAK_CLIENT_ID` | Public mobile OAuth client ID | `mobile` |
| `OAUTH_REDIRECT_URL` | OAuth callback registered for the mobile client | `com.animallocket.app:/oauth2redirect` |

Configure the backend gateway, Keycloak realm, public OAuth client, and exact
redirect URI before using live sign-in. The app sends API requests through the
configured gateway and uses the configured WebSocket endpoint for live chat.
Backend routes and enabled features must match the backend deployment.

### Optional integrations

| Define | Default | Requirement |
| --- | --- | --- |
| `ENABLE_PUSH` | `false` | Firebase native app configuration and a backend that accepts device registration |
| `ENABLE_BILLING` | `false` | Store product configuration and the corresponding billing integration |
| `GLYPH_READ_ENABLED` | `true` | Set to `false` to disable live glyph reads |
| `GLYPH_PERSONAL_LIBRARY_ENABLED` | `true` | Set to `false` to disable the personal glyph library |
| `GLYPH_PUBLISH_ENABLED` | `true` | Set to `false` to disable glyph publishing |
| `GLYPH_SEND_ENABLED` | `true` | Set to `false` to disable glyph sending |

For example, to run without push and billing integrations:

```powershell
flutter run `
  --dart-define=API_BASE_URL=https://api.example.test `
  --dart-define=WS_URL=wss://api.example.test/ws/chat `
  --dart-define=KEYCLOAK_ISSUER=https://auth.example.test/realms/animal-locket `
  --dart-define=ENABLE_PUSH=false `
  --dart-define=ENABLE_BILLING=false
```

Replace example hostnames with values for your environment. Never use real
credentials in sample commands or commit local configuration files containing
secrets.

## Localization and checks

```powershell
flutter gen-l10n
flutter analyze
flutter test
```

The demo integration smoke test requires a connected emulator or device:

```powershell
flutter test integration_test/demo_smoke_test.dart
```
