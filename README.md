# ContentApp

[![CodSpeed](https://img.shields.io/endpoint?url=https://codspeed.io/badge.json)](https://app.codspeed.io/MakerParsDev/android-multi-app-framework?utm_source=badge)

## CI/CD

This repository uses GitHub Actions for CI/CD. Azure Pipelines have been fully removed.

Start with:

- `.github/workflows/` for all CI/CD workflow definitions.
- `docs/CI_CD.md` for the current CI/CD operating model.

## Performance benchmarks

JVM microbenchmarks for the Android-free logic (qibla math, prayer-time
scheduling, location matching, log sanitizing, ad policies) live in
`benchmarks/` and are tracked continuously with [CodSpeed](https://codspeed.io)
by `.github/workflows/codspeed.yml`.

```bash
git submodule update --init --recursive
./gradlew -p benchmarks jmh
```
