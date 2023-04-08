# CleanroomGradle

## Development
#### Setup
1. Clone this Project: `git clone https://github.com/CleanroomMC/CleanroomGradle.git`
2. Preferably using IntelliJ IDEA.
   - File => Open => (Location of Cloned Source) => Import `build.gradle`
#### Attaching Gradle API Sources:

1. Follow setup steps above
2. Run the `generateGradleSource` gradle task
3. Locate any gradle-api classes
4. Attach sources found at the path `userdir/.gradle/caches/{version}/generated-gradle-jars/gradle-api-{version}-sources.jar`

- Many thanks to [klokwrk-project](https://github.com/croz-ltd/klokwrk-project/blob/master/modules/tool/klokwrk-tool-gradle-source-repack)
for providing an easy script to repack gradle-api.