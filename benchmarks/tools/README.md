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
