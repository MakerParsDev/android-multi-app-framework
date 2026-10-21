# CodSpeed runner provenance comparison

Issue [#451](https://github.com/MakerParsDev/android-multi-app-framework/issues/451) established that GitHub-hosted runs of the **same commit** can receive a different CPU model or Ubuntu runner image. This offline, standard-library-only helper compares the nonsecret metadata printed by `.github/workflows/codspeed.yml` (merged in #452).

Save the **complete** logs from two runs (GitHub CLI uses the account authorized to read the repository):

```bash
gh run view RUN_ID_1 -R MakerParsDev/android-multi-app-framework --log > /tmp/codspeed-run-1.log
gh run view RUN_ID_2 -R MakerParsDev/android-multi-app-framework --log > /tmp/codspeed-run-2.log

python3 benchmarks/tools/compare_runner_provenance.py \
  /tmp/codspeed-run-1.log /tmp/codspeed-run-2.log --json
python3 -m unittest discover -s benchmarks/tools -p 'test_*.py'
```

Output status and exit codes:

- `metadata_matched` (0): CPU model, visible processors, JDK, OS, architecture and image revision match. **This is not proof of statistically comparable measurements**: runtime load, microcode, CPU frequency and JMH variation can differ.
- `environment_mismatch` (2): at least one of those recorded attributes differs; avoid asserting performance causality from percentages alone.
- `incomplete` (3): one or both logs lack required fields (e.g., pre-#452 logs); no comparison claim.
- Input/read/conflicting-data errors also exit 3.

The helper reads local log files only. It makes no GitHub API requests, modifies no CI gates and never changes benchmark parameters or suppresses CodSpeed failures. Use it for evidence review, **not** as an authorization to override branch protections. For numeric significance, inspect the original JMH sample data and run repeated same-environment measurements.

The script is for runner diagnostics, not a CodSpeed substitute. It does not parse or compare the rounded `cs` summary as if it were high-resolution benchmark data.


## Descriptive variation in local JMH JSON

For **independent, same-source/same-host** JMH runs, inspect the original JMH
JSON files directly:

```bash
python3 benchmarks/tools/summarize_jmh_runs.py \
  /tmp/amf-451-local-strong-run1-20261021.json \
  /tmp/amf-451-local-strong-run2-20261021.json \
  /tmp/amf-451-local-strong-run3-20261021.json --json
```

The JMH analyzer checks benchmark name sets, units, measurement modes,
warmup/measurement settings **and the JMH/JDK/VM versions, JVM arguments,
thread/fork/batch settings and raw sample counts**. It rejects missing or
truncated raw fork/iteration samples, duplicate run JSON (including copied
files) and incompatible JVM configurations rather than silently combining
numbers. It then calculates descriptive min, max,
average, (max–min)/min, across-run coefficient of variation, and reports
samples per run. This requires full JMH JSON files, not only summary rows. It cannot verify that input files were measured on the same CPU or
at the same source revision; check that separately. The three-run example
is exploratory and not a statistical proof of a regression or an estimate of
CodSpeed's hosted Walltime noise floor.


## Numeric JMH evidence in existing benchmark job logs

The optional diagnostic step in `.github/workflows/codspeed.yml` prints only
allowlisted numeric JMH `primaryMetric.score` and per-fork `rawData`
values and safe benchmark labels from the local
`benchmarks/build/results/jmh/results.json`. The JMH fork already writes this
file during a benchmark run. No new network request, GitHub artifact retention,
secrets, JVM arguments or source paths are published.

Look for the `jmh_evidence_status` and `jmh_evidence=` prefixed records
under the **Report sanitized JMH samples** step. The step always returns
success, even if an upstream benchmark failure left no valid JSON; it never
overrides the original CodSpeed job/check result.

**Important:** these are JMH-generated measurements, **not** CodSpeed's
separate profiler-based performance-analysis scores. They may be useful for
debugging sample completeness and run-to-run variation only. Do not infer an
equivalent CodSpeed Walltime delta or relax the required checks based on them.


## Compare JMH evidence from complete GitHub Actions logs

For any two **independent** runs containing the diagnostic step added in #457:

```bash
gh run view BASE_RUN_ID -R MakerParsDev/android-multi-app-framework --log > /tmp/base.log
gh run view HEAD_RUN_ID -R MakerParsDev/android-multi-app-framework --log > /tmp/head.log
python3 benchmarks/tools/compare_jmh_action_logs.py \
  /tmp/base.log /tmp/head.log --json
```

By default the analyzer **refuses to calculate a percentage**. It checks
complete `jmh_evidence_status=available` counts and every bounded numeric
record, compares CPU model, runner image, JVM, processor allocation,
benchmark set/mode/unit and per-fork sample lengths. Invalid/missing data,
copied/identical log files, and mismatched environments fail closed.

If the benchmark source trees were independently verified via the GitHub
commit history, supply their exact Git SHA-1 tree IDs:

```bash
git rev-parse BASE_COMMIT:benchmarks/src/jmh
git rev-parse HEAD_COMMIT:benchmarks/src/jmh
python3 benchmarks/tools/compare_jmh_action_logs.py \
  /tmp/base.log /tmp/head.log \
  --base-benchmark-tree BASE_TREE_SHA \
  --head-benchmark-tree HEAD_TREE_SHA --json
```

A `descriptive_review_only` result (exit 0) means runner metadata,
benchmark source tree IDs, sample shapes and units *appear* compatible.
The script computes only a **descriptive head-versus-base JMH percentage**;
it cannot independently authenticate user-supplied Git tree IDs or prove the
host was otherwise stable. It does not calculate a p-value, confidence
interval, or equivalent CodSpeed profiler performance score. More raw
samples and repeated independent runs are required for a justified
regression conclusion. A mismatch returns exit 2; missing/unverifiable
evidence returns exit 3. Do not wire this helper into required CI checks.
