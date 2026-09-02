package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.Platform;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A published Cleanroom userdev module a workspace can actually be built from: one raw artifact, the raw
 * variants the plugin's transforms consume, the Minecraft jars they expect in the shared cache, and stub
 * decompiler, merge tool, access transformer and renamer resolvable by the coordinates the artifact records.
 *
 * <p>The module deliberately publishes nothing pre-materialized. Materializing is an artifact transform, so
 * a fixture that shipped the finished variants would resolve without ever running one.
 */
final class UserdevFixture {

    static final String MINECRAFT_VERSION = "1.12.2";
    static final String CACHE_DIRECTORY = "shared-cache";
    static final String RENAMER_JAR = "local-maven/net/minecraftforge/renamer/1.0/renamer-1.0.jar";

    /** The buildscript lines a workspace needs to reach this fixture instead of the real toolchain. */
    static final String PREAMBLE = """
            cleanroom.caches.directory = file('%s')
            deobf.renamerClasspath.setFrom(files('%s'))
            """.formatted(CACHE_DIRECTORY, RENAMER_JAR);

    /** What the seeded artifact declares, so a test can break exactly one thing about it. */
    static final class Spec {
        String clientSha1;
        String omit = "";
    }

    static void seed(Path projectDir, String version) throws IOException {
        seed(projectDir, version, new Spec());
    }

    static void seed(Path projectDir, String version, Spec spec) throws IOException {
        stubTools(projectDir);
        seedModule(projectDir, "library");
        seedModule(projectDir, "native-current");
        seedModule(projectDir, "native-foreign");
        var cache = Files.createDirectories(projectDir.resolve(CACHE_DIRECTORY + "/versions/" + MINECRAFT_VERSION));
        var client = minecraftJar(cache.resolve("client.jar"), "assets/pack.mcmeta");
        var server = minecraftJar(cache.resolve("server.jar"), "assets/server.txt");

        var module = Files.createDirectories(
                projectDir.resolve("local-maven/com/cleanroommc/cleanroom-userdev/" + version));
        writeArtifact(module.resolve("cleanroom-userdev-" + version + ".jar"), version, spec,
                spec.clientSha1 == null ? IO.sha1(client) : spec.clientSha1, IO.sha1(server));
        Files.write(module.resolve("cleanroom-userdev-" + version + "-sources.jar"), archive(Map.of(
                "net/minecraft/Block.java", "class Block {\n    // patched by the artifact\n}\n",
                "com/cleanroommc/Loader.java", "package com.cleanroommc;\n")));
        Files.writeString(module.resolve("cleanroom-userdev-" + version + ".pom"),
                pom("com.cleanroommc", "cleanroom-userdev", version));

        var platform = Platform.CURRENT.canonicalNativePlatform();
        var foreign = Platform.nativePlatforms().stream()
                .filter(candidate -> candidate != platform)
                .findFirst().orElseThrow();
        Files.writeString(module.resolve("cleanroom-userdev-" + version + ".module"), """
                {
                  "formatVersion": "1.1",
                  "component": {"group":"com.cleanroommc","module":"cleanroom-userdev","version":"%1$s","attributes":{"org.gradle.status":"release"}},
                  "createdBy": {"gradle": {"version":"9.5.1"}},
                  "variants": [
                    {"name":"apiElements","attributes":{"org.gradle.category":"library","org.gradle.libraryelements":"jar","org.gradle.usage":"java-api","com.cleanroommc.userdev.role":"classes"},"dependencies":[{"group":"com.cleanroommc","module":"fixture-library","version":{"requires":"1"}}],"files":[{"name":"cleanroom-userdev-%1$s.jar","url":"cleanroom-userdev-%1$s.jar"}]},
                    {"name":"runtimeElements","attributes":{"org.gradle.category":"library","org.gradle.libraryelements":"jar","org.gradle.usage":"java-runtime","com.cleanroommc.userdev.role":"classes"},"dependencies":[{"group":"com.cleanroommc","module":"fixture-library","version":{"requires":"1"}}],"files":[{"name":"cleanroom-userdev-%1$s.jar","url":"cleanroom-userdev-%1$s.jar"}]},
                    {"name":"sourcesElements","attributes":{"org.gradle.category":"documentation","org.gradle.docstype":"sources","org.gradle.usage":"java-runtime","com.cleanroommc.userdev.role":"sources"},"dependencies":[{"group":"com.cleanroommc","module":"fixture-library","version":{"requires":"1"}}],"files":[{"name":"cleanroom-userdev-%1$s.jar","url":"cleanroom-userdev-%1$s.jar"}]},
                    {"name":"clientExtraElements","attributes":{"org.gradle.category":"library","org.gradle.libraryelements":"jar","org.gradle.usage":"java-runtime","com.cleanroommc.userdev.role":"client-extra"},"files":[{"name":"cleanroom-userdev-%1$s.jar","url":"cleanroom-userdev-%1$s.jar"}]},
                    {"name":"serverExtraElements","attributes":{"org.gradle.category":"library","org.gradle.libraryelements":"jar","org.gradle.usage":"java-runtime","com.cleanroommc.userdev.role":"server-extra"},"files":[{"name":"cleanroom-userdev-%1$s.jar","url":"cleanroom-userdev-%1$s.jar"}]},
                    {"name":"%2$sElements","attributes":{"org.gradle.category":"library","org.gradle.libraryelements":"jar","org.gradle.usage":"java-runtime","org.gradle.native.operatingSystem":"%3$s","org.gradle.native.architecture":"%4$s","com.cleanroommc.userdev.role":"natives"},"dependencies":[{"group":"com.cleanroommc","module":"fixture-native-current","version":{"requires":"1"}}]},
                    {"name":"%5$sElements","attributes":{"org.gradle.category":"library","org.gradle.libraryelements":"jar","org.gradle.usage":"java-runtime","org.gradle.native.operatingSystem":"%6$s","org.gradle.native.architecture":"%7$s","com.cleanroommc.userdev.role":"natives"},"dependencies":[{"group":"com.cleanroommc","module":"fixture-native-foreign","version":{"requires":"1"}}]}
                  ]
                }
                """.formatted(version, platform.lwjglNativesClassifier(),
                        platform.operatingSystemFamily(), platform.machineArchitecture(),
                        foreign.lwjglNativesClassifier(), foreign.operatingSystemFamily(),
                        foreign.machineArchitecture()));
    }

    static void writeArtifact(Path jar, String version, Spec spec, String clientSha1, String serverSha1)
            throws IOException {
        var entries = new LinkedHashMap<String, byte[]>();
        entries.put("com/cleanroommc/Loader.class", classBytes("com/cleanroommc/Loader"));
        entries.put(UserdevConfig.meta(UserdevConfig.FILE_NAME),
                config(version, clientSha1, serverSha1).getBytes(StandardCharsets.UTF_8));
        entries.put(UserdevConfig.meta(UserdevConfig.BINPATCHES), binaryArchive(Map.of(
                UserdevConfig.CLIENT_BINPATCHES + "net/minecraft/Patched.class.add",
                classBytes("net/minecraft/Patched"),
                UserdevConfig.SERVER_BINPATCHES + "net/minecraft/Patched.class.add",
                classBytes("net/minecraft/Patched"))));
        entries.put(UserdevConfig.meta(UserdevConfig.OBF2SRG), tsrg());
        entries.put(UserdevConfig.meta(UserdevConfig.SRG2MCP), tsrg());
        entries.put(UserdevConfig.meta(UserdevConfig.MCP2SRG), tsrg());
        entries.put(UserdevConfig.meta(UserdevConfig.ACCESS), new byte[0]);
        entries.put(UserdevConfig.meta(UserdevConfig.CONSTRUCTORS), new byte[0]);
        entries.put(UserdevConfig.meta(UserdevConfig.EXCEPTIONS), new byte[0]);
        entries.put(UserdevConfig.meta(UserdevConfig.METHODS), csv("searge,name,side,desc"));
        entries.put(UserdevConfig.meta(UserdevConfig.FIELDS), csv("searge,name,side,desc"));
        entries.put(UserdevConfig.meta(UserdevConfig.PARAMS), csv("param,name,side"));
        entries.put(UserdevConfig.meta(UserdevConfig.DEOBF_LIBRARY), archive("README", "empty"));
        entries.put(UserdevConfig.meta(UserdevConfig.SOURCE_INPUT), classArchive());
        entries.put(UserdevConfig.meta("client-extra") + "/assets/pack.mcmeta", "{}".getBytes(StandardCharsets.UTF_8));
        entries.put(UserdevConfig.meta("server-extra") + "/assets/server.txt", "server".getBytes(StandardCharsets.UTF_8));
        entries.put(UserdevConfig.meta(UserdevConfig.PATCHES) + "/net/minecraft/Block.java.patch", """
                --- a/net/minecraft/Block.java
                +++ b/net/minecraft/Block.java
                @@ -1,2 +1,3 @@
                 class Block {
                +    // patched by the artifact
                 }
                """.getBytes(StandardCharsets.UTF_8));
        entries.put(UserdevConfig.meta(UserdevConfig.LOADER_SOURCES) + "/com/cleanroommc/Loader.java",
                "package com.cleanroommc;\n".getBytes(StandardCharsets.UTF_8));

        entries.remove(spec.omit);
        Files.createDirectories(jar.getParent());
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
    }

    /** The spec 1 document the artifact carries, which every consumer reads its layout from. */
    static String config(String version, String clientSha1, String serverSha1) {
        return """
                {
                  "spec": 1,
                  "minecraft": {
                    "version": "%4$s",
                    "client": {"url": "https://example.invalid/client.jar", "sha1": "%1$s"},
                    "server": {"url": "https://example.invalid/server.jar", "sha1": "%2$s"}
                  },
                  "loader": {"version": "%3$s", "forgeVersion": "14.23.5.2860", "group": "com.cleanroommc"},
                  "inputs": {
                    "mcpConfig": "de.oceanlabs.mcp:mcp_config:1.12.2-20201025.185735",
                    "mappings": "de.oceanlabs.mcp:mcp_stable:39-1.12@zip",
                    "initialPatches": "com.cleanroommc:initial-patches:1.0",
                    "tools": {
                      "accesstransformer": "net.minecraftforge:accesstransformers:1.0",
                      "mergetool": "net.minecraftforge:mergetool:1.0",
                      "decompiler": "net.minecraftforge:decompiler:1.0"
                    }
                  },
                  "layout": {
                    "binpatches": "userdev/binpatches.zip",
                    "clientBinpatches": "binpatch/client/",
                    "serverBinpatches": "binpatch/server/",
                    "obfToSrg": "userdev/obf2srg.tsrg",
                    "srgToMcp": "userdev/srg2mcp.tsrg",
                    "mcpToSrg": "userdev/mcp2srg.tsrg",
                    "access": "userdev/access.txt",
                    "constructors": "userdev/constructors.txt",
                    "exceptions": "userdev/exceptions.txt",
                    "methods": "userdev/methods.csv",
                    "fields": "userdev/fields.csv",
                    "params": "userdev/params.csv",
                    "deobfLibrary": "userdev/deobf-library.jar",
                    "sourceInput": "userdev/source-input.jar",
                    "clientExtra": "userdev/client-extra",
                    "serverExtra": "userdev/server-extra",
                    "initialPatches": "userdev/initial-patches",
                    "accessTransformers": [],
                    "sideAnnotationStrippers": "userdev/cleanroom.sas",
                    "patches": "userdev/patches",
                    "loaderSources": "userdev/loader-sources"
                  },
                  "runs": {
                    "client": {"mainClass": "GradleStart", "launchClass": "net.minecraft.launchwrapper.Launch", "tweakClass": "net.minecraftforge.fml.common.launcher.FMLTweaker", "target": "fmluserdevclient"},
                    "server": {"mainClass": "GradleStartServer", "launchClass": "net.minecraft.launchwrapper.Launch", "tweakClass": "net.minecraftforge.fml.common.launcher.FMLServerTweaker", "target": "fmluserdevserver"}
                  }
                }
                """.formatted(clientSha1, serverSha1, version, MINECRAFT_VERSION);
    }

    static String pom(String group, String artifact, String version) {
        // Without the redirect marker Gradle never looks at the module file, and every attributed variant
        // is replaced by the POM's single default artifact
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- do_not_remove: published-with-gradle-metadata -->
                <project><modelVersion>4.0.0</modelVersion><groupId>%s</groupId>
                <artifactId>%s</artifactId><version>%s</version></project>
                """.formatted(group, artifact, version);
    }

    // Stub tools, resolved by the coordinates the artifact records

    private static void stubTools(Path projectDir) throws IOException {
        toolJar(projectDir, "mergetool", "net.minecraftforge.mergetool.ConsoleMerger", """
                package net.minecraftforge.mergetool;

                import java.nio.file.*;

                public final class ConsoleMerger {
                    public static void main(String[] args) throws Exception {
                        Path client = null, output = null;
                        for (int i = 0; i < args.length - 1; i++) {
                            if ("--client".equals(args[i])) client = Path.of(args[i + 1]);
                            if ("--output".equals(args[i])) output = Path.of(args[i + 1]);
                        }
                        Files.copy(client, output, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                """);
        toolJar(projectDir, "accesstransformers", "net.minecraftforge.accesstransformer.TransformerProcessor", """
                package net.minecraftforge.accesstransformer;

                import java.nio.file.*;
                import java.util.*;
                import java.util.zip.*;

                public final class TransformerProcessor {
                    public static void main(String[] args) throws Exception {
                        Path in = null, out = null;
                        List<String> ats = new ArrayList<>();
                        for (int i = 0; i < args.length - 1; i++) {
                            if ("--inJar".equals(args[i])) in = Path.of(args[i + 1]);
                            if ("--outJar".equals(args[i])) out = Path.of(args[i + 1]);
                            if ("--atFile".equals(args[i])) ats.add(Files.readString(Path.of(args[i + 1])).trim());
                        }
                        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(in));
                             ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(out))) {
                            for (ZipEntry e = zin.getNextEntry(); e != null; e = zin.getNextEntry()) {
                                zout.putNextEntry(new ZipEntry(e.getName()));
                                zin.transferTo(zout);
                            }
                            zout.putNextEntry(new ZipEntry("access-transformed.txt"));
                            zout.write(String.join("\\n", ats).getBytes("UTF-8"));
                        }
                    }
                }
                """);
        toolJar(projectDir, "decompiler", "org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler", """
                package org.jetbrains.java.decompiler.main.decompiler;

                import java.nio.file.*;
                import java.util.zip.*;

                public final class ConsoleDecompiler {
                    public static void main(String[] args) throws Exception {
                        Path input = Path.of(args[args.length - 2]);
                        Path output = Path.of(args[args.length - 1]);
                        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(input));
                             ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(output))) {
                            for (ZipEntry e = zin.getNextEntry(); e != null; e = zin.getNextEntry()) {
                                String name = e.getName();
                                if (!name.endsWith(".class")) continue;
                                name = name.substring(0, name.length() - ".class".length());
                                zout.putNextEntry(new ZipEntry(name + ".java"));
                                zout.write(("class " + name.substring(name.lastIndexOf('/') + 1)
                                        + " {\\n}\\n").getBytes("UTF-8"));
                            }
                            zout.putNextEntry(new ZipEntry("decompiler-classpath.txt"));
                            zout.write(java.util.Arrays.stream(args).filter(arg -> arg.startsWith("-e="))
                                    .collect(java.util.stream.Collectors.joining("\\n")).getBytes("UTF-8"));
                        }
                    }
                }
                """);
        toolJar(projectDir, "renamer", "tool.FakeRenamer", """
                package tool;

                import java.nio.file.*;

                public final class FakeRenamer {
                    public static void main(String[] args) throws Exception {
                        Path input = null, output = null;
                        for (int i = 0; i < args.length - 1; i++) {
                            if ("--input".equals(args[i])) input = Path.of(args[i + 1]);
                            if ("--output".equals(args[i])) output = Path.of(args[i + 1]);
                        }
                        Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                """);
    }

    private static void toolJar(Path projectDir, String artifact, String mainClass, String source) throws IOException {
        var module = projectDir.resolve("local-maven/net/minecraftforge/" + artifact + "/1.0");
        if (Files.isDirectory(module)) {
            return;
        }
        var work = Files.createDirectories(projectDir.resolve("tools/" + artifact));
        var sourceFile = work.resolve(mainClass.substring(mainClass.lastIndexOf('.') + 1) + ".java");
        Files.writeString(sourceFile, source);
        var classes = Files.createDirectories(work.resolve("classes"));
        assertEquals(0, ToolProvider.getSystemJavaCompiler()
                        .run(null, null, null, "-d", classes.toString(), sourceFile.toString()),
                "could not compile the " + artifact + " stub");

        Files.createDirectories(module);
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        try (var out = new JarOutputStream(Files.newOutputStream(module.resolve(artifact + "-1.0.jar")), manifest);
             var files = Files.walk(classes)) {
            for (var file : files.filter(Files::isRegularFile).toList()) {
                out.putNextEntry(new ZipEntry(classes.relativize(file).toString().replace('\\', '/')));
                out.write(Files.readAllBytes(file));
                out.closeEntry();
            }
        }
        Files.writeString(module.resolve(artifact + "-1.0.pom"), pom("net.minecraftforge", artifact, "1.0"));
    }

    // Small binary fixtures

    private static Path minecraftJar(Path jar, String resource) throws IOException {
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("net/minecraft/Block.class"));
            out.write(classBytes("net/minecraft/Block"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry(resource));
            out.write("resource".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar;
    }

    private static byte[] classArchive() {
        return archive("net/minecraft/Block.class", null);
    }

    private static byte[] archive(String entry, String content) {
        var entries = new LinkedHashMap<String, String>();
        entries.put(entry, content);
        return archive(entries);
    }

    private static byte[] archive(Map<String, String> entries) {
        var buffer = new ByteArrayOutputStream();
        try (var out = new JarOutputStream(buffer)) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue() == null
                        ? classBytes("net/minecraft/Block")
                        : entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    private static byte[] binaryArchive(Map<String, byte[]> entries) {
        var buffer = new ByteArrayOutputStream();
        try (var out = new JarOutputStream(buffer)) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    private static void seedModule(Path projectDir, String artifact) throws IOException {
        artifact = "fixture-" + artifact;
        var module = Files.createDirectories(projectDir.resolve("local-maven/com/cleanroommc/" + artifact + "/1"));
        Files.write(module.resolve(artifact + "-1.jar"), archive("marker.txt", artifact));
        Files.writeString(module.resolve(artifact + "-1.pom"), pom("com.cleanroommc", artifact, "1"));
    }

    private static byte[] csv(String header) {
        return (header + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] tsrg() {
        return "tsrg2 obf srg\n".getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] classBytes(String name) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private UserdevFixture() { }

}
