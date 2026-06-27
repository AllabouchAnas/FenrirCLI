# Contributing to FenrirCLI

First off, thank you for taking the time to contribute to FenrirCLI! 🎉 Contributions from the community make this open-source terminal launcher better for everyone.

The following guidelines outline how you can contribute, report issues, and build the project successfully.

---

## 🛠️ Getting Started

### Prerequisites
To build and run FenrirCLI, you will need:
* **Android Studio** (Koala or newer recommended)
* **JDK 17** configured in your project structure
* **Android SDK** (API Level 34 or higher recommended)

### Build Environment Setup
1. **Fork and Clone the repo:**
   ```bash
   git clone https://github.com/<your-username>/FenrirCLI.git
   cd FenrirCLI
   ```
2. **Open the project in Android Studio.** Let Gradle sync completely.
3. **Build the project:**
   You can compile the project using the terminal or Gradle task pane:
   ```bash
   ./gradlew compileDebugKotlin
   ```

---

## 🐛 Reporting Bugs

If you find a bug or unexpected behavior:
1. Check the [Issues tracker](https://github.com/AllabouchAnas/FenrirCLI/issues) to ensure it hasn't already been reported.
2. If it's a new issue, open a new bug report.
3. Be sure to include:
   * A clear, descriptive title.
   * Steps to reproduce the issue.
   * Expected vs. actual behavior.
   * Your device model and Android version.
   * Relevant logs (use `adb logcat` if possible).

---

## 💡 Suggesting Features

We welcome new features! If you have ideas for new terminal commands, keyboard layout improvements, or theme options:
1. Open a new issue with the label `enhancement` or `feature request`.
2. Explain the use case and why it would be beneficial to users.
3. Provide mockups or CLI command syntax examples if applicable.

---

## 🎨 Development & Style Guide

### Code Guidelines
- **Kotlin Standard**: Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- **Compose Best Practices**: Use Compose best practices (e.g., wrap callbacks in `rememberUpdatedState` when needed, keep states stable, and separate UI components from VM logic).
- **Theme Palette**: FenrirCLI uses the **Nord theme**. Make sure any new components, keys, or logs leverage the theme colors defined in the `com.terminal.rootnode.ui.theme` package.

### Architecture
FenrirCLI follows the standard MVVM design pattern:
- **`TerminalViewModel.kt`**: Contains the core business logic, command parser, aliases/macros, database interactions, and app listings.
- **`TerminalScreen.kt`**: Contains the terminal UI wrapper, output list renderer, and focus/caret logic.
- **`keyboard/`**: Houses all custom keyboard components and controls.

---

## 🤝 Pull Request (PR) Process

1. **Create a branch** from `main`:
   ```bash
   git checkout -b feature/my-amazing-feature
   ```
2. **Write clean, documented code.** Include comments for complex logic.
3. **Ensure it compiles locally:**
   ```bash
   ./gradlew assembleDebug
   ```
4. **Commit your changes** with descriptive commit messages.
5. **Push to your fork** and open a Pull Request against our `main` branch.
6. Provide a description of the changes in the PR description, along with screenshots if the UI was modified.
