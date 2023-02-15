package com.cleanroommc.gradle.tests;

import com.cleanroommc.gradle.CleanroomLogger;
import org.junit.jupiter.api.*;

public class TestRuns {

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CleanClientTest {
        @BeforeEach
        void beforeEach(TestInfo testInfo) {
            testInfo.getTestMethod().ifPresent(m -> CleanroomLogger.log("TEST PHASE >> {}", m.getName()));
        }

        @Test
        @Order(1)
        void executeTasks() {
            Tasks.runTasks(tasks -> {
                //tasks.runClearCacheTask();
                tasks.runDownloadManifest();
                tasks.runDownloadVersion();
                tasks.runGrabAssets();
                tasks.runDownloadClientTask();
                tasks.runPrepareDependenciesTask();
            });
        }

        @Test
        @Order(2)
        void testRunCleanClient() {
            Runs.executeRun(Runs::runCleanClient);
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CleanServerTest {
        @BeforeEach
        void beforeEach(TestInfo testInfo) {
            testInfo.getTestMethod().ifPresent(m -> CleanroomLogger.log("TEST PHASE >> {}", m.getName()));
        }

        @Test
        @Order(1)
        void executeTasks() {
            Tasks.runTasks(tasks -> {
                //tasks.runClearCacheTask();
                tasks.runDownloadManifest();
                tasks.runDownloadVersion();
                tasks.runDownloadServerTask();
                tasks.runPrepareDependenciesTask();
            });
        }

        @Test
        @Order(2)
        void testRunCleanServer() {
            Runs.executeRun(Runs::runCleanServer);
        }
    }

}
