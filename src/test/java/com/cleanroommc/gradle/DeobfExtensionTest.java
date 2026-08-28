package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.schema.UserdevConfig;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeobfExtensionTest {

    private static final String RESOLVE_TASK = """
            tasks.register('resolveDeobf') {
                def classpath = configurations.compileClasspath.incoming.files
                inputs.files(classpath)
                doLast { classpath.each { println 'FILE ' + it.name; println 'PATH ' + it.absolutePath } }
            }
            """;

    @TempDir
    Path projectDir;

    @Test
    void configuresWithoutResolving() throws IOException {
        var build = new PluginBuild(this.projectDir).settings();
        build.vanilla("""
                repositories { %s }
                dependencies {
                    implementation deobf('net.test:mod:1.0.0')
                }
                """.formatted(fixture()));

        assertTrue(build.runner("help", "--offline").build().getOutput().contains("BUILD SUCCESSFUL"));
    }

    @Test
    void closureFormParses() throws IOException {
        var build = new PluginBuild(this.projectDir).settings();
        build.vanilla("""
                repositories { %s }
                dependencies {
                    implementation deobf('net.test:mod:1.0.0') { sources = false }
                }
                """.formatted(fixture()));

        assertTrue(build.runner("help", "--offline").build().getOutput().contains("BUILD SUCCESSFUL"));
    }

    @Test
    void sourcesAreNotImplementedYet() throws IOException {
        var build = new PluginBuild(this.projectDir).settings();
        build.vanilla("""
                repositories { %s }
                dependencies {
                    implementation deobf('net.test:mod:1.0.0') { sources = true }
                }
                """.formatted(fixture()));

        var result = build.runner("help", "--offline").buildAndFail();
        assertTrue(result.getOutput().contains("sources = true } is not implemented yet"), result.getOutput());
    }

    @Test
    void rejectsNonModuleNotation() throws IOException {
        var build = new PluginBuild(this.projectDir).settings();
        build.vanilla("""
                dependencies {
                    implementation deobf(files('lib.jar'))
                }
                """);

        var result = build.runner("help", "--offline").buildAndFail();
        assertTrue(result.getOutput().contains("only accepts external module notations"), result.getOutput());
    }

    @Test
    void vanillaModeFailsWithoutMappings() throws IOException {
        var build = new PluginBuild(this.projectDir).settings();
        build.vanilla("""
                repositories { %s }
                dependencies {
                    implementation deobf('net.test:mod:1.0.0')
                }
                """.formatted(fixture()) + RESOLVE_TASK);

        var result = build.runner("resolveDeobf", "--offline").buildAndFail();
        assertTrue(result.getOutput().contains("deobf() needs MCP mappings"), result.getOutput());
    }

    @Test
    void loaderModeRejectsCompileClasspath() throws IOException {
        var build = new PluginBuild(this.projectDir).settings();
        build.loader("""
                repositories { %s }
                dependencies {
                    implementation deobf('net.test:mod:1.0.0')
                }
                """.formatted(fixture()) + RESOLVE_TASK);

        var result = build.runner("resolveDeobf", "--offline").buildAndFail();
        assertTrue(result.getOutput().contains("cannot be declared on the 'compileClasspath' hierarchy in loader mode"),
                result.getOutput());
        build.assertProblem("deobf-on-compile-classpath");
    }

    @Test
    void loaderModeRejectsDependenciesAddedLate() throws IOException {
        var build = new PluginBuild(this.projectDir).settings();
        build.loader("""
                repositories { %s }
                afterEvaluate {
                    dependencies {
                        implementation deobf('net.test:mod:1.0.0')
                    }
                }
                """.formatted(fixture()) + RESOLVE_TASK);

        var result = build.runner("resolveDeobf", "--offline").buildAndFail();
        assertTrue(result.getOutput().contains("cannot be declared on the 'compileClasspath' hierarchy in loader mode"),
                result.getOutput());
    }

    @Test
    void loaderModeAllowsTestConfigurations() throws IOException {
        var build = new PluginBuild(this.projectDir).settings();
        build.loader("""
                repositories { %s }
                dependencies {
                    testImplementation deobf('net.test:mod:1.0.0')
                }
                """.formatted(fixture()) + RESOLVE_TASK);

        assertTrue(build.runner("resolveDeobf", "--offline").build().getOutput().contains("BUILD SUCCESSFUL"));
    }

    @Test
    void remapsTheRootArtifactAndCachesIt() throws IOException {
        var build = new PluginBuild(this.projectDir).settings();
        var mappings = this.projectDir.resolve("srg2mcp.tsrg");
        Files.writeString(mappings, "tsrg2 srg mcp\n");
        stubRenamerSource();
        build.vanilla("""
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
                """.formatted(fixture()) + RESOLVE_TASK);

        var first = build.runner("resolveDeobf", "--offline").build().getOutput();
        assertTrue(first.contains("FILE mod-1.0.0-deobf.jar"), first);
        assertTrue(first.contains("FILE child-1.0.0.jar"), "transitive was dropped:\n" + first);
        assertTrue(first.contains(":toolJar"), "the renamer jar was not built before the transform ran:\n" + first);

        var arguments = Files.readAllLines(this.projectDir.resolve("srg2mcp.tsrg.args"));
        var libraries = arguments.stream()
                .filter(argument -> argument.endsWith(".jar"))
                .filter(argument -> arguments.get(arguments.indexOf(argument) - 1).equals("--lib"))
                .toList();
        assertTrue(libraries.stream().anyMatch(library -> library.endsWith("child-1.0.0.jar")),
                "the mod's own graph was not passed to the renamer: " + arguments);
        assertTrue(arguments.contains(mappings.toAbsolutePath().toString()), arguments.toString());

        Files.delete(this.projectDir.resolve("srg2mcp.tsrg.args"));
        var second = build.runner("resolveDeobf", "--offline").build().getOutput();
        assertTrue(second.contains("FILE mod-1.0.0-deobf.jar"), second);
        assertFalse(Files.exists(this.projectDir.resolve("srg2mcp.tsrg.args")),
                "the transform re-ran instead of hitting its cache");
    }

    @Test
    void ordersInputsForDependenciesAddedLate() throws IOException {
        var build = new PluginBuild(this.projectDir).settings();
        Files.writeString(this.projectDir.resolve("srg2mcp.tsrg"), "tsrg2 srg mcp\n");
        stubRenamerSource();
        build.vanilla("""
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
                """.formatted(fixture()) + RESOLVE_TASK);

        var result = build.runner("resolveDeobf", "--offline").build();
        assertTrue(result.getOutput().contains(":toolJar"), result.getOutput());
        assertTrue(result.getOutput().contains("FILE mod-1.0.0-deobf.jar"), result.getOutput());
    }

    @Test
    void remapsSrgMembersWithTheDefaultRenamer() throws IOException {
        var build = new PluginBuild(this.projectDir).settings();
        Files.writeString(this.projectDir.resolve("srg2mcp.tsrg"), """
                tsrg2 srg mcp
                net/test/mod net/test/mod
                \tfunc_123_a ()V readableName
                """);
        build.vanilla("""
                repositories { %s }
                deobf.mappings.from(file('srg2mcp.tsrg'))
                dependencies {
                    implementation deobf('net.test:mod:1.0.0')
                }
                """.formatted(fixture()) + RESOLVE_TASK);

        var result = build.runner("resolveDeobf", "--offline").build();
        var transformed = result.getOutput().lines()
                .filter(line -> line.startsWith("PATH ") && line.endsWith("mod-1.0.0-deobf.jar"))
                .map(line -> Path.of(line.substring("PATH ".length())))
                .findFirst()
                .orElseThrow(() -> new AssertionError(result.getOutput()));

        var methods = methodsIn(transformed, "net/test/mod.class");
        assertTrue(methods.contains("readableName"), methods.toString());
        assertFalse(methods.contains("func_123_a"), methods.toString());
    }

    @Test
    void preparedInputsResolveInIdeaModel() throws IOException {
        var build = new PluginBuild(this.projectDir).settings();
        stubRenamerSource();
        build.vanilla("""
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
                """.formatted(fixture()));

        var prepared = build.runner("prepareDeobf", "--offline").build().getOutput();
        assertTrue(Files.isRegularFile(this.projectDir.resolve("build/deobf/srg2mcp.tsrg")), prepared);
        assertTrue(Files.isRegularFile(this.projectDir.resolve("build/libs/test-project-tool.jar")), prepared);

        var output = new ByteArrayOutputStream();
        try (var connection = GradleConnector.newConnector()
                .useInstallation(new File(System.getProperty("test.gradle.home")))
                .useGradleUserHomeDir(new File(System.getProperty("testkit.gradle.user.home")))
                .forProjectDirectory(this.projectDir.toFile())
                .connect()) {
            var model = connection.model(IdeaProject.class)
                    .withArguments("--offline", "--configuration-cache", "--configuration-cache-problems=fail")
                    .setStandardOutput(output)
                    .setStandardError(output)
                    .get();
            var hasDeobf = model.getModules().stream()
                    .flatMap(module -> module.getDependencies().stream())
                    .filter(IdeaSingleEntryLibraryDependency.class::isInstance)
                    .map(IdeaSingleEntryLibraryDependency.class::cast)
                    .anyMatch(dependency -> dependency.getFile().getName().equals("mod-1.0.0-deobf.jar"));
            assertTrue(hasDeobf, output.toString());
        }
    }

    @Test
    void packagedUserdevInputsResolveInIdeaModelWithoutPreparation() throws IOException {
        var build = new PluginBuild(this.projectDir).settings();
        var userdev = packagedUserdev();
        build.vanilla("""
                apply plugin: 'idea'
                repositories { %s }
                def userdevDeobf = configurations.create('userdevDeobf')
                dependencies.add(userdevDeobf.name, files('%s'))
                dependencies {
                    implementation deobf('net.test:mod:1.0.0')
                }
                deobf.useUserdev(userdevDeobf)
                """.formatted(fixture(), userdev.toString().replace('\\', '/')));

        var output = new ByteArrayOutputStream();
        try (var connection = GradleConnector.newConnector()
                .useInstallation(new File(System.getProperty("test.gradle.home")))
                .useGradleUserHomeDir(new File(System.getProperty("testkit.gradle.user.home")))
                .forProjectDirectory(this.projectDir.toFile())
                .connect()) {
            var model = connection.model(IdeaProject.class)
                    .withArguments("--offline", "--configuration-cache", "--configuration-cache-problems=fail")
                    .setStandardOutput(output)
                    .setStandardError(output)
                    .get();
            var transformed = model.getModules().stream()
                    .flatMap(module -> module.getDependencies().stream())
                    .filter(IdeaSingleEntryLibraryDependency.class::isInstance)
                    .map(IdeaSingleEntryLibraryDependency.class::cast)
                    .map(IdeaSingleEntryLibraryDependency::getFile)
                    .filter(file -> file.getName().equals("mod-1.0.0-deobf.jar"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(output.toString()));

            var methods = methodsIn(transformed.toPath(), "net/test/mod.class");
            assertTrue(methods.contains("readableName"), methods.toString());
            assertFalse(methods.contains("func_123_a"), methods.toString());
        }
    }

    private void stubRenamerSource() throws IOException {
        var sourceDir = this.projectDir.resolve("src/tool/java/tool");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("FakeRenamer.java"), """
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
                """);
    }

    /**
     * A local repository holding {@code net.test:mod:1.0.0} and the transitive {@code net.test:child:1.0.0}.
     * Both jars contain one minimal class; the stub renamer copies rather than reads them.
     */
    private String fixture() throws IOException {
        var repository = this.projectDir.resolve("fixture-repo");
        module(repository, "mod", """
                    <dependencies>
                        <dependency>
                            <groupId>net.test</groupId>
                            <artifactId>child</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                """);
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
            jar.putNextEntry(new ZipEntry(UserdevConfig.meta(UserdevConfig.SRG2MCP)));
            jar.write("""
                    tsrg2 srg mcp
                    net/test/mod net/test/mod
                    \tfunc_123_a ()V readableName
                    """.getBytes(StandardCharsets.UTF_8));
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
        Files.writeString(moduleDir.resolve(name + "-1.0.0.pom"), """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>net.test</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0.0</version>
                %s</project>
                """.formatted(name, dependencies));
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
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor,
                                                                       String signature, String[] exceptions) {
                        methods.add(name);
                        return null;
                    }
                }, ClassReader.SKIP_CODE);
            }
        }
        return Set.copyOf(methods);
    }

}
