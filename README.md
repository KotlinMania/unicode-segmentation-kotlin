# unicode-segmentation-kotlin in Kotlin

[![GitHub link](https://img.shields.io/badge/GitHub-KotlinMania%2Funicode--segmentation--kotlin-blue.svg)](https://github.com/KotlinMania/unicode-segmentation-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/unicode-segmentation-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/unicode-segmentation-kotlin)
[![Build status](https://img.shields.io/github/actions/workflow/status/KotlinMania/unicode-segmentation-kotlin/ci.yml?branch=main)](https://github.com/KotlinMania/unicode-segmentation-kotlin/actions)

This is a Kotlin Multiplatform line-by-line transliteration port of [`unicode-rs/unicode-segmentation`](https://github.com/unicode-rs/unicode-segmentation).

**Original Project:** This port is based on [`unicode-rs/unicode-segmentation`](https://github.com/unicode-rs/unicode-segmentation). All design credit and project intent belong to the upstream authors; this repository is a faithful port to Kotlin Multiplatform with no behavioural changes intended.

### Porting status

This is an **in-progress port**. The goal is feature parity with the upstream Rust crate while providing a native Kotlin Multiplatform API. Every Kotlin file carries a `// port-lint: source <path>` header naming its upstream Rust counterpart so the AST-distance tool can track provenance.

---

## Upstream README — `unicode-rs/unicode-segmentation`

> The text below is reproduced and lightly edited from [`https://github.com/unicode-rs/unicode-segmentation`](https://github.com/unicode-rs/unicode-segmentation). It is the upstream project's own description and remains under the upstream authors' authorship; links have been rewritten to absolute upstream URLs so they continue to resolve from this repository.

Iterators which split strings on Grapheme Cluster, Word, or Sentence boundaries,
according to the
[Unicode Standard Annex #29](https://www.unicode.org/reports/tr29/) rules.

[![Build Status](https://github.com/unicode-rs/unicode-segmentation/actions/workflows/rust.yml/badge.svg)](https://github.com/unicode-rs/unicode-segmentation/actions/workflows/rust.yml)

[Documentation](https://docs.rs/unicode-segmentation)

```rust
use unicode_segmentation::UnicodeSegmentation;

fn main() {
    let s = "a̐éö̲\r\n";
    let g = s.graphemes(true).collect::<Vec<&str>>();
    let b: &[_] = &["a̐", "é", "ö̲", "\r\n"];
    assert_eq!(g, b);

    let s = "The quick (\"brown\") fox can't jump 32.3 feet, right?";
    let w = s.unicode_words().collect::<Vec<&str>>();
    let b: &[_] = &["The", "quick", "brown", "fox", "can't", "jump", "32.3", "feet", "right"];
    assert_eq!(w, b);

    let s = "The quick (\"brown\")  fox";
    let w = s.split_word_bounds().collect::<Vec<&str>>();
    let b: &[_] = &["The", " ", "quick", " ", "(", "\"", "brown", "\"", ")", "  ", "fox"];
    assert_eq!(w, b);
}
```

## no_std

unicode-segmentation does not depend on libstd, so it can be used in crates
with the `#![no_std]` attribute.

# crates.io

You can use this package in your project by adding the following
to your `Cargo.toml`:

```toml
[dependencies]
unicode-segmentation = "1"
```

# Change Log

## 1.13.2

* [#164](https://github.com/unicode-rs/unicode-segmentation/pull/164) Set explicit 1.85 MSRV
* [#147](https://github.com/unicode-rs/unicode-segmentation/pull/147) Add ascii fast path for unicode_word_indices and unicode_words
* [#157](https://github.com/unicode-rs/unicode-segmentation/pull/157) Support Unicode 17.0.0

## 1.13.0, 1.13.1

Yanked due to accidental breakage and MSRV mistag.

## 1.12.0

* [#131](https://github.com/unicode-rs/unicode-segmentation/pull/131) Implement Debug on all public structs
* [#136](https://github.com/unicode-rs/unicode-segmentation/pull/136) Use stdlib alphabetic and numeric character tables
* [#138](https://github.com/unicode-rs/unicode-segmentation/pull/138) Fix arithmetic overflow
* [#137](https://github.com/unicode-rs/unicode-segmentation/pull/137) Fix unwrap panic in next_boundary()
* [#140](https://github.com/unicode-rs/unicode-segmentation/pull/140) Support Unicode 16.0.0


## 1.11.0
* [#124](https://github.com/unicode-rs/unicode-segmentation/pull/124) Update data to Unicode 15.1
* [#128](https://github.com/unicode-rs/unicode-segmentation/pull/128) Add `size_hint` to iterators

## 1.10.1
* [#113](https://github.com/unicode-rs/unicode-segmentation/pull/113) Use criterion.rs for word benchmarks
* [#112](https://github.com/unicode-rs/unicode-segmentation/pull/112) Improve table search speed through lookups

## 1.10.0
* [#107](https://github.com/unicode-rs/unicode-segmentation/pull/107) Upgrade to Unicode 15.0.0
* [#104](https://github.com/unicode-rs/unicode-segmentation/pull/104) Supersedes and fixes [#75](https://github.com/unicode-rs/unicode-segmentation/pull/75)

## 1.9.0
* [#101](https://github.com/unicode-rs/unicode-segmentation/pull/101) Upgrade to Unicode 14.0.0

## 1.8.0
* [#100](https://github.com/unicode-rs/unicode-segmentation/pull/100) Increase `#[inline]` opportunities, resulting in 15-40% performance improvement.
* [#95](https://github.com/unicode-rs/unicode-segmentation/pull/98) Implement debug for Graphemes
* [#94](https://github.com/unicode-rs/unicode-segmentation/pull/94) Add Initial fuzzer for oss-fuzz integration
* [#93](https://github.com/unicode-rs/unicode-segmentation/pull/93) Fix  unused imports and deprecated pattern warnings
* [#92](https://github.com/unicode-rs/unicode-segmentation/pull/92) Made local variable immutable by moving it into loop
* [#91](https://github.com/unicode-rs/unicode-segmentation/pull/91) Add new iterator [UnicodeWordIndices](https://docs.rs/unicode-segmentation/latest/unicode_segmentation/struct.UnicodeWordIndices.html) and [unicode_word_indices](https://docs.rs/unicode-segmentation/latest/unicode_segmentation/trait.UnicodeSegmentation.html#tymethod.unicode_word_indices)

## 1.7.1

* Update docs on version number

## 1.7.0

* [#87](https://github.com/unicode-rs/unicode-segmentation/pull/87) Upgrade to Unicode 13
* [#79](https://github.com/unicode-rs/unicode-segmentation/pull/79) Implement a special-case lookup for ascii grapheme categories
* [#77](https://github.com/unicode-rs/unicode-segmentation/pull/77) Optimization for grapheme iteration

## 1.6.0

* [#72](https://github.com/unicode-rs/unicode-segmentation/pull/72) Upgrade to Unicode 12

## 1.5.0

* [#68](https://github.com/unicode-rs/unicode-segmentation/pull/68) Upgrade to Unicode 11

## 1.4.0

* [#56](https://github.com/unicode-rs/unicode-segmentation/pull/56) Upgrade to Unicode 10

## 1.3.0

* [#24](https://github.com/unicode-rs/unicode-segmentation/pull/24) Add support for sentence boundaries
* [#44](https://github.com/unicode-rs/unicode-segmentation/pull/44) Treat `gc=No` as a subset of `gc=N`

## 1.2.1

* [#37](https://github.com/unicode-rs/unicode-segmentation/pull/37):
  Fix panic in `provide_context`.
* [#40](https://github.com/unicode-rs/unicode-segmentation/pull/40):
  Fix crash in `prev_boundary`.

## 1.2.0

* New `GraphemeCursor` API allows random access and bidirectional iteration.
* Fixed incorrect splitting of certain emoji modifier sequences.

## 1.1.0

* Add `as_str` methods to the iterator types.

## 1.0.3

* Code cleanup and additional tests.

## 1.0.1

* Fix a bug affecting some grapheme clusters containing Prepend characters.

## 1.0.0

* Upgrade to Unicode 9.0.0.

---

## About this Kotlin port

### Installation

```kotlin
dependencies {
    implementation("io.github.kotlinmania:unicode-segmentation-kotlin:0.1.1")
}
```

### Building

```bash
./gradlew build
./gradlew test
```

### Targets

- macOS arm64
- Linux x64
- Windows mingw-x64
- iOS arm64 / simulator-arm64 (Swift export + XCFramework)
- JS (browser + Node.js)
- Wasm-JS (browser + Node.js)
- Android (API 24+)

### Porting guidelines

See [AGENTS.md](AGENTS.md) and [CLAUDE.md](CLAUDE.md) for translator discipline, port-lint header convention, and Rust → Kotlin idiom mapping.

### License

This Kotlin port is distributed under the same MIT license as the upstream [`unicode-rs/unicode-segmentation`](https://github.com/unicode-rs/unicode-segmentation). See [LICENSE](LICENSE) (and any sibling `LICENSE-*` / `NOTICE` files mirrored from upstream) for the full text.

Original work copyrighted by the unicode-segmentation authors.  
Kotlin port: Copyright (c) 2026 Sydney Renee and The Solace Project.

### Acknowledgments

Thanks to the [`unicode-rs/unicode-segmentation`](https://github.com/unicode-rs/unicode-segmentation) maintainers and contributors for the original Rust implementation. This port reproduces their work in Kotlin Multiplatform; bug reports about upstream design or behavior should go to the upstream repository.
