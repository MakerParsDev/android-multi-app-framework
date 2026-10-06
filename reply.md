Fixed the two flaky expiry tests in `scripts/ci/maintenance_health_controller_test.py` by freezing the test clock and adding expiry-boundary assertions.

**Changes made:**
1. **`test_side_project_exception_expiry_uses_current_policy_schema`**: 
   - Patched `maintenance_health_controller.datetime.now()` to return a fixed date (2026-09-25)
   - Added boundary test case with `expiresOn: "2026-09-25"` (equal to frozen date) to verify `exp_date <= today` logic
   - Updated assertions: now expects 2 expired findings (2026-09-21 and 2026-09-25) instead of 1
   - Verified future-dated exception (2026-10-01) is correctly excluded

2. **`test_active_security_exceptions_include_tracking_issue`**:
   - Patched `maintenance_health_controller.datetime.now()` to return a fixed date (2026-09-25)
   - Added test cases for active (2026-10-01), boundary-active (2026-09-26, expires_on > today), and expired (2026-09-20) exceptions
   - Updated assertions: expects 3 active exceptions, verifies correct inclusion/exclusion

**Verification:**
- `python3 scripts/ci/maintenance_health_controller_test.py` ✅ (13 tests pass)
- `python3 scripts/ci/autonomous_maintenance_test.py` ✅ (20 tests pass)
- `python3 scripts/ci/sync_automerge_label_test.py` ✅ (14 tests pass)
- `python3 scripts/ci/bootstrap_autonomous_repository_settings_test.py` ✅ (15 tests pass)

Production expiry logic, security policies, and merge protections are preserved unchanged.