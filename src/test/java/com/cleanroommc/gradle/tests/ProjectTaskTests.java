package com.cleanroommc.gradle.tests;

import com.cleanroommc.gradle.CleanroomLogger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

@TestMethodOrder(OrderAnnotation.class)
public class ProjectTaskTests {

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        testInfo.getTestMethod().ifPresent(m -> CleanroomLogger.log("TEST PHASE >> {}", m.getName()));
    }

    @Test
    @Order(1)
    public void testDefaults() {
        // Assert default maven repos
        Assertions.assertTrue(ProjectTestInstance.getProject().getRepositories().stream().anyMatch(ar -> ar.getName().equals("Minecraft")));
        Assertions.assertTrue(ProjectTestInstance.getProject().getRepositories().stream().anyMatch(ar -> ar.getName().equals("CleanroomMC")));
    }

    @Test
    @Order(2)
    public void testTasks() {
        Tasks.executeAllTasks();
    }

}
