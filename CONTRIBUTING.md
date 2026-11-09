# Contributing to Git Store

Thank you for your interest in contributing to Git Store! This document provides guidelines for contributing to the project.

## Getting Started

1. Fork the repository
2. Clone your fork: `git clone https://github.com/Darkmintis/GitStore.git`
3. Open the project in Android Studio and sync Gradle

## Development Setup

### Requirements
- JDK 17 or higher
- Android Studio (latest stable version)
- Git

## Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions small and focused

## Commit Guidelines

We follow conventional commit format:

```
type(scope): description

[optional body]
```

### Types:
- `feat`: New feature
- `fix`: Bug fix
- `perf`: Performance improvement
- `refactor`: Code refactoring
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

### Examples:
```
feat(auth): add GitHub OAuth login
fix(home): resolve trending repos loading issue
perf(database): optimize repository queries
docs: update README with new features
```

## Pull Request Process

1. Create a new branch: `git checkout -b feature/your-feature-name`
2. Make your changes
3. Test thoroughly
4. Commit with descriptive messages
5. Push to your fork: `git push origin feature/your-feature-name`
6. Open a Pull Request with a clear description

### PR Checklist:
- [ ] Code builds successfully
- [ ] No lint errors
- [ ] Tested on Android device/emulator
- [ ] Updated documentation if needed
- [ ] Follows code style guidelines

## Reporting Issues

When reporting bugs, please include:
- Android version
- Device model
- Steps to reproduce
- Expected vs actual behavior
- Screenshots if applicable

## Feature Requests

We welcome feature suggestions! Please:
- Check if it's already requested
- Explain the use case
- Describe the proposed solution

## License

By contributing, you agree that your contributions will be licensed under Apache License 2.0.

---

Thank you for contributing to Git Store! 🚀
