# Security

## Reporting

Please report suspected vulnerabilities through your IPification partner support contact. Do not create public issues for security-sensitive findings.

## Sensitive Data Rules

The SDK and sample app should not log or commit:

- Access tokens, ID tokens, authorization codes, or VP tokens.
- Full phone numbers or MSISDN values.
- Client secrets, partner credentials, or backend credentials.
- Partner-specific Firebase or service configuration files.

When adding diagnostics, prefer redacted values and short identifiers that are enough to troubleshoot without exposing user or partner data.
