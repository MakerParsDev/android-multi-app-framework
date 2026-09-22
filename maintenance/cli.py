"""Main CLI Entrypoint for Autonomy Control Plane."""

import sys
import argparse
from maintenance.policy import AutonomyPolicy
from maintenance.render import project_all_surfaces
from maintenance.canary import execute_canary
from maintenance.security import render_security_issue_body, evaluate_security_issue_state
from maintenance.health import HealthReporter

def main() -> int:
    parser = argparse.ArgumentParser(description="Autonomous Maintenance Control Plane CLI")
    subparsers = parser.add_subparsers(dest="command", help="Subcommand to run")

    # generate
    subparsers.add_parser("generate", help="Generate derived maintenance surfaces from canonical autonomy policy")

    # check
    subparsers.add_parser("check", help="Check generated surfaces for drift against canonical policy")

    # canary
    parser_canary = subparsers.add_parser("canary", help="Execute event-driven canary test")
    parser_canary.add_argument("canary_id", help="Canary ID to run (e.g. kotlin-codeql, firebase-stream-json)")

    # security-issue-sync
    subparsers.add_parser("security-issue-sync", help="Sync machine-owned security tracking Issue #183 state")

    # health
    subparsers.add_parser("health", help="Render autonomy observability metrics and health report")

    args = parser.parse_args()

    if args.command == "generate":
        print("Generating derived maintenance surfaces from config/autonomy-policy.yaml...")
        results = project_all_surfaces(write=True)
        for path, drifted in results.items():
            print(f"  {'Updated' if drifted else 'Up to date'}: {path}")
        return 0

    elif args.command == "check":
        print("Checking derived maintenance surfaces for drift...")
        results = project_all_surfaces(write=False)
        has_drift = False
        for path, drifted in results.items():
            if drifted:
                print(f"  [DRIFT DETECTED] {path}")
                has_drift = True
            else:
                print(f"  [OK] {path}")
        if has_drift:
            print("Drift detected! Run 'python3 -m maintenance generate' to fix.")
            return 1
        print("All generated surfaces match canonical policy.")
        return 0

    elif args.command == "canary":
        print(f"Executing canary '{args.canary_id}'...")
        res = execute_canary(args.canary_id)
        print(f"Canary '{res.canary_id}' result: Success={res.success}")
        print(f"Details: {res.details}")
        return 0 if res.success else 1

    elif args.command == "security-issue-sync":
        policy = AutonomyPolicy.load()
        state = evaluate_security_issue_state(policy)
        body = render_security_issue_body(policy)
        print(f"Issue #183 State Evaluation: Open={state['should_be_open']} ({state['active_blocker_count']} active blockers)")
        print("\n--- Rendered Issue Body Preview ---\n")
        print(body[:300] + "...\n")
        return 0

    elif args.command == "health":
        policy = AutonomyPolicy.load()
        reporter = HealthReporter(policy)
        report = reporter.generate_report()
        print(report["markdown"])
        return 0

    else:
        parser.print_help()
        return 0

if __name__ == "__main__":
    sys.exit(main())
