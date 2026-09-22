/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.schema.UserdevConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;

class DeobfExtensionTest extends BaseFunctionalTest {

    private static final String RESOLVE_TASK = """
            tasks.register('resolveDeobf') {
                def classpath = configurations.compileClasspath.incoming.files
                inputs.files(classpath)
                doLast { classpath.each { println 'FILE ' + it.name; println 'PATH ' + it.absolutePath } }
            }
            """;

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void loaderModeRejectsDeobfOnTheCompileClasspath(boolean declaredLate) throws IOException {
        var declaration = "dependencies { implementation deobf('net.test:mod:1.0.0') }";
        var body = declaredLate ? "afterEvaluate { " + declaration + " }" : declaration;
        this.project.loader("repositories { %s }\n%s\n".formatted(fixture(), body) + RESOLVE_TASK);

        var output = this.project.runner("resolveDeobf", "--offline").buildAndFail().getOutput();
        assertThat(output).as(output).contains("cannot be declared on the 'compileClasspath' hierarchy in loader mode");
        this.project.assertProblem("deobf-on-compile-classpath");
    }

    @Test
    void loaderModeAllowsTestConfigurations() throws IOException {
        var build = this.project;
        var cache = this.projectDir.resolve("cg-cache");
        build.seedLauncherMeta(
                cache,
                "1.12.2",
                """
                {
                  "id": "1.12.2",
                  "libraries": []
                }
                """
        );
        build.loader(
                """
                cleanroom.caches.directory = layout.projectDirectory.dir('cg-cache')
                repositories { %s }
                dependencies {
                    testImplementation deobf('net.test:mod:1.0.0')
                }
                tasks.register('checkDeobf') {
                    def compile = configurations.compileClasspath.incoming.dependencies*.name
                    def testCompile = configurations.testCompileClasspath.incoming.dependencies*.name
                    doLast {
                        assert !compile.contains('mod')
                        assert testCompile.contains('mod')
                    }
                }
                """.formatted(
                        fixture()
                )
        );

        var result = build.runner("checkDeobf", "--offline").build();
        assertThat(result.getOutput()).as(result.getOutput()).contains("BUILD SUCCESSFUL");
    }

    @Test
    void remapsTheRootArtifactAndCachesIt() throws IOException {
        var build = this.project;
        var mappings = this.projectDir.resolve("srg2mcp.tsrg");
        Files.writeString(mappings, "tsrg2 srg mcp\n");
        stubRenamerSource();
        build.vanilla(
                """
                repositories { %s }
                sourceSets { tool }
                tasks.register('toolJar', Jar) {
                    from sourceSets.tool.output
                    archiveClassifier = 'tool'
                    manifest { attributes 'Main-Class': 'tool.FakeRenamer' }
                }
                deobf.renamerClasspath.setFrom(tasks.named('toolJar'))
                deobf.mappings.from(file('srg2mcp.tsrg'))
                dependencies {
                    implementation deobf('net.test:mod:1.0.0')
                }
                """.formatted(
                                fixture()
                        ) + RESOLVE_TASK
        );

        var first = build.runner("resolveDeobf", "--offline").build().getOutput();
        assertThat(first).as(first).contains("FILE mod-1.0.0-deobf.jar");
        assertThat(first).as("transitive was dropped:\n" + first).contains("FILE child-1.0.0.jar");
        assertThat(first).as("the renamer jar was not built before the transform ran:\n" + first).contains(":toolJar");

        var arguments = Files.readAllLines(this.projectDir.resolve("srg2mcp.tsrg.args"));
        var libraries = arguments.stream()
                .filter(argument -> argument.endsWith(".jar"))
                .filter(argument -> arguments.get(arguments.indexOf(argument) - 1).equals("--lib"))
                .toList();
        assertThat(libraries.stream().anyMatch(library -> library.endsWith("child-1.0.0.jar")))
                .as("the mod's own graph was not passed to the renamer: " + arguments)
                .isTrue();
        assertThat(arguments).as(arguments.toString()).contains(mappings.toAbsolutePath().toString());

        Files.delete(this.projectDir.resolve("srg2mcp.tsrg.args"));
        var second = build.runner("resolveDeobf", "--offline").build().getOutput();
        assertThat(second).as(second).contains("FILE mod-1.0.0-deobf.jar");
        assertThat(Files.exists(this.projectDir.resolve("srg2mcp.tsrg.args"))).as("the transform re-ran instead of hitting its cache").isFalse();
    }

    @Test
    void ordersInputsForDependenciesAddedLate() throws IOException {
        var build = this.project;
        Files.writeString(this.projectDir.resolve("srg2mcp.tsrg"), "tsrg2 srg mcp\n");
        stubRenamerSource();
        build.vanilla(
                """
                repositories { %s }
                sourceSets { tool }
                tasks.register('toolJar', Jar) {
                    from sourceSets.tool.output
                    archiveClassifier = 'tool'
                    manifest { attributes 'Main-Class': 'tool.FakeRenamer' }
                }
                deobf.renamerClasspath.setFrom(tasks.named('toolJar'))
                deobf.mappings.from(file('srg2mcp.tsrg'))
                afterEvaluate {
                    dependencies {
                        implementation deobf('net.test:mod:1.0.0')
                    }
                }
                """.formatted(
                                fixture()
                        ) + RESOLVE_TASK
        );

        var result = build.runner("resolveDeobf", "--offline").build();
        assertThat(result.getOutput()).as(result.getOutput()).contains(":toolJar");
        assertThat(result.getOutput()).as(result.getOutput()).contains("FILE mod-1.0.0-deobf.jar");
    }

    @Test
    void remapsSrgMembersWithTheDefaultRenamer() throws IOException {
        var build = this.project;
        Files.writeString(
                this.projectDir.resolve("srg2mcp.tsrg"),
                """
                tsrg2 srg mcp
                net/test/mod net/test/mod
                \tfunc_123_a ()V readableName
                """
        );
        build.vanilla(
                """
                repositories { %s }
                deobf.mappings.from(file('srg2mcp.tsrg'))
                dependencies {
                    implementation deobf('net.test:mod:1.0.0')
                }
                """.formatted(
                                fixture()
                        ) + RESOLVE_TASK
        );

        var result = build.runner("resolveDeobf", "--offline").build();
        var transformed = result.getOutput()
                .lines()
                .filter(line -> line.startsWith("PATH ") && line.endsWith("mod-1.0.0-deobf.jar"))
                .map(line -> Path.of(line.substring("PATH ".length())))
                .findFirst()
                .orElseThrow(() -> new AssertionError(result.getOutput()));

        var methods = methodsIn(transformed, "net/test/mod.class");
        assertThat(methods).as(methods.toString()).contains("readableName");
        assertThat(methods).as(methods.toString()).doesNotContain("func_123_a");
    }

    @ParameterizedTest
    @CsvSource({ "false, true", "true, false" })
    void remapsClassifiedJarsWithoutChangingOtherDependencies(boolean moduleMetadata, boolean mapNotation) throws IOException {
        var repository = fixture();
        var module = this.projectDir.resolve("fixture-repo/net/test/mod/1.0.0");
        try (var jar = new JarOutputStream(Files.newOutputStream(module.resolve("mod-1.0.0-srg.jar")))) {
            jar.putNextEntry(new ZipEntry("net/test/classified.class"));
            jar.write(classBytes("net/test/classified"));
            jar.closeEntry();
        }
        if (moduleMetadata) {
            Files.writeString(
                    module.resolve("mod-1.0.0.module"),
                    """
                    {
                      "formatVersion": "1.1",
                      "component": {"group": "net.test", "module": "mod", "version": "1.0.0"},
                      "variants": [
                        {"name": "apiElements", "attributes": {"org.gradle.usage": "java-api"},
                         "dependencies": [{"group": "net.test", "module": "child", "version": {"requires": "1.0.0"}}],
                         "files": [{"name": "mod-1.0.0.jar", "url": "mod-1.0.0.jar"}]},
                        {"name": "runtimeElements", "attributes": {"org.gradle.usage": "java-runtime"},
                         "dependencies": [{"group": "net.test", "module": "child", "version": {"requires": "1.0.0"}}],
                         "files": [{"name": "mod-1.0.0.jar", "url": "mod-1.0.0.jar"}]}
                      ]
                    }
                    """
            );
            repository = repository.replace(" }", "; metadataSources { gradleMetadata(); mavenPom() } }");
        }
        Files.writeString(
                this.projectDir.resolve("srg2mcp.tsrg"),
                """
                tsrg2 srg mcp
                net/test/classified net/test/classified
                \tfunc_123_a ()V readableName
                """
        );
        var notation = mapNotation ? "[group: 'net.test', name: 'mod', version: '1.0.0', classifier: 'srg']" : "'net.test:mod:1.0.0:srg'";
        this.project.vanilla(
                """
                repositories { %s }
                deobf.mappings.from(file('srg2mcp.tsrg'))
                dependencies {
                    implementation 'net.test:mod:1.0.0'
                    implementation deobf(%s)
                    testImplementation deobf(%s)
                }
                tasks.register('resolveClassified') {
                    def compile = configurations.compileClasspath.incoming.files
                    def runtime = configurations.runtimeClasspath.incoming.files
                    inputs.files(compile, runtime)
                    doLast {
                        assert compile.files*.name.containsAll(['mod-1.0.0.jar', 'mod-1.0.0-srg-deobf.jar', 'child-1.0.0.jar'])
                        assert runtime.files*.name.containsAll(['mod-1.0.0.jar', 'mod-1.0.0-srg-deobf.jar', 'child-1.0.0.jar'])
                        compile.each { println 'PATH ' + it.absolutePath }
                    }
                }
                """.formatted(
                        repository,
                        notation,
                        notation
                )
        );

        var output = this.project.runner("resolveClassified", "--offline").build().getOutput();
        var transformed = output.lines()
                .filter(line -> line.startsWith("PATH ") && line.endsWith("mod-1.0.0-srg-deobf.jar"))
                .map(line -> Path.of(line.substring("PATH ".length())))
                .findFirst()
                .orElseThrow(() -> new AssertionError(output));
        assertThat(methodsIn(transformed, "net/test/classified.class")).contains("readableName").doesNotContain("func_123_a");
        PluginBuild.reused(this.project.runner("resolveClassified", "--offline").build().getOutput());
    }

    @Test
    void preparedInputsResolveInIdeaModel() throws IOException {
        var build = new PluginBuild(this.projectDir).settings();
        stubRenamerSource();
        build.vanilla(
                """
                apply plugin: 'idea'
                repositories { %s }
                sourceSets { tool }
                def writeDeobfMappings = tasks.register('writeDeobfMappings') {
                    def output = layout.buildDirectory.file('deobf/srg2mcp.tsrg')
                    outputs.file(output)
                    doLast {
                        output.get().asFile.parentFile.mkdirs()
                        output.get().asFile.text = 'tsrg2 srg mcp\\n'
                    }
                }
                tasks.register('toolJar', Jar) {
                    from sourceSets.tool.output
                    archiveClassifier = 'tool'
                    manifest { attributes 'Main-Class': 'tool.FakeRenamer' }
                }
                deobf.renamerClasspath.setFrom(tasks.named('toolJar'))
                deobf.mappings.from(writeDeobfMappings)
                dependencies {
                    implementation deobf('net.test:mod:1.0.0')
                }
                """.formatted(
                        fixture()
                )
        );

        var prepared = build.runner("prepareDeobf", "--offline").build().getOutput();
        assertThat(Files.isRegularFile(this.projectDir.resolve("build/deobf/srg2mcp.tsrg"))).as(prepared).isTrue();
        assertThat(Files.isRegularFile(this.projectDir.resolve("build/libs/test-project-tool.jar"))).as(prepared).isTrue();

        var model = build.ideaModel("--offline");
        var hasDeobf = model.value()
                .getModules()
                .stream()
                .flatMap(module -> module.getDependencies().stream())
                .filter(IdeaSingleEntryLibraryDependency.class::isInstance)
                .map(IdeaSingleEntryLibraryDependency.class::cast)
                .anyMatch(dependency -> dependency.getFile().getName().equals("mod-1.0.0-deobf.jar"));
        assertThat(hasDeobf).as(model.output()).isTrue();
    }

    @Test
    void packagedUserdevInputsResolveInIdeaModelWithoutPreparation() throws IOException {
        var build = new PluginBuild(this.projectDir).settings();
        var userdev = packagedUserdev();
        build.vanilla(
                """
                apply plugin: 'idea'
                repositories { %s }
                def userdevDeobf = configurations.create('userdevDeobf')
                dependencies.add(userdevDeobf.name, files('%s'))
                dependencies {
                    implementation deobf('net.test:mod:1.0.0')
                }
                deobf.useUserdev(userdevDeobf)
                """.formatted(
                        fixture(),
                        userdev.toString().replace('\\', '/')
                )
        );

        var model = build.ideaModel("--offline");
        var transformed = model.value()
                .getModules()
                .stream()
                .flatMap(module -> module.getDependencies().stream())
                .filter(IdeaSingleEntryLibraryDependency.class::isInstance)
                .map(IdeaSingleEntryLibraryDependency.class::cast)
                .map(IdeaSingleEntryLibraryDependency::getFile)
                .filter(file -> file.getName().equals("mod-1.0.0-deobf.jar"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(model.output()));

        var methods = methodsIn(transformed.toPath(), "net/test/mod.class");
        assertThat(methods).as(methods.toString()).contains("readableName");
        assertThat(methods).as(methods.toString()).doesNotContain("func_123_a");
    }

    private void stubRenamerSource() throws IOException {
        var sourceDir = this.projectDir.resolve("src/tool/java/tool");
        Files.createDirectories(sourceDir);
        Files.writeString(
                sourceDir.resolve("FakeRenamer.java"),
                """
                package tool;

                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.nio.file.StandardCopyOption;
                import java.util.Arrays;

                public final class FakeRenamer {
                    public static void main(String[] args) throws Exception {
                        String input = null, output = null, map = null;
                        for (int i = 0; i < args.length - 1; i++) {
                            switch (args[i]) {
                                case "--input" -> input = args[i + 1];
                                case "--output" -> output = args[i + 1];
                                case "--map" -> map = args[i + 1];
                                default -> { }
                            }
                        }
                        Files.write(Path.of(map + ".args"), Arrays.asList(args));
                        Files.copy(Path.of(input), Path.of(output), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                """
        );
    }

    /**
     * A local repository holding {@code net.test:mod:1.0.0} and the transitive {@code net.test:child:1.0.0}.
     * Both jars contain one minimal class; the stub renamer copies rather than reads them.
     */
    private String fixture() throws IOException {
        var repository = this.projectDir.resolve("fixture-repo");
        module(
                repository,
                "mod",
                """
                    <dependencies>
                        <dependency>
                            <groupId>net.test</groupId>
                            <artifactId>child</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                """
        );
        module(repository, "child", "");
        return "maven { url = '" + repository.toUri() + "' }";
    }

    private Path packagedUserdev() throws IOException {
        var library = this.projectDir.resolve("cleanroom-srg.jar");
        try (var jar = new JarOutputStream(Files.newOutputStream(library))) {
            jar.putNextEntry(new ZipEntry("net/test/library.class"));
            jar.write(classBytes("net/test/library"));
            jar.closeEntry();
        }

        var userdev = this.projectDir.resolve("cleanroom-userdev.jar");
        try (var jar = new JarOutputStream(Files.newOutputStream(userdev))) {
            // The deobf inputs are found through the layout this document declares, not by convention
            jar.putNextEntry(new ZipEntry(UserdevConfig.meta(UserdevConfig.FILE_NAME)));
            jar.write(PluginBuild.userdevConfigJson("0.7.0").getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            jar.putNextEntry(new ZipEntry(UserdevConfig.meta(UserdevConfig.SRG2MCP)));
            jar.write(
                    """
                    tsrg2 srg mcp
                    net/test/mod net/test/mod
                    \tfunc_123_a ()V readableName
                    """.getBytes(
                            StandardCharsets.UTF_8
                    )
            );
            jar.closeEntry();
            jar.putNextEntry(new ZipEntry(UserdevConfig.meta(UserdevConfig.DEOBF_LIBRARY)));
            jar.write(Files.readAllBytes(library));
            jar.closeEntry();
        }
        return userdev;
    }

    private void module(Path repository, String name, String dependencies) throws IOException {
        var moduleDir = repository.resolve("net/test/" + name + "/1.0.0");
        Files.createDirectories(moduleDir);
        try (var jar = new JarOutputStream(Files.newOutputStream(moduleDir.resolve(name + "-1.0.0.jar")))) {
            jar.putNextEntry(new ZipEntry("net/test/" + name + ".class"));
            jar.write(classBytes("net/test/" + name));
            jar.closeEntry();
        }
        Files.writeString(
                moduleDir.resolve(name + "-1.0.0.pom"),
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>net.test</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0.0</version>
                %s</project>
                """.formatted(
                        name,
                        dependencies
                )
        );
    }

    private static byte[] classBytes(String name) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_123_a", "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static Set<String> methodsIn(Path jarPath, String entryName) throws IOException {
        var methods = new HashSet<String>();
        try (var jar = new JarFile(jarPath.toFile())) {
            try (var input = jar.getInputStream(jar.getJarEntry(entryName))) {
                new ClassReader(input).accept(
                        new ClassVisitor(Opcodes.ASM9) {

                            @Override
                            public org.objectweb.asm.MethodVisitor visitMethod(
                                    int access,
                                    String name,
                                    String descriptor,
                                    String signature,
                                    String[] exceptions
                            ) {
                                methods.add(name);
                                return null;
                            }

                        },
                        ClassReader.SKIP_CODE
                );
            }
        }
        return Set.copyOf(methods);
    }

}
