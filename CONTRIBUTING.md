# Contributing to HAFMessenger

Thank you for your interest in contributing! This document provides guidelines for contributing to HAFMessenger.

## Getting Started

1. **Fork** the repository
2. **Clone** your fork
3. **Run** the bootstrap script to set up your local environment:

   ```bash
   ./scripts/bootstrap-local-dev.sh
   ```

4. **Create a branch** for your work:

   ```bash
   git checkout -b feature/your-feature-name
   ```

## Development Setup

See [docs/setup/LOCAL_DEV.md](docs/setup/LOCAL_DEV.md) for full local development setup instructions.

### Prerequisites

- Java 25+
- Maven (wrapper included: `./mvnw`)
- MySQL 8.0+
- OpenSSL
- keytool (included with JDK)

## Making Changes

### Code Style

- Follow existing code conventions in the project
- All public methods must have Javadoc comments
- Keep methods focused and reasonably sized

### Testing

- Add tests for new functionality
- Ensure all existing tests pass:

  ```bash
  ./mvnw test
  ```

- Server tests require a configured environment:

  ```bash
  HAF_SERVER_ENV_FILE=.local/hafmessenger/server/variables.env ./mvnw -pl server test
  ```

### Commit Messages

- Use clear, descriptive commit messages
- Start with a verb in the imperative mood (e.g., "Add", "Fix", "Update")
- Reference issue numbers when applicable

## Submitting Changes

1. **Push** your branch to your fork
2. **Open a Pull Request** against `main`
3. **Fill out** the PR template
4. **Wait** for review - maintainers will provide feedback

## Security

- **Never** commit secrets, certificates, or private keys
- **Never** commit `.env` files or `variables.env`
- Always use the bootstrap script to generate local secrets
- See [SECURITY.md](SECURITY.md) for vulnerability reporting

## Code of Conduct

Be respectful and constructive in all interactions. We are all here to learn and build.

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
