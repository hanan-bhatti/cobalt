# Contributing to Second Brain

Welcome! We are excited that you want to help make **Second Brain** better. 

Please note that Second Brain is currently a solo-developer project and is in an early public beta phase (`v0.9.0-beta01`). Because of this, the underlying architecture, database schemas, and APIs may still undergo structural changes. We ask for your patience and coordination as we work towards a stable `v1.0.0` release.

---

## Ways to Contribute

There are several ways you can contribute to the project:
*   **Bug Reports:** Identify bugs or crashes and submit detailed issues with steps to reproduce and system logs.
*   **Feature Suggestions:** Propose new features or UX improvements that align with the app's minimalist aesthetic.
*   **Documentation Improvements:** Fix typos, clarify build setup instructions, or translate guides.
*   **Code Contributions (Pull Requests):** Implement feature requests, refactor existing code, or optimize background operations.

---

## Development Setup

Before writing any code, please configure your local development environment. 

To prevent duplicate documentation, please refer directly to the [Installation & Build](README.md#installation--build) section in the main project `README.md` for details on:
*   Required JDK 21 / Android SDK setups
*   Firebase integration (`google-services.json`)
*   Environmental variables and API keys configuration (`.env` file)

---

## Branch Naming Conventions

To keep our repository organized, please name your local branches using the following prefixes:
*   `feature/` for new features or feature components (e.g., `feature/custom-stickers`)
*   `fix/` for bug fixes or crash resolutions (e.g., `fix/ocr-timeout`)
*   `docs/` for modifications to text documentation (e.g., `docs/contributing-guidelines`)
*   `refactor/` for structural code cleanup (e.g., `refactor/viewmodel-split`)
*   `chore/` for build scripts, gradle configurations, or dependency bumps (e.g., `chore/version-bump`)

---

## Commit Message Conventions

We adhere to the [Conventional Commits](https://www.conventionalcommits.org/) specification. This coordinates directly with our Keep a Changelog automated processes. Please format your commit messages as follows:

```
<type>(<scope>): <short description>
```

### Allowed Types:
*   `feat`: A new user-facing feature.
*   `fix`: A bug fix.
*   `docs`: Documentation changes only.
*   `style`: Formatting, missing semi-colons, etc. (no production code changes).
*   `refactor`: A code change that neither fixes a bug nor adds a feature.
*   `test`: Adding missing tests or correcting existing tests.
*   `chore`: Changes to the build process, tooling, or auxiliary libraries.

*Example:* `feat(ocr): add region selection canvas to screen capture activity`

---

## Code Style Expectations

### 1. Kotlin Style & Design Guidelines
*   We adhere to the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
*   All code should remain clean, self-documenting, and properly commented.

### 2. Architecture & Design Patterns
*   **MVVM Pattern:** Maintain a strict separation of concerns. UI Composables read state flows from ViewModels, which in turn interface with Repositories. Directly querying the database or services from the UI layer is not permitted.
*   **State Flows:** UI states should be exposed as read-only `StateFlow` structures inside ViewModels using `asStateFlow()` or standard mapping utilities.

### 3. Jetpack Compose Conventions
*   **State Hoisting:** Hoist states where appropriate to keep composables reusable and testable.
*   **Previews:** Provide a `@Preview` composable with light/dark theme wrappers for all standalone components.
*   **Naming:** Name Composable functions as nouns and capitalize them (e.g., `OcrOverlayUI`).
*   **Performance:** Avoid heavy computations inside Composable bodies; use `remember` or derive values within the ViewModel.

---

## Pull Request Process

1.  **Fork the Repository:** Create a personal fork of the project on GitHub.
2.  **Create your Branch:** Build your feature or fix on a branch using the [naming conventions](#branch-naming-conventions) above.
3.  **Check for Warnings:** Ensure your project compiles cleanly without any compiler or packaging warnings.
4.  **Write Tests:** Add unit tests (e.g., JVM or Roborazzi UI tests) if you are adding new features or patching critical logic.
5.  **Submit the PR:** Target your PR against the `main` branch of the upstream repository.
6.  **Include Context:** Fill out the pull request template completely, outlining:
    *   What problem does this PR solve?
    *   What structural changes were made?
    *   How was it tested? Include screenshots/screencasts for UI updates.

---

## Code of Conduct

All contributors are expected to uphold a respectful, collaborative atmosphere. By participating in this project, you agree to abide by our [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Please report any unacceptable behavior to the maintainer.
