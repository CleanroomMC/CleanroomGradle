# CleanroomGradle

Gradle plugin for Cleanroom and Cleanroom-targeted mod development.

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
    id 'com.cleanroommc.cleanroomgradle.settings' version '0.6.0'
}

// build.gradle
plugins {
    id 'java'
    id 'com.cleanroommc.cleanroomgradle'
}
```
