# AGENTS.md

Durable rules for any AI agent working in this repository. Read this before acting.
Writing them down also helps the human team agree on how we build.

## The app

Roamigo is a Kotlin/Android application for young and budget-conscious travelers.

Its main features include:

- Shared trip itineraries containing activities, bookings, tickets, and points of interest.
- A digital travel wallet for tickets and reservations.
- Map-based visualization of itinerary locations.
- Shared trips that multiple users can view or edit.
- Traveler profiles and optional nearby traveler matching.
- Google account authentication.
- Offline access to essential travel information.

The application uses Firebase to store and synchronize trips, bookings, tickets,
itineraries, expenses, user profiles, shared travel information, and
traveler-matching data.

Essential travel data is also cached locally so that it remains available offline.

The app uses:

- GPS for location-related features and optional traveler matching.
- The camera for ticket and reservation capture and QR-code scanning.

## Architecture rules

Follow the MVVM architecture.

### Model

The Model is responsible for application data and business logic.

- Represent application objects using data classes.
- Access data through repositories.
- Repository interfaces provide an abstraction between ViewModels and data sources.
- Repositories expose clean APIs for accessing and modifying application data.
- Firebase and local-storage implementation details belong behind repository
  abstractions.
- ViewModels must not need to know whether data comes from Firebase, local storage,
  a cache, or another source.
- Use `suspend` functions for asynchronous repository operations when appropriate.

### View

The View is implemented using Jetpack Compose composable functions.

- Composables display application state provided by the ViewModel.
- The View observes changes from the ViewModel and updates the UI accordingly.
- User interactions are delegated to the ViewModel.
- Do not access Firebase or repositories directly from Composables.
- Do not put business logic directly inside Composables.
- Keep Composables focused on displaying state and forwarding user actions.

### ViewModel

The ViewModel acts as the bridge between the View and the Model.

- Manage UI-related data in a lifecycle-aware way.
- Expose UI state using `StateFlow` or another appropriate observable state holder.
- Interact with repository interfaces to fetch, update, or delete data.
- Transform Model data into state suitable for display when necessary.
- Use `viewModelScope` and Kotlin coroutines for asynchronous operations.
- ViewModels must not depend directly on Firebase or other concrete data-source
  implementations.

### General architecture rules

- Keep Model, View, and ViewModel responsibilities clearly separated.
- Keep remote and local data access behind repository abstractions.
- Follow the existing project structure before introducing new architectural patterns.
- Avoid unnecessary dependencies and abstractions.
- Do not over-engineer for hypothetical future requirements.
- Do not edit generated files.

## Offline data

Roamigo must keep essential travel information available offline.

Essential offline information may include:

- Itineraries
- Booking details
- Ticket information and photos
- Hotel information
- Important addresses
- Expense information

Rules:

- Local persistence and caching belong in the Model/data layer.
- The UI must not directly decide whether data comes from Firebase or local storage.
- Repository abstractions should hide the concrete data source from the ViewModel.
- Changes made offline should be synchronized with Firebase when connectivity returns.
- Do not invent a synchronization or conflict-resolution policy unless it has been
  specified by the requirements or established by the team.

## Privacy and permissions

Roamigo handles potentially sensitive information such as precise location,
travel plans, tickets, bookings, and user profiles.

- Request Android permissions only when they are needed.
- Traveler matching and location sharing must remain opt-in.
- Do not expose a user's location or travel information unless the corresponding
  feature explicitly requires it and the user has enabled it.
- Camera access should only be used for features requiring ticket, reservation,
  or QR-code capture.
- Keep private user data separate from information intended to be visible to
  other travelers.
- Do not log sensitive user information unnecessarily.
- Respect authentication and authorization boundaries when accessing user data.

## Security

- Never commit secrets or private credentials.
- Never place privileged backend credentials inside the Android application.
- Firebase access must respect the project's authentication and authorization rules.
- Do not bypass Firebase security rules from client code.
- Validate assumptions about authenticated users before accessing user-specific data.
- Treat tickets, bookings, travel information, and precise location as sensitive data.

## Definition of done

A task or feature is considered done when:

- It satisfies its acceptance criteria.
- The implementation follows the project's architecture and coding conventions.
- New functionality is covered by appropriate tests.
- Existing tests still pass.
- `./gradlew check` passes successfully.
- The affected functionality has been manually verified when appropriate.
- Offline behavior has been checked when the feature depends on locally cached data.
- Authentication and permissions have been checked when relevant.
- No secrets, credentials, local configuration, or generated files are committed.
- The code is clear, maintainable, and ready for review.

## Implementing a user story

When implementing a user story:

1. Read and understand the user story and its acceptance criteria.
2. Determine what needs to change in:
    - Data classes
    - Repository interfaces
    - ViewModels
    - UI
    - External data-source implementations when necessary
3. Define the required classes and function interfaces before implementing them.
4. Implement the feature while respecting the MVVM separation.
5. Write appropriate tests.
6. Run `./gradlew check`.
7. Fix any test, lint, or compilation failure before requesting review.

Do not add bootcamp-specific mechanisms such as `sigchecks` unless they are
explicitly introduced into this project.

## How to work

- Work on a dedicated branch for each task.
- Follow the branch naming convention agreed upon by the team.
- Make one small, focused, reviewable change at a time.
- Do not combine unrelated changes in the same pull request.
- Push changes regularly instead of creating one large commit at the end.
- Read and understand existing code before modifying it.
- Do not over-engineer solutions for hypothetical future requirements.
- Stage only the files intended for the change.
- Be careful not to commit local configuration such as `local.properties`.
- Never commit secrets, credentials, or generated files.
- Review your own diff before requesting a code review.

## Commit conventions

Commit messages must be clear, descriptive, and focused.

- Describe what changed and, when useful, why the change was made.
- Use the imperative mood.

Good:

`Add trip creation screen`

Avoid:

`Added trip creation screen`

- Keep the subject line to 50 characters or less.
- Capitalize the first letter of the subject line.
- Avoid vague messages such as:
    - `fix`
    - `update`
    - `changes`
    - `work`
- If the subject line is not sufficient, add a blank line followed by a more
  detailed body explaining the motivation, context, and reason for the change.
- Wrap commit message body lines at 72 characters.
- Reference the related GitHub issue or ticket when relevant.

Example of a simple commit:

`Add offline itinerary cache`

Example with a body:

`Add offline itinerary cache`

`Store essential itinerary information locally so that it remains`
`available when the device has no network connection.`

`Related to #42`

### Conventional Commits

If the team chooses to use Conventional Commits, follow:

`<type>[optional scope]: <description>`

Common types include:

- `feat`
- `fix`
- `docs`
- `style`
- `refactor`
- `test`
- `chore`

Example:

`feat(itinerary): add offline cache`

When using Conventional Commits, do not capitalize the description after the
type and optional scope.

## Pull requests

- Keep each pull request focused on one task or feature.
- Do not bundle unrelated changes into the same pull request.
- Explain what was changed and why.
- Include the relevant tests.
- Run `./gradlew check` before requesting review.
- Review the diff before submitting the pull request.
- Mention important architectural decisions, assumptions, or limitations when relevant.
- Request review early enough to allow teammates to review the change properly.
- A pull request must receive at least one accepting teammate review before being merged.
- The required CI checks must pass before merging.

## AI usage

AI tools may be used to explore, draft, review, and accelerate development,
but they are not a substitute for the developer's own understanding.

- AI contributions must always be acknowledged according to the SwEnt course rules.
- Credit AI contributions using the acknowledgement convention required by the
  course, including a `Co-authored-by` line when applicable.
- Never blindly commit AI-generated code.
- Review and test AI-generated changes before submitting them.
- Make sure AI-generated code follows the project's architecture and requirements.
- Do not accept architectural changes proposed by an AI without understanding
  their consequences.
- Developers must understand and be able to explain everything they submit.

## Your role

The human developer provides the goal, context, acceptance criteria, and permissions.

The AI agent may plan, propose, implement, or review changes, but the human
developer reviews the result and owns every line submitted to the repository.
"The agent wrote it" is not a defence.