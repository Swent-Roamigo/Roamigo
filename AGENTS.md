# AGENTS.md

This file defines the conventions AI agents must follow when contributing to Roamigo.

Before making changes, read the relevant issue and the existing code. Keep changes focused on the requested task and avoid speculative features or unrelated refactors.

## Product scope

Roamigo helps groups of friends plan, coordinate, and experience trips together.

The core experience includes:
- shared itineraries and points of interest,
- live group voting,
- group location sharing and an interactive trip map,
- geolocated photos and shared trip memories.

Do not introduce unrelated features unless they have been explicitly approved by the team.

## Architecture

Roamigo follows MVVM.

- Compose screens render UI state and forward user actions.
- ViewModels contain presentation logic and expose UI state.
- Repositories abstract local, Firebase, and external data sources.
- UI and ViewModels should not depend directly on Firebase or Mapbox implementation details.
- Inject repository dependencies into ViewModels through interfaces. Production entry points should provide real implementations; reserve fakes and sample data for tests, previews, or an explicitly selected demo mode.
- Repository operations callable from the UI must be safe on the main thread, including response reading and parsing. Validate external data before use and handle failures and retries where appropriate.

Firebase Authentication handles Google Sign-In.
Cloud Firestore stores persistent trip data.
Firebase Realtime Database handles frequently changing data such as live locations.
Firebase Storage stores shared photos.
Mapbox provides map and location visualization.

## Kotlin and code conventions

Write idiomatic and readable Kotlin.

- Use `PascalCase` for classes and types.
- Use `camelCase` for functions, properties, and variables.
- Prefer meaningful and descriptive names.
- Follow the existing project formatting and Kotlin conventions.
- Keep code simple and avoid unnecessary abstractions.
- Avoid duplicated logic when a clear reusable abstraction is appropriate.
- Comments should explain why non-obvious logic exists, not restate what the code does.
- Use KDoc where public APIs or contracts require documentation.
- Use Android resources for user-facing and accessibility text, including plurals where appropriate. Prefer standard Compose components and semantics; test tags do not replace accessible labels.
- Check that changed screens remain usable on small displays and in landscape, including access to primary actions.
- Keep visible actions functional or use the existing feedback for unfinished actions. Verify the navigation-to-data path before describing a feature as integrated.

Follow the style of the surrounding code rather than introducing a new convention.

## Testing

New behavior should include appropriate tests.

Before requesting review:
- run the relevant Gradle tests and checks,
- verify that existing behavior is not broken,
- keep tests focused on observable behavior, meaningful boundaries, failures, and recovery,
- inject fakes or controlled clients into unit and UI tests; do not make live billable network calls by default,
- use isolated emulators for relevant Firebase integrations and distinguish emulator, device, and live-service verification,
- reuse the existing test-tag convention and report checks actually run and their limitations.

## Git workflow

Develop each Sprint task on its own branch.

Keep commits small and focused.
Commit subjects should be short and use the imperative mood.

Example:

`Add shared trip voting`

Use a commit body when useful to explain what changed and why, not how the implementation works.

## Pull requests

- Keep each pull request focused on one task. Aim for roughly 300 lines of production changes; if larger, consider coherent, independently useful steps. Do not omit validation, tests, or necessary integration just to meet a line target; explain justified larger changes.
- Do not bundle unrelated changes into the same pull request.
- Use `.github/pull_request_template.md` when creating a pull request, and replace its issue placeholder with a closing reference such as `Closes #123` only when the issue is completed; otherwise use a non-closing reference.
- Explain what was changed and why. Check the final diff against the description, including changes to model types and any incomplete behavior.
- Include the relevant tests.
- Run `./gradlew check` before requesting review.
- Review the diff before submitting the pull request.
- Mention important architectural decisions, assumptions, or limitations when relevant.
- Request review early enough to allow teammates to review the change properly.
- Code reviews must be written in English.
- A pull request must receive at least one accepting teammate review before being merged.
- The required CI checks must pass before merging.

## Privacy and permissions

Location sharing must be optional and visible to the user.
Request camera and location permissions only when required.
Trip locations, photos, and other private trip data must only be accessible to authorized trip members.

## AI contributions

AI assistance must be acknowledged according to the SwEnt collaboration policy.

The developer must review, understand, and be able to justify every submitted change.
AI-generated code must follow the same architecture, testing, and quality standards as human-written code.
