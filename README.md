# CleanroomGradle

Gradle plugin for Cleanroom Loader development and Cleanroom-targeted mod development. The current release is `0.15.0`.

## Usage

The settings plugin adds the following maven repositories:
  - Maven Central
  - CleanroomMC (proxies MinecraftForge's maven)
  - Mojang Libraries

As well as configuring the Foojay toolchain resolution.


The project plugin registers the main toolchain and development tasks.

```groovy file="settings.gradle"
pluginManagement {
    repositories {
        maven {
            url = 'https://maven.cleanroommc.com'
        }
        gradlePluginPortal()
    }
}

plugins {
    id 'com.cleanroommc.cleanroomgradle.settings' version '0.15.0'
}
```

```groovy file="build.gradle"
plugins {
    id 'java'
    id 'com.cleanroommc.cleanroomgradle'
}
```

## Modes

The plugin stays inert until an environment is registered.

Loader and standalone vanilla development still use
`cleanroom.mode = 'loader'` and `cleanroom.mode = 'vanilla'`.

Whereas a mod workspace (userdev) is activated by its dependency:

```groovy
dependencies {
    // Selects Cleanroom version 0.7.0
    implementation cleanroom.userdev('0.7.0') {
        accessTransformers.from('src/main/resources/META-INF/modid_at.cfg') // Optional
    }
}
```

## Configuration

```groovy
cleanroom {
    caches {
        // Global cache
        directory = layout.projectDirectory.dir('.gradle/cleanroom-shared')
        // 1.12.2 toolchain cache
        versionDirectory = layout.projectDirectory.dir('.gradle/cleanroom-shared/versions/1.12.2')
        // Project local generated/intermediate data
        localDirectory = layout.buildDirectory.dir('cleanroom_gradle')
        // Loader defaults false; other modes default true
        discardIntermediates = true
    }

    mappings {
        // Optional editable Tiny v2 source at <directory>/mappings.tiny
        namesDirectory = layout.projectDirectory.dir('mappings')
    }

    loader {
        accessTransformers.from('src/main/resources/META-INF/accesstransformer.cfg')
        sideAnnotationStrippers.from('src/main/resources/META-INF/side_annotation_stripper.cfg')
        intermediateRuns = true
    }
}
```

- The unsuffixed vanilla tasks always target Minecraft 1.12.2 and resolve that version through Mojang's launcher manifest into the shared cache.
- Named vanilla environments are the way to work with other Minecraft versions.
  - Available in every project mode. Override the Java launcher for a named environment with `javaVersion`.
- For several versions in one project, declare them together:

```groovy
cleanroom {
    vanilla {
        "1.4.7" {
            client {
                args '--demo'
                maxHeapSize = '2G'
            }
            server {
                args '--custom-server-argument'
            }
        }
        "26.1" {
            javaVersion = 25 // Defaults to launcher metadata otherwise
        }
    }
}
```

Letters, numbers, dots, underscores, and hyphens are accepted in environment names.

An alias can target a different version:

```groovy
cleanroom {
    vanilla {
        legacy {
            version = '1.4.7'
        }
    }
}
```

That creates `runLegacyClient` while targeting Minecraft `1.4.7`.

Named environments share downloaded assets and per-version metadata/JARs, but use isolated dependency configurations, extracted natives, and run directories.
Launcher metadata supplies each vanilla client's main class and arguments.

## Deobfuscation

Wrap a SRG-named dependency in `deobf(...)` to have it deobfuscated before reaching the classpath:

```groovy
dependencies {
    implementation deobf('net.test:other-artifact:1.0.0')
}
```

In `loader` mode `deobf(...)` cannot be declared on the main compile classpath, that is `implementation`,`compileOnly` or `api`.

Renaming there needs the SRG-named Cleanroom jar, which is built from the main compile classpath, and that would be a cycle.
Use a runtime or test configuration instead, such as `runtimeOnly`.

Kotlin DSL cannot see `deobf(...)` as a bare function inside a `dependencies` block. Use `cleanroom.deobf(...)` there instead.

`sources = true` is reserved for decompiling the renamed jar and is not implemented yet.


## Toolchain

| Area        | Tasks                                                                                                                                                                   |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Diagnostics | `cleanroomInfo`                                                                                                                                                         |
| Vanilla     | `runVanillaClient`, `runVanillaServer` (`decompile<name>` and `run<name>Client`/`Server` for named environments)                                                        |
| MCP         | `importMcpNames`                                                                                                                                                        |
| Stage Runs  | `runSrgClient`, `runSrgServer`, `runReobfSrgClient`, `runReobfSrgServer`, `runMcpClient`, `runMcpServer` enabled via `loader.intermediateRuns`                          |
| Loader      | `setup`, `runCleanroomClient`, `runCleanroomServer`, `universalJar`, `userdevJar`, `userdevSourcesJar`, `sourcesJar`, `javadocJar`, `installerJar`, `publishMmcPackZip` |
| Userdev     | `runClient`, `runServer`, `reobfJar`                                                                                                                                    |

- Run `./gradlew tasks --all` for the complete pipeline.

## Modifying Tooling

Tool configurations use defaults only while empty. Add a dependency to replace a tool without changing task wiring:

```groovy
dependencies {
    decompiler 'example:replacement-decompiler:1.0'
    mergetool 'example:replacement-merger:1.0'
    accesstransformer 'example:replacement-at:1.0'
    installertools 'example:replacement-installer-tools:1.0'
}
```

If the replacement has a different command line, configure the corresponding `MavenJarExec` task with `useDefaultToolArguments = false`, `mainClass`, and `args`.
These overrides apply while producing the loader and distributions. A published userdev artifact records its exact source-producing tool coordinates, and consuming workspaces resolve those coordinates from spec 1.


## Diagnostics

```shell
./gradlew cleanroomInfo
```

The report shows the effective mode, Minecraft version, names source, cache paths, configured/default tool versions, intermediate policy, and whether the client JAR, server JAR, and asset index are ready for offline use.

It does not resolve or download tool artifacts.

## Offline Usage

Before using userdev with `--offline`, resolve the userdev dependency online once.

For loader and vanilla work, run the relevant `setup`, download, or client task online once so the shared cache contains `version_manifest_v2.json` and the selected version's `meta.json`. `downloadAssets --offline` validates all indexed objects and reports missing/corrupt assets together with a repair command.

Configuration and task validation failures use Gradle's Problems API.
IDEs and the generated `build/reports/problems/problems-report.html` receive structured problem IDs, details, locations, and suggested fixes.

## Cache Behavior

Gradle build-cache and configuration-cache support are enabled by this project's defaults.

Expensive deterministic transforms including: decompilation, mappings, access transformation, SAS, and binpatch work declare cacheable inputs and outputs.

- `clean` deletes the build directory and `caches.localDirectory`, but preserves shared Minecraft downloads.
- `cleanCleanroomSharedCache` explicitly deletes the configured shared `caches.directory`.
- `caches.discardIntermediates` when set to `true` deletes consumed project-local pipeline artifact, cacheable tasks can restore them later.
  - A file is deleted after the consumers that ran in this build have finished.

For shared CI reuse, configure a Gradle local or remote build cache in the consuming build's `settings.gradle`; CleanroomGradle does not choose credentials or a cache server for you.

## Sources in Userdev

The dependency exposes one normal combined module. Cacheable artifact transforms remap and combine patched
Minecraft with Cleanroom, rebuild the matching patched sources, and select client or server extra resources.
Gradle module metadata carries ordinary compile, runtime, and platform dependencies. `userdev/config.json`
spec 1 carries only artifact-owned pipeline inputs, paths, identities, hashes, tools, and launch metadata.

IDE import resolves the combined jar on its first sync. No generated local Maven repository, synthetic
module version, module-copy task, or `setup` invocation is involved. Extra access transformers are transform
inputs, so changing one invalidates the combined artifact in Gradle's transform cache. IDE source lookup uses
the published `-sources.jar`, while Gradle consumers can still request the attributed sources variant directly.
