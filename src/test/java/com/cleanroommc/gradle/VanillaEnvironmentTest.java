package com.cleanroommc.gradle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaEnvironmentTest {

    @TempDir
    Path projectDir;

    private PluginBuild project;

    @BeforeEach
    void setup() throws IOException {
        this.project = new PluginBuild(this.projectDir).settings();
    }

    @Test
    void namedEnvironmentsRegisterSuffixedTasks() throws IOException {
        this.project.build("""
                import com.cleanroommc.gradle.api.task.mc.RunMinecraft

                cleanroom {
                    mode = com.cleanroommc.gradle.api.ext.ProjectMode.USERDEV
                    userdev {
                        version = 'test-version'
                    }
                    vanilla {
                        "1.12" {
                            client {
                                args '--custom-client'
                                maxHeapSize = '3G'
                            }
                            server {
                                args '--custom-server'
                            }
                        }
                        "26.1" {
                            javaVersion = 25
                        }
                    }
                }
                gradle.projectsEvaluated {
                    assert cleanroom.vanilla.named('1.12').get().version.get() == '1.12'
                    assert cleanroom.vanilla.named('26.1').get().version.get() == '26.1'
                    assert tasks.named('run1.12Client', RunMinecraft).get().args == ['--custom-client']
                    assert tasks.named('run1.12Client', RunMinecraft).get().maxHeapSize == '3G'
                    assert tasks.named('run1.12Server', RunMinecraft).get().args == ['--custom-server']
                    assert tasks.findByName('run26.1Client') != null
                    assert tasks.findByName('download1.12ClientJar') != null
                    assert configurations.findByName('vanilla1.12') != null
                }
                """);

        var first = this.project.runner("tasks", "--all").build();
        assertTrue(first.getOutput().contains("run1.12Client"));
        assertTrue(first.getOutput().contains("download1.12Assets"));
        assertTrue(first.getOutput().contains("run26.1Client"));

        PluginBuild.reused(this.project.runner("tasks", "--all").build().getOutput());
    }

    @Test
    void invalidNameUsesProblemsApi() throws IOException {
        this.project.vanilla("""
                cleanroom.vanilla {
                    "../escape" { }
                }
                """);

        var result = this.project.runner("help").buildAndFail();
        assertTrue(result.getOutput().contains("Invalid vanilla environment name '../escape'"));
        this.project.assertProblem("invalid-vanilla-environment");
    }

}
