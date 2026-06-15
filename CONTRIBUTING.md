# Contributing

## Code Style

- Keep public APIs documented with KDoc.
- Prefer clear names over inline comments.
- Add comments only when they explain non-obvious SDK, network, carrier, or Android platform behavior.
- Avoid committing partner credentials, generated binaries, local IDE state, or machine-specific files.

## Development Checks

Run focused checks before opening a pull request:

```bash
./gradlew clean :ipification-auth:assembleRelease :app:assembleDebug
```

## Pull Request Checklist

- Public API changes include KDoc and sample usage updates.
- Error messages are safe for partner apps and do not expose secrets.
- Logs do not include access tokens, authorization codes, phone numbers, or full request bodies.
- New dependencies are necessary and documented.
- Generated artifacts and local configuration files are not committed.
