# IPification Android SDK Public Demo

This repository contains the IPification Android SDK module and a stage sample app that shows clients how to integrate the SDK flows from an Android application.

The demo app uses the SDK from the local module:

```gradle
implementation project(':ipification-auth')
```

It does not download the SDK from Maven or any external package source.

When the SDK is published, client apps should use this Maven coordinate:

```gradle
implementation "com.ipification.android:ipification-sdk:2.2.1"
```

## Project Structure

- `ipification-auth` - Android SDK library module.
- `app` - Stage demo app for testing and learning the SDK flows.
- `docs/` - Standard IP authentication documentation.

## Requirements

- Android Studio with JDK 17.
- Android SDK installed.
- A device or emulator with network access.
- For IM flow only: valid Firebase configuration for the demo app package.

## Maven Central Release

The SDK library module publishes the release artifact:

```text
com.ipification.android:ipification-sdk:2.2.1
```

## Build The Demo

Open the repository in Android Studio and sync Gradle, or build from terminal:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:assembleStageDebug
```

Compile check:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:compileStageDebugKotlin --no-daemon
```

## Stage Configuration

The demo loads dynamic stage configuration from:

```text
https://showcase.stage.ipification.com/api/config
```

These values are defined in `app/build.gradle`:

```gradle
BASE_URL = "https://showcase.stage.ipification.com"
CONFIG_PATH = "/api/config"
TOKEN_EXCHANGE_PATH = "/auth/mobile/login"
```

The loaded config provides available clients, scopes, redirect URIs, auth servers, and displayable flows. The demo selects a client by `user_flow`, then configures the SDK before starting authentication.

## Core SDK Setup

The demo configures the SDK in `ProcessViewModel` before each flow:

```kotlin
IPConfiguration.getInstance().apply {
    CLIENT_ID = "your_client_id"
    REDIRECT_URI = "redirect_uri"
    REALM = "ipification"
}
```

For TS43 and SMS flows, the app configures the SDK to use the backend URL provided by the showcase configuration:

```kotlin
val backendUrl = "your_backend_url"

IPConfiguration.getInstance().apply {
    TS43_BACKEND_URL_SANDBOX = backendUrl
    TS43_BACKEND_URL_PRODUCTION = backendUrl
    TS43_AUTH_PATH = "/ts43/auth"
    TS43_TOKEN_PATH = "/ts43/token"

    SMS_BACKEND_URL_SANDBOX = backendUrl
    SMS_BACKEND_URL_PRODUCTION = backendUrl
    SMS_AUTH_PATH = "/sms/auth"
    SMS_TOKEN_PATH = "/sms/token"
}
```

## Demo Flow Map

The main flow is:

```text
MainScreen or PnvScreen
        -> user_flow + optional phone number
        -> ProcessScreen
        -> ProcessViewModel.startAuthenticationWithUserFlow(...)
        -> SDK call
        -> ResultScreen or SmsOtpScreen
```

The demo no longer uses numeric flow types. Each flow is selected by `user_flow`, such as `pvn_ip`, `login_ip`, `pvn_sim`, or `pvn_sms`.

## Code Tracking

Use this map to jump from the UI entry point to the SDK call:

| Area | Function | File |
| --- | --- | --- |
| Main navigation graph | `AppNavigation` | `app/src/main/java/com/ipification/demoapp/activity/MainActivity.kt:153` |
| Main menu screen | `MainScreen` | `app/src/main/java/com/ipification/demoapp/activity/ui/main/MainScreen.kt:70` |
| PNV phone input screen | `PnvScreen` | `app/src/main/java/com/ipification/demoapp/activity/ui/main/PnvScreen.kt:81` |
| Processing screen | `ProcessScreen` | `app/src/main/java/com/ipification/demoapp/activity/ui/main/ProcessScreen.kt:27` |
| Main flow dispatcher | `startAuthenticationWithUserFlow` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:62` |
| SDK configuration | `configureDynamicIPification` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:142` |
| Demo backend URL | `getBackendUrl` | `app/src/main/java/com/ipification/demoapp/manager/ConfigManager.kt:28` |
| Token exchange | `performTokenExchange` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:558` |

## IP / PNV Flow

Used for phone number verification over IP.

Flow:

```text
PnvScreen
    -> user_flow = pvn_ip or pvn_ip_plus
    -> AuthRequest with login_hint
    -> IPificationServices.startAuthentication(...)
    -> token exchange with showcase backend
```

SDK call:

```kotlin
val authRequest = AuthRequest.Builder()
    .setScope(client.scope)
    .addQueryParam("login_hint", phoneNumber)
    .build()

IPificationServices.startAuthentication(activity, authRequest, callback)
```

After success, the app sends `response.code` to:

```text
https://showcase.stage.ipification.com/auth/mobile/login
```

Code path:

| Step | Function | File |
| --- | --- | --- |
| Verify button | `PnvViewModel.onVerifyClicked` | `app/src/main/java/com/ipification/demoapp/model/PnvViewModel.kt:95` |
| Start flow | `ProcessViewModel.startAuthenticationWithUserFlow` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:62` |
| SDK auth call | `ProcessViewModel.doAuth` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:183` |
| Exchange auth code | `ProcessViewModel.exchangeToken` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:202` |
| Backend Request | `ProcessViewModel.performTokenExchange` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:558` |

## Login IP Flow

Used for quick login without entering a phone number.

Flow:

```text
MainScreen
    -> user_flow = login_ip or login_ip_plus
    -> AuthRequest without login_hint
    -> IPificationServices.startAuthentication(...)
    -> token exchange with showcase backend
```

Code path:

| Step | Function | File |
| --- | --- | --- |
| Login button | `MainViewModel.onQuickLoginClicked` | `app/src/main/java/com/ipification/demoapp/model/MainViewModel.kt:184` |
| Start flow | `ProcessViewModel.startAuthenticationWithUserFlow` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:62` |
| SDK auth call | `ProcessViewModel.doAuth` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:183` |
| Backend request | `ProcessViewModel.performTokenExchange` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:558` |

## Anonymous Flow

Used for anonymous identity authentication.

Flow:

```text
MainScreen
    -> user_flow = anonymous
    -> AuthRequest without login_hint
    -> IPificationServices.startAuthentication(...)
    -> token exchange with showcase backend
```

Code path:

| Step | Function | File |
| --- | --- | --- |
| Anonymous button | `MainViewModel.onAnonymousLoginClicked` | `app/src/main/java/com/ipification/demoapp/model/MainViewModel.kt:225` |
| Start flow | `ProcessViewModel.startAuthenticationWithUserFlow` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:62` |
| SDK auth call | `ProcessViewModel.doAuth` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:183` |
| Backend request | `ProcessViewModel.performTokenExchange` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:558` |

## TS43 / SIM Flow

Used for SIM-based TS43 authentication. The SDK handles the TS43 backend calls internally after the app configures the TS43 backend URL and paths.

PNV TS43 flow:

```text
PnvScreen
    -> user_flow = pvn_sim
    -> AuthRequest with login_hint
    -> AuthChannel.TS43
    -> IPificationServices.startAuthentication(...)
    -> ResultScreen
```

Login TS43 flow:

```text
MainScreen
    -> user_flow = login_sim
    -> AuthRequest without login_hint
    -> AuthChannel.TS43
    -> IPificationServices.startAuthentication(...)
    -> ResultScreen
```

The login TS43 demo intentionally does not send `"anonymous"` as a `login_hint`.

Code path:

| Step | Function | File |
| --- | --- | --- |
| PNV TS43 button | `PnvViewModel.onTS43VerifyClicked` | `app/src/main/java/com/ipification/demoapp/model/PnvViewModel.kt:140` |
| Login TS43 button | `MainViewModel.onTS43QuickLoginClicked` | `app/src/main/java/com/ipification/demoapp/model/MainViewModel.kt:205` |
| Start flow | `ProcessViewModel.startAuthenticationWithUserFlow` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:62` |
| Configure TS43 backend | `ProcessViewModel.configureDynamicIPification` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:142` |
| SDK TS43 auth call | `ProcessViewModel.callTS43Auth` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:222` |

## SMS OTP Flow

Used for phone verification with SMS OTP.

Flow:

```text
PnvScreen
    -> user_flow = pvn_sms
    -> SMSServices.startVerification(...)
    -> SmsOtpScreen
    -> SMSServices.verifyOTP(...)
    -> ResultScreen
```

SDK calls:

```kotlin
SMSServices.startVerification(
    activity = activity,
    phoneNumber = phoneNumber,
    scope = client.scope,
    callback = callback
)
```

```kotlin
SMSServices.verifyOTP(
    activity = activity,
    otpCode = otpCode,
    authReqId = authReqId,
    nonce = nonce,
    callback = callback
)
```

Code path:

| Step | Function | File |
| --- | --- | --- |
| SMS verify button | `PnvViewModel.onSmsVerifyClicked` | `app/src/main/java/com/ipification/demoapp/model/PnvViewModel.kt:178` |
| Start flow | `ProcessViewModel.startAuthenticationWithUserFlow` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:62` |
| Start SMS verification | `ProcessViewModel.callSmsAuth` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:323` |
| Submit OTP | `ProcessViewModel.submitSmsOtp` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:369` |
| Resend OTP | `ProcessViewModel.resendSmsOtp` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:399` |

## Multi-Channel Flow

Used for trying multiple available channels in priority order.

The demo enables multi-channel only when `pvn_ip` exists and at least one extra PNV channel exists.

Current priority:

```text
TS43 -> IP -> SMS
```

The SDK channels are configured dynamically from available stage clients:

```kotlin
IPConfiguration.getInstance().AUTH_CHANNELS = listOf(
    AuthChannel.TS43,
    AuthChannel.IP,
    AuthChannel.SMS
)
```

Only available channels are included.

The app starts the flow with:

```kotlin
IPificationServices.startAuthentication(
    activity,
    authRequest,
    object : MultiAuthCallback { ... }
)
```

If SMS is required, the SDK callback navigates to `SmsOtpScreen`.

Code path:

| Step | Function | File |
| --- | --- | --- |
| Multi-channel button | `PnvViewModel.onMultiChannelVerifyClicked` | `app/src/main/java/com/ipification/demoapp/model/PnvViewModel.kt:213` |
| Start flow | `ProcessViewModel.startAuthenticationWithUserFlow` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:62` |
| Configure channel order | `ProcessViewModel.startAuthenticationWithUserFlow` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:89` |
| SDK multi-channel auth call | `ProcessViewModel.callMultiChannelAuth` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:264` |
| SMS fallback screen | `ProcessNavigation.ToSmsOtp` handling | `app/src/main/java/com/ipification/demoapp/activity/ui/main/ProcessScreen.kt:50` |

## IM Flow

Used for IM/backchannel authentication.

Flow:

```text
MainScreen
    -> user_flow = login_im
    -> Firebase token registration
    -> IMServices.startAuthentication(...)
    -> token exchange or S2S sign-in
    -> ResultScreen
```

Firebase is optional at app startup. If this public repo does not include `google-services.json`, the app still opens normally. IM flow will be unavailable until Firebase is configured for:

```text
com.ipification.demoapp.stage
```

Do not commit real client Firebase config to a public repository unless it is intended for public demo use.

Code path:

| Step | Function | File |
| --- | --- | --- |
| IM menu button | `MainViewModel.onFlowClicked` | `app/src/main/java/com/ipification/demoapp/model/MainViewModel.kt:252` |
| Firebase guard | `DemoApplication.ensureFirebaseInitialized` | `app/src/main/java/com/ipification/demoapp/DemoApplication.kt:50` |
| Start IM flow | `ProcessViewModel.callIMAuth` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:437` |
| SDK IM auth call | `ProcessViewModel.performIMAuth` | `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt:496` |
| Register device | `IMHelper.registerDevice` | `app/src/main/java/com/ipification/demoapp/im/IMHelper.kt:22` |
| S2S sign-in | `IMHelper.signIn` | `app/src/main/java/com/ipification/demoapp/im/IMHelper.kt:77` |

## Backend URLs

All demo backend calls use:

```text
https://showcase.stage.ipification.com
```

This includes:

- `/api/config`
- `/auth/mobile/login`
- `/ts43/auth`
- `/ts43/token`
- `/sms/auth`
- `/sms/token`
- `/device/register`
- `/auth/s2s/signin`

The demo must not use USSD backend URLs for IP, TS43, SMS, or IM flows.

## Files Worth Reading First

- `app/src/main/java/com/ipification/demoapp/model/ProcessViewModel.kt`
  - Main SDK flow implementation.
- `app/src/main/java/com/ipification/demoapp/model/MainViewModel.kt`
  - Main screen flow selection.
- `app/src/main/java/com/ipification/demoapp/model/PnvViewModel.kt`
  - Phone number collection and PNV flow selection.
- `app/src/main/java/com/ipification/demoapp/manager/ConfigManager.kt`
  - Loaded config and backend URL helpers.
- `app/src/main/java/com/ipification/demoapp/im/IMHelper.kt`
  - IM registration and sign-in helper.

## Public Repository Hygiene

Do not commit partner-specific or generated files:

- `local.properties`
- `google-services.json`
- `ipification-services.json`
- `*.apk`, `*.aab`, `*.aar`
- `.gradle/`, `.idea/`, `build/`
- `.DS_Store`

Before committing, run:

```bash
git status --short
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:compileStageDebugKotlin --no-daemon
```

## Support

For credentials, production onboarding, or environment access, contact your IPification support representative.
