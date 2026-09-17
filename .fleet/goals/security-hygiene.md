# Goal: Security Hygiene & Supply Chain Hardening

Continuously audit workflow files, scripts, and code for security vulnerabilities, secret leakage risks, and unsafe execution patterns.

## Focus Areas
1. **GitHub Actions Security**: Prevent arbitrary PR code execution in privileged contexts, enforce SHA pinning, and eliminate shell injection risks in workflow `run:` steps.
2. **Secret Ownership & Exposure Controls**: Audit `config/secret-ownership.json` and ensure no secrets are exposed to untrusted runner steps or stored in artifacts.
3. **Gitleaks & Secret Scanning Policy**: Maintain `.gitleaks.toml` rules and verify zero secret leaks across repository history.
4. **Temporary File & Process Cleanup**: Ensure all scripts use secure temporary directories and explicit traps/cleanup blocks.

## Verification Commands
- `python3 scripts/ci/validate_security_pipeline.py`
- `python3 scripts/ci/validate_secret_ownership.py`
- `python3 scripts/ci/validate_secret_scan_policy.py`
- `bash scripts/ci/security_gate.sh --mode history --self-test`

## Rules
- Never weaken Gitleaks, Semgrep, CodeQL, or secret ownership rules.
- Do not request or output secret values.
