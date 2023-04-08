package com.cleanroommc.gradle.test.vanilla

import com.cleanroommc.gradle.TestFoundation
import com.cleanroommc.gradle.task.ManifestTasks
import org.junit.jupiter.api.Test

import java.nio.file.Path

class GatherAndReadManifestTest extends TestFoundation {

    @Override
    void appendBuildScript(Path buildFile) {
        buildFile <<
                """
                task gatherAndReadManifestTest {
                    dependsOn '${ManifestTasks.READ_MANIFEST}'
                    doLast {
                        def file = project.tasks.getByName('${ManifestTasks.GATHER_MANIFEST}').getDest()
                        assert file.exists() : "Manifest does not exist: \${file}"
                        def output = project.tasks.getByName('${ManifestTasks.READ_MANIFEST}').output
                        assert output != null : "Manifest json not parsed"
                    }
                }
                """
    }

    @Test
    void test() {
        def result = gradleRunner().withArguments('gatherAndReadManifestTest').build()
        isSuccessful('gatherAndReadManifestTest', result)
    }

}
