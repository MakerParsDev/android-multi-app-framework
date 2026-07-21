# Doppler Secret Materialization Wrapper Design

## Goal

Provide one repository-level command wrapper that runs any command through Doppler while safely materializing binary/file-backed secrets into private temporary files and deleting them when the command exits.

## Context

The Doppler `prod` configuration stores canonical secret material in:

- `KEYSTORE_BASE64`
- `PLAY_SERVICE_ACCOUNT_JSON_BASE64`

The current `KEYSTORE_FILE` and `PLAY_SERVICE_ACCOUNT_JSON` Doppler values are machine-specific paths and do not exist on `ops-vps-5`. Existing Azure scripts decode the base64 values independently, which duplicates logic and does not provide one consistent local/VPS execution path.

## Approaches Considered

### 1. Extend `scripts/azure/doppler-run.sh`

This is the smallest edit, but it leaves a general repository function under an Azure-specific path and makes local/VPS usage conceptually dependent on Azure.

### 2. Add a common wrapper and keep the Azure wrapper as an adapter — selected

Create `scripts/doppler-run.sh` as the canonical implementation. It runs `doppler run`, validates and decodes supported base64 secrets, exports generated file paths, executes the requested command, and cleans up. `scripts/azure/doppler-run.sh` can remain compatible and delegate to the common wrapper in a later focused change.

This provides a clear repository-wide command without forcing unrelated pipeline edits in the first implementation.

### 3. Generate persistent files in a repository-local secrets directory

This simplifies repeated builds but increases the risk of stale credentials, accidental disclosure, permission drift, and cleanup failures. It is rejected.

## Command Interface

```bash
scripts/doppler-run.sh -- <command> [arguments...]
```

Examples:

```bash
scripts/doppler-run.sh -- ./gradlew verifyReleaseSigning
scripts/doppler-run.sh -- ./gradlew publishAmenerrasuluReleaseBundle
scripts/doppler-run.sh -- env
```

The wrapper uses the directory-scoped Doppler project/config by default. Explicit `DOPPLER_PROJECT` and `DOPPLER_CONFIG` environment variables may override the defaults.

## Data Flow

1. Validate that `doppler` is installed and a command was provided.
2. Create a private temporary directory with mode `0700`.
3. Execute an internal materializer under `doppler run` so secret values never appear in shell arguments or logs.
4. When `KEYSTORE_BASE64` is present:
   - remove whitespace;
   - strictly decode it;
   - require a non-empty output file;
   - set mode `0600`;
   - export `KEYSTORE_FILE` to the generated path.
5. When `PLAY_SERVICE_ACCOUNT_JSON_BASE64` is present:
   - remove whitespace;
   - strictly decode it;
   - parse it as JSON;
   - require `type=service_account`, `project_id`, `client_email`, and `private_key`;
   - set mode `0600`;
   - export `PLAY_SERVICE_ACCOUNT_JSON` to the generated path.
6. Execute the requested command with the full Doppler environment plus generated path variables.
7. Preserve the wrapped command's exit status.
8. Delete the temporary directory on success, failure, interruption, and termination.

## Existing File-Path Values

Generated paths always override Doppler-provided `KEYSTORE_FILE` and `PLAY_SERVICE_ACCOUNT_JSON` when their corresponding base64 values are present. This prevents stale machine-specific paths from winning.

If a base64 value is absent, the wrapper leaves an existing file-path variable unchanged. This preserves local development workflows that intentionally use local files.

## Security Requirements

- Never print secret values or decoded file contents.
- Never pass secret values as process arguments.
- Temporary directory permissions must be `0700`.
- Materialized file permissions must be `0600`.
- Base64 decoding must fail closed.
- Service-account JSON must be structurally validated before command execution.
- Cleanup must use a shell trap and run for normal exit and common termination signals.
- Temporary files must be outside the repository.

## Error Handling

The wrapper exits before running the requested command when:

- Doppler CLI is unavailable;
- no wrapped command is supplied;
- temporary directory creation fails;
- either base64 value is malformed;
- decoded keystore material is empty;
- decoded service-account JSON is invalid or missing required fields.

Error messages identify the failing variable but do not include secret values.

## Testing

Create a shell integration test that uses a fake `doppler` executable and synthetic fixtures. It verifies:

- command execution receives both generated file paths;
- decoded bytes and service-account JSON are correct;
- permissions are `0600`;
- generated paths override stale Doppler paths;
- malformed base64 prevents command execution;
- invalid service-account JSON prevents command execution;
- temporary files are removed after success;
- temporary files are removed after wrapped-command failure;
- wrapped-command exit status is preserved.

After unit/integration tests pass, run a real Doppler smoke test that reveals only validation status, file existence, and permissions—not values or contents.

## Files

- Create `scripts/doppler-run.sh`: canonical wrapper and materializer.
- Create `scripts/ci/test_doppler_run.sh`: self-contained integration test.
- Update `docs/SECRETS_SETUP.md`: repository-wide usage and canonical source guidance.

## Out of Scope

- Removing Doppler keys in this change.
- Refactoring all Azure and GitHub pipeline scripts.
- Publishing or signing an actual application bundle.
- Changing secret values.
