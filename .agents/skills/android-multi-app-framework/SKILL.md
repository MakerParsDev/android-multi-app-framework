```markdown
# android-multi-app-framework Development Patterns

> Auto-generated skill from repository analysis

## Overview

This skill teaches the core development patterns and workflows for contributing to the `android-multi-app-framework` repository. It covers coding conventions, file organization, workflow automation (especially around CI), and testing practices. By following these guidelines, contributors can ensure consistency, maintainability, and reliability in the codebase.

## Coding Conventions

### File Naming

- Use **snake_case** for all Python files and scripts.
  - **Example:** `multi_app_manager.py`, `build_tools_test.py`

### Import Style

- Use **relative imports** within the package.
  - **Example:**
    ```python
    from .utils import load_config
    ```

### Export Style

- Use **named exports** (explicitly define what is exported).
  - **Example:**
    ```python
    # In multi_app_manager.py
    __all__ = ['MultiAppManager', 'AppConfig']
    ```

## Workflows

### Add or Update CI Workflow

**Trigger:** When introducing a new CI workflow or modifying an existing one for Android builds, tests, or security.  
**Command:** `/add-ci-workflow`

1. **Edit or create** a workflow YAML file in `.github/workflows/`.
2. **Update or add** supporting scripts in `scripts/ci/` (Python or shell).
3. **Update or add** corresponding test scripts in `scripts/ci/` (files ending with `_test.py` or `_test.sh`).
4. **Optionally update** related documentation.

**Example:**
```yaml
# .github/workflows/android-ci.yml
name: Android CI

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run build script
        run: python scripts/ci/build_android.py
```

### Document or Plan CI Workflow

**Trigger:** When proposing, designing, or documenting a new CI workflow or improvement.  
**Command:** `/document-ci-workflow`

1. **Create or update** a plan/spec document in `docs/superpowers/plans/` or `docs/superpowers/specs/`.
2. **Optionally add or update** documentation in `.github/workflows/README.md` or related docs.

**Example:**
```markdown
# docs/superpowers/plans/android_ci_plan.md

## Objective
Describe the goals and requirements for the new Android CI workflow...
```

### Test Enhancement for CI Workflow

**Trigger:** When adding or changing a CI workflow and needing to ensure its correctness via automated tests.  
**Command:** `/add-ci-workflow-test`

1. **Edit or create** test scripts in `scripts/ci/` (e.g., `build_android_test.py`, `deploy_test.sh`).
2. **Ensure tests cover** new or changed workflow logic.

**Example:**
```python
# scripts/ci/build_android_test.py

def test_build_success():
    result = run_build_script()
    assert result == 0
```

## Testing Patterns

- **Test files** follow the pattern `*_test.py` or `*_test.sh` and are located in `scripts/ci/`.
- **Testing framework** is not explicitly specified; use standard Python `assert` statements or shell exit codes.
- **Test coverage** should focus on verifying the logic of CI-related scripts and workflows.

**Example:**
```python
# scripts/ci/deploy_test.py

def test_deploy_returns_zero():
    assert deploy() == 0
```

## Commands

| Command                | Purpose                                                      |
|------------------------|--------------------------------------------------------------|
| /add-ci-workflow       | Add or update a CI workflow and its supporting scripts       |
| /document-ci-workflow  | Propose or document a new or improved CI workflow            |
| /add-ci-workflow-test  | Add or update tests for CI workflows and scripts             |
```
