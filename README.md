# CleanroomGradle

Gradle plugin for Cleanroom Loader development and Cleanroom-targeted mod development. The current release is `0.9.1`.

## Applying the plugin

The settings plugin adds the Cleanroom, Forge, Mojang, and Maven Central dependency repositories and configures Foojay toolchain resolution. The project plugin registers the Minecraft toolchain and development tasks.

```groovy
// settings.gradle
pluginManagement {
    repositories {
        maven {
            url = 'https://maven.cleanroommc.com'
        }
        maven {
            url = 'https://maven.minecraftforge.net/'
        }
        gradlePluginPortal()
    }
}

plugins {
    id 'com.cleanroommc.cleanroomgradle.settings' version '0.9.1'
}
```

```groovy
// build.gradle
plugins {
    id 'java'
    id 'com.cleanroommc.cleanroomgradle'
}
```

## Project Mode

Choose the pipeline explicitly for predictable task registration:

```groovy
import com.cleanroommc.gradle.api.ext.ProjectMode

cleanroom {
    mode = ProjectMode.USERDEV // VANILLA, LOADER, or default: USERDEV
    version = '0.7.0-alpha'
}
```

| Mode      | Purpose                                                                            |
|-----------|------------------------------------------------------------------------------------|
| `VANILLA` | Vanilla download, run, decompile, and shared MCP facilities only                   |
| `LOADER`  | Cleanroom loader sources, SAS/AT processing, run tasks, and distribution artifacts |
| `USERDEV` | Mod workspace backed by `cleanroomVersion` or a `cleanroomUserdev` dependency      |

The default is `USERDEV`, since mod development is the primary use case. Loader development and standalone vanilla tooling must select `LOADER` or `VANILLA` explicitly.

`USERDEV` requires one of:

```groovy
cleanroom {
    version '0.7.0-alpha'
}

// or
dependencies {
    cleanroomUserdev 'com.cleanroommc:cleanroom:0.7.0:userdev@jar'
}
```

## Useful Configuration

```groovy
cleanroom {
    // Optional cache overrides, shared downloads use Gradle user home by default
    cacheDirectory = layout.projectDirectory.dir('.gradle/cleanroom-shared')
    versionCacheDirectory = layout.projectDirectory.dir('.gradle/cleanroom-shared/versions/1.12.2')

    // Project-local generated/intermediate data
    localCacheDirectory = layout.buildDirectory.dir('cleanroom_gradle')

    // Loader defaults false; other modes default true
    discardIntermediates = true

    // Optional editable Tiny v2 source at <directory>/mappings.tiny
    namesDirectory = layout.projectDirectory.dir('mappings')

    accessTransformers.from('src/main/resources/META-INF/accesstransformer.cfg')
    sideAnnotationStrippers.from('src/main/resources/META-INF/side_annotation_stripper.cfg')
}
```

Use `-Pmc=<version>` to select another Minecraft version through Mojang's launcher manifest. Use `-Pcleanroom.vanillaJava=<major>` to override the Java launcher selected for vanilla Minecraft.

The `mc` property remains a shortcut for selecting the version used by the original unsuffixed tasks. Named vanilla environments are available in every project mode, including the default `USERDEV` mode. For several versions in one project, declare them together:

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
            javaVersion = 25 // optional - defaults to launcher metadata
        }
    }
}
```

An environment name defaults to its Minecraft version and becomes part of its task names: 
The example creates `run1.4.7Client`, `run1.4.7Server`, `decompile1.4.7`, `run26.1Client`, and their version-specific download tasks.
Dots are valid in Gradle task names, so quoted version strings are kept exactly.
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

That creates `runLegacyClient` while caching the Minecraft data under version `1.4.7`.
Named environments share downloaded assets and per-version metadata/JARs, but use isolated dependency configurations, extracted natives, and run directories.
Launcher metadata supplies each vanilla client's main class and arguments.
Compatibility is still ultimately constrained by the launch protocol and Java requirements of the selected Minecraft version.

## Entry-point tasks

| Area        | Tasks                                                                                                                                    |
|-------------|------------------------------------------------------------------------------------------------------------------------------------------|
| Diagnostics | `cleanroomInfo`                                                                                                                          |
| Vanilla     | `decompileVersion`, `runVanillaClient`, `runVanillaServer`, plus `decompile<name>` and `run<name>Client`/`Server` for named environments |
| MCP/loader  | `runSrgClient`, `runSrgServer`, `runMcpClient`, `runMcpServer`, `importMcpNames`                                                         |
| Loader      | `setup`, `runCleanroomClient`, `runCleanroomServer`, `universalJar`, `userdevJar`, `javadocJar`, `publishMmcPackZip`                              |
| Userdev     | `setup`, `runClient`, `runServer`, `decompileDevJar`, `reobfJar`                                                                |

Run `./gradlew tasks --all` for the complete pipeline.

## Diagnostics and Offline Use

```shell
./gradlew cleanroomInfo
```

The report shows the effective mode, Minecraft version, names source, cache paths, configured/default tool versions, intermediate policy, and whether the client JAR, server JAR, and asset index are ready for offline use.
It does not resolve or download tool artifacts.

Before using `--offline`, run the relevant setup or client task online once.
`downloadAssets --offline` validates all indexed objects and reports missing/corrupt assets together with a repair command.
Configuration and task validation failures use Gradle's Problems API where Gradle exposes it, so IDEs and the generated `build/reports/problems/problems-report.html` receive structured problem IDs, details, locations, and suggested fixes.

## Cache Behavior

Gradle build-cache and configuration-cache support are enabled by this project's defaults.
Expensive deterministic transforms including: decompilation, mappings, access transformation, SAS, and binpatch work declare cacheable inputs and outputs.

- `clean` deletes the build directory and `localCacheDirectory` as it preserves shared Minecraft downloads.
- `cleanCleanroomSharedCache` explicitly deletes the configured shared `cacheDirectory`.
- `cleanroom.discardIntermediates=true` deletes consumed project-local pipeline artifacts - cacheable tasks can restore them later.

For shared CI reuse, configure a Gradle local or remote build cache in the consuming build's `settings.gradle`; CleanroomGradle does not choose credentials or a cache server for you.

## Replacing Tools

Tool configurations use defaults only while empty. Add a dependency to replace a tool without changing task wiring:

```groovy
dependencies {
    decompiler 'example:replacement-decompiler:1.0'
    mergetool 'example:replacement-merger:1.0'
    mcinjector 'example:replacement-injector:1.0'
    accesstransformer 'example:replacement-at:1.0'
    installertools 'example:replacement-installer-tools:1.0'
}
```

If the replacement has a different command line, configure the corresponding `MavenJarExec` task with `useDefaultToolArguments = false`, `mainClass`, and `args`.
