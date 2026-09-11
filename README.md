<div align="center">

# nekuva-exts

**Manga source parsers for the [Nekuva](https://github.com/Neko-Koubou/nekuva) reader.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![JitPack](https://img.shields.io/jitpack/version/com.github.Neko-Koubou/nekuva-exts)](https://jitpack.io/#Neko-Koubou/nekuva-exts)
![Kotlin](https://img.shields.io/badge/Kotlin-JVM-7F52FF)

</div>

---

## About

**nekuva-exts** is a Kotlin (JVM) library of manga source parsers/scrapers used by the
**[Nekuva](https://github.com/Neko-Koubou/nekuva)** app. It contains well over a thousand
parsers spanning many languages, exposed through a small, stable contract so the host app can
fetch catalogs, details, chapters, and pages from each source.

Keeping parsers in their own repository means the UI app stays free of site-specific code, and the
source library can be versioned and published independently.

## Credits & lineage

nekuva-exts is a fork of [DokiTeam/doki-exts](https://github.com/DokiTeam), which derives from
[KotatsuApp/kotatsu-parsers](https://github.com/KotatsuApp/kotatsu-parsers). Huge thanks to the
Doki and Kotatsu teams and all upstream contributors. It is now maintained independently by
[Neko-Koubou](https://github.com/Neko-Koubou).

- **Extension issues:** <https://github.com/Neko-Koubou/nekuva-exts/issues>


## Using it (as a dependency)

nekuva-exts is published via **JitPack**. Add JitPack to your repositories and depend on a tagged
release:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.Neko-Koubou:nekuva-exts:<tag>") {
        // Android already bundles org.json in the platform; exclude it to avoid a conflict.
        exclude(group = "org.json", module = "json")
    }
}
```

> **Desktop/JVM note:** plain JVM targets do **not** bundle `org.json`. If you exclude it (as above)
> and run on a non-Android JVM, also add `implementation("org.json:json:<version>")` to that target,
> or JSON-based sources will fail at runtime with `NoClassDefFoundError`.

### The contract

The library exposes:

- `MangaParser` — the interface each source implements (`getList`, `getDetails`, `getPages`, …).
- `MangaLoaderContext` — an **abstract class the host app implements**, providing the HTTP client,
  JS evaluation, cookies/locales, and bitmap helpers the parsers need.
- The data models (`Manga`, `MangaChapter`, `MangaPage`, …) and the `@MangaSourceParser`
  annotation, whose KSP processor generates the source catalog.

The host app provides a concrete `MangaLoaderContext`; the library never talks to the platform
directly.

## Building

Requires **JDK 17**.

```bash
./gradlew build                       # compile + run tests
./gradlew publishToMavenLocal -x test # install to ~/.m2 for local consumption
```

- Group: `org.nekosukuriputo`
- Internal parser APIs are gated behind `@InternalParsersApi` and the library uses
  `explicitApiWarning()`; keep public API explicit.
- The `@MangaSourceParser` KSP processor (module `nekuva-ksp`) generates the source registry at
  build time.

## Roadmap (Kotlin Multiplatform)

nekuva-exts is currently **JVM-only** (it relies on OkHttp + Jsoup). To support Nekuva on iOS, the
networking and HTML layers will need a multiplatform path (e.g. Ktor + a multiplatform HTML
parser). This is planned for a later phase and tracked alongside Nekuva's own migration.

## License

Licensed under the **GNU General Public License v3.0**, same as the upstream projects.
See [`LICENSE`](LICENSE).

## Disclaimer

nekuva-exts does **not** host any content. It only provides code that reads publicly available,
third-party websites. It is not affiliated with any of those sites. Please support official and
licensed releases wherever they are available.
