# Benchmark replication protocol (issue #451)

Status: **exploratory methodology**, not a protected CI gate, a CodSpeed
performance verdict, or a production acceptance threshold.

## Measurement and provenance

- Before reviewing scores, choose the target benchmark(s), minimum practical
  performance effect, family size, alpha, power, maximum **independent**
  workflow-run budget and a **fixed** sample count. Do not stop selectively
  after observing an apparently favorable result.
- For a source comparison, independently confirm benchmark JMH source trees,
  Gradle benchmark configuration, JMH mode and sampling configuration.
  Source files that define a benchmark must not differ unless establishing a
  new baseline. Application code under test may legitimately differ.
- Group runs by *observed* CPU model, image revision, JDK, OS/architecture and
  visible CPU allocation using the merged runner-provenance comparator.
  Matching labels do **not** prove that physical hosts, contention or
  frequency control match. Rejected runs must remain reported with their
  reasons; never select only favorable measured results.
- Capture full GitHub Actions logs and raw JMH evidence from the already
  merged `Report sanitized JMH samples` step, plus exact workflow-run ID and
  commit/tree identifiers. Observe that CodSpeed's separate profiler score
  is **not** the JMH `primaryMetric.score`. Preserve external failing
  checks, even when their CPU/image is different.
- One fresh JVM/workflow execution is one independent **run**. The one fork
  and three iterations within an ordinary hosted JMH action run are nested
  measurements, **not three independent replicates**. The local AMD2700X
  experiment used two forks × six measurement iterations per run, and is
  a different JMH `avgt` mode from CodSpeed `cs`.

## Pilot, planning, and decision rule

1. Collect enough independent environment-matched hosted runs **per source
   group** to characterize between-run variation; 10 per group is an
   *illustrative planning goal*, not a fixed magic number. If a matched cohort
   cannot be assembled without excessive retries, report **inconclusive**.
2. Use a log transformation of the **independent run means**, not every
   iteration as a separate observation. Decide the exact response variable
   and method **before** analyzing the confirmatory runs. Different host
   strata or incompatible sampling modes must not be pooled without a
   preregistered hierarchical method.
3. For 15 endpoints, start from familywise alpha 0.05; a conservative
   Bonferroni two-sided test uses alpha **0.05 / 15** for each comparison.
   For one preregistered primary endpoint, family size may instead be one;
   changing this after looking at results is not acceptable.
4. Use the *independent hosted pilot* to estimate between-run log standard
   deviation. Under an **illustrative equal-variance, independent two-arm,
   normal-approximation design**, required independent runs per **arm** are
   estimated by
   `ceil(2 * (z_(1-alpha/(2K)) + z_power)^2 *
   (sigma_log / log(1+effect))^2)`. This uses a two-sided difference test,
   not proof of practical equivalence. A small pilot makes variance and
   sample-size estimates unstable.
5. Predefine a minimum practically relevant effect. A confidence interval
   that merely excludes zero is **not** proof of a regression larger than
   the practical margin. For a meaningful slowdown exceeding +5%, a
   suitably corrected log-ratio interval would need its *lower* endpoint
   above `log(1.05)`. Demonstrating equivalence/no meaningful change needs
   a separately designed equivalence test and interval contained within
   declared margins. Otherwise mark the result **inconclusive**.
6. Use a fixed sample count/analysis and review allocation completeness
   before interpreting results. An exploratory flagged difference is
   grounds for investigation, not an automatic production/merge decision.
   Never disable or acknowledge CodSpeed failures, silently change CI
   thresholds, or purchase dedicated runners.

## Local ten-run pilot (21 October 2026)

The connected `amd2700x-ubuntu` machine ran **10 separate JVM launches** of
the same unchanged benchmark JAR, JDK 21.0.12.1, JMH `avgt` with two forks
and six measured iterations per fork. Across-run CV: **4.564%** for
`inlineFeedAdLayout` and **3.750%** for `formatCountdown`. Host
1-minute load average during the seven new launches ranged **3.91–7.01**,
the `schedutil` CPU governor was unchanged. These are **not** estimates of
CodSpeed hosted `cs` variance.

Use the standard-library-only script against the original ten independent
raw local JMH JSON files:

```bash
python3 benchmarks/tools/plan_jmh_repetitions.py \
  /tmp/amf-451-local-strong-run{1,2,3}-20261021.json \
  /tmp/amf-451-ryzen-repeat-20261021/run{4,5,6,7,8,9,10}.json \
  --benchmark-tree f4e521dc5ec6bad6272c107c65ae3edc345f25e0 \
  --host-label amd2700x-ubuntu --family-size 15 --json
```

The explicit tree SHA and host label are **user-supplied statements**, not
authenticated proofs; verify them against the Git repository and runner logs.

Illustrative independent runs **per arm**, assuming alpha 0.05 across
15 endpoints, desired power 0.8, two-sided test, and a change of:

| Local benchmark | 2% | 5% | 10% |
|---|---:|---:|---:|
| `inlineFeedAdLayout` | 143 | 24 | 7 |
| `formatCountdown` | 97 | 16 | 5 |

With a **1.5× multiplier on pilot log standard deviation** (only a
sensitivity scenario, *not* an upper confidence limit), the corresponding
independent runs per arm become **321/53/14** and **217/36/10**. In a
balanced two-arm study, total independent runs are **twice** the per-arm
number. Hosted CPU-image matching, variance and the cost of finding enough
matching runs are **unknown**. These numbers must never be presented as
CodSpeed's true reproducibility or automatically used as a CI policy.

Official CodSpeed documentation warns that Walltime on ordinary hosted
runners can be inconsistent and that its Java/JMH integration supports
Walltime mode; it does not establish drop-in JVM simulation support:
- https://codspeed.io/changelog/2026-05-06-java-support
- https://github.com/CodSpeedHQ/codspeed

**Follow-up:** gather independent, properly matched hosted pilot data as
resources and repository protections permit. Re-estimate sample requirements
from those actual hosted runs and report inconclusive results transparently.
