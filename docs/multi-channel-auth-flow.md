# Multi-Channel Authentication Flow

This version is optimized for Mermaid Chart / mermaid.ai sharing. It avoids long loop-back arrows so the diagram renders compactly.

## High-Level Flow

```mermaid
flowchart TD
    A["Client calls startAuthentication()"] --> B["Read AUTH_CHANNELS priority list"]
    B --> C["Try channels in configured order"]

    C --> T["1. Try TS43"]
    T --> T_RESULT{"TS43 result"}

    T_RESULT -- "Success" --> OK_TS43["Return success: TS43 verified"]
    T_RESULT -- "Fallback error" --> I["2. Try IP if configured"]
    T_RESULT -- "Non-fallback error" --> ERR_TS43["Return TS43 error"]

    I --> I_RESULT{"IP result"}
    I_RESULT -- "Success" --> OK_IP["Return success: IP auth code or token"]
    I_RESULT -- "Fallback error" --> S["3. Try SMS if configured"]
    I_RESULT -- "Token exchange error" --> S["3. Try SMS if configured"]

    NOTE_TOKEN_FALLBACK["Note: token exchange failures fallback only when another channel is configured. If no next channel exists, return the token error."]
    T_RESULT -.-> NOTE_TOKEN_FALLBACK
    I_RESULT -.-> NOTE_TOKEN_FALLBACK

    S --> S_RESULT{"SMS / OTP result"}
    S_RESULT -- "OTP initiated" --> OTP["Return onOTPRequired(auth_req_id, nonce)"]
    S_RESULT -- "SMS auth failed" --> ERR_SMS["Return SMS error if no next channel"]

    OTP --> USER["Client shows OTP input"]
    USER --> VERIFY["Client calls verifySMSOTP()"]
    VERIFY --> V_RESULT{"OTP verify result"}
    V_RESULT -- "Success" --> OK_SMS["Return success: SMS verified"]
    V_RESULT -- "Failure" --> ERR_OTP["Return OTP verification error"]

    classDef success fill:#d9fdd3,stroke:#2e7d32,color:#1b5e20;
    classDef error fill:#ffe0e0,stroke:#c62828,color:#8e0000;
    classDef fallback fill:#fff4cc,stroke:#f9a825,color:#6d4c00;
    classDef process fill:#e3f2fd,stroke:#1565c0,color:#0d47a1;
    classDef note fill:#f5f5f5,stroke:#616161,color:#212121,stroke-dasharray: 5 5;
    classDef note fill:#f5f5f5,stroke:#616161,color:#212121,stroke-dasharray: 5 5;
    classDef note fill:#f5f5f5,stroke:#616161,color:#212121,stroke-dasharray: 5 5;

    class OK_TS43,OK_IP,OK_SMS success;
    class ERR_TS43,ERR_SMS,ERR_OTP error;
    class T_RESULT,I_RESULT,S_RESULT,V_RESULT fallback;
    class A,B,C,T,I,S,OTP,USER,VERIFY process;
    class NOTE_TOKEN_FALLBACK note;
```

## TS43 Decision Details

```mermaid
flowchart TD
    T0["TS43 selected"] --> T1{"Backend URL and paths configured?"}
    T1 -- "No" --> E1["Return error immediately: config required"]
    T1 -- "Yes" --> T2{"CredentialManager available?"}

    T2 -- "No" --> F1["Fallback to next channel"]
    T2 -- "Yes" --> T3["Call TS43 /auth"]

    T3 --> T4{"/auth result"}
    T4 -- "Network/auth/unknown error" --> F2["Fallback to next channel"]
    T4 -- "Config/client error" --> E2["Return TS43 error"]
    T4 -- "Success" --> T5["Launch Credential Manager"]

    T5 --> T6{"Credential Manager result"}
    T6 -- "Success" --> T7["Exchange TS43 token"]
    T6 -- "User cancelled" --> F3["Fallback to next channel"]
    T6 -- "No credential" --> F4["Fallback to next channel"]
    T6 -- "Error/interrupted/invalid" --> F5["Fallback to next channel"]

    T7 --> T8{"Token exchange success?"}
    T8 -- "Yes" --> OK["Return TS43 success"]
    T8 -- "No" --> F6["Fallback to next channel"]

    NOTE_TS43_TOKEN["Note: TS43 token exchange failure falls back to the next configured channel. If TS43 is last, return the token error."]
    F6 -.-> NOTE_TS43_TOKEN

    classDef success fill:#d9fdd3,stroke:#2e7d32,color:#1b5e20;
    classDef error fill:#ffe0e0,stroke:#c62828,color:#8e0000;
    classDef fallback fill:#fff4cc,stroke:#f9a825,color:#6d4c00;
    classDef process fill:#e3f2fd,stroke:#1565c0,color:#0d47a1;
    classDef note fill:#f5f5f5,stroke:#616161,color:#212121,stroke-dasharray: 5 5;
    classDef note fill:#f5f5f5,stroke:#616161,color:#212121,stroke-dasharray: 5 5;

    class OK success;
    class E1,E2 error;
    class F1,F2,F3,F4,F5,F6 fallback;
    class T0,T3,T5,T7 process;
    class NOTE_TS43_TOKEN note;
```

## IP Decision Details

```mermaid
flowchart TD
    I0["IP selected"] --> I1["Start IP auth over cellular"]
    I1 --> I2{"IP auth success?"}
    I2 -- "No" --> F1["Fallback to next channel"]
    I2 -- "Yes" --> I3{"IP_TOKEN_URL configured?"}

    I3 -- "No" --> OK1["Return success: IP auth code"]
    I3 -- "Yes" --> I4["Exchange IP code for token"]
    I4 --> I5{"Token exchange success?"}
    I5 -- "Yes" --> OK2["Return success: IP token response"]
    I5 -- "No" --> F2["Fallback to next channel"]

    NOTE_IP_TOKEN["Note: IP token exchange failure falls back to the next configured channel. If IP is last, return the token error."]
    F2 -.-> NOTE_IP_TOKEN

    classDef success fill:#d9fdd3,stroke:#2e7d32,color:#1b5e20;
    classDef error fill:#ffe0e0,stroke:#c62828,color:#8e0000;
    classDef fallback fill:#fff4cc,stroke:#f9a825,color:#6d4c00;
    classDef process fill:#e3f2fd,stroke:#1565c0,color:#0d47a1;
    classDef note fill:#f5f5f5,stroke:#616161,color:#212121,stroke-dasharray: 5 5;

    class OK1,OK2 success;
    class E1 error;
    class F1,F2 fallback;
    class I0,I1,I4 process;
    class NOTE_IP_TOKEN note;
```

## SMS / OTP Decision Details

```mermaid
flowchart TD
    S0["SMS selected"] --> S1{"Callback is MultiAuthCallback?"}
    S1 -- "No" --> F1["Fallback to next channel or return error if SMS is last"]
    S1 -- "Yes" --> S2{"Valid login_hint phone number?"}

    S2 -- "No" --> F2["Fallback to next channel or return error if SMS is last"]
    S2 -- "Yes" --> S3["Call SMS /sms/auth"]

    S3 --> S4{"/sms/auth success?"}
    S4 -- "No" --> E1["Return SMS error if no next channel"]
    S4 -- "Yes" --> O1["Return onOTPRequired(auth_req_id, nonce)"]

    O1 --> O2["Client shows OTP input"]
    O2 --> O3["Client calls verifySMSOTP()"]
    O3 --> O4{"OTP verification success?"}
    O4 -- "Yes" --> OK["Return SMS success"]
    O4 -- "No" --> E2["Return OTP verification error"]

    classDef success fill:#d9fdd3,stroke:#2e7d32,color:#1b5e20;
    classDef error fill:#ffe0e0,stroke:#c62828,color:#8e0000;
    classDef fallback fill:#fff4cc,stroke:#f9a825,color:#6d4c00;
    classDef process fill:#e3f2fd,stroke:#1565c0,color:#0d47a1;

    class OK success;
    class E1,E2 error;
    class F1,F2 fallback;
    class S0,S3,O1,O2,O3 process;
```

## Summary

- The SDK tries channels in the order configured by `AUTH_CHANNELS`.
- A channel success stops the flow and returns success to the client.
- A fallback error moves to the next configured channel.
- A non-fallback error returns immediately. IP token exchange failure falls back when another channel is configured.
- SMS requires `MultiAuthCallback` because OTP is a two-step flow: start SMS auth, then verify OTP.
