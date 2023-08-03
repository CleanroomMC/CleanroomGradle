package com.cleanroommc.gradle.test.vanilla

import com.cleanroommc.gradle.TestFoundation
import com.cleanroommc.gradle.task.MinecraftTasks
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

import java.nio.file.Path

@Disabled
class GatherAndReadManifestTest extends TestFoundation {

    @Override
    void appendBuildScript(Path buildFile) {
        buildFile <<
                """
                task gatherAndReadManifestTest {
                    dependsOn '${MinecraftTasks.PREPARE_NEEDED_MANIFESTS}'
                    doLast {
                        def file = project.tasks.getByName('${MinecraftTasks.GATHER_MANIFEST}').getDest()
                        assert file.exists() : "Manifest does not exist: \${file}"
                        def output = project.tasks.getByName('${MinecraftTasks.PREPARE_NEEDED_MANIFESTS}').output
                        assert output != null : "Manifest json not parsed"
                    }
                }
                """
    }

    @Test
    void test() {
        createTest(gradleRunner -> {
            gradleRunner.setTaskName('gatherAndReadManifestTest')
            gradleRunner.configureTests(tests -> {
                tests.isSuccessful()
            })
        })
    }

}
