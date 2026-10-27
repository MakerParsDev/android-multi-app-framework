# ContentApp

## CI/CD

This repository uses GitHub Actions for CI/CD. Azure Pipelines have been fully removed.

Start with:

- `.github/workflows/` for all CI/CD workflow definitions.
- `docs/CI_CD.md` for the current CI/CD operating model.

## Performance benchmarks

JVM microbenchmarks for the Android-free logic (qibla math, prayer-time
scheduling, location matching, log sanitizing, ad policies) live in
`benchmarks/` and can be executed manually with JMH. Hosted CodSpeed comparisons were
retired because unmanaged walltime runner differences produced unreliable PR
regression signals; these measurements are not required CI gates.

```bash
git submodule update --init --recursive
./gradlew -p benchmarks jmh
```
