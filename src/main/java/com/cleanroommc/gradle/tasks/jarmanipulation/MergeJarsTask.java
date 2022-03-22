package com.cleanroommc.gradle.tasks.jarmanipulation;

import com.cleanroommc.gradle.extensions.MinecraftExtension;
import com.cleanroommc.gradle.util.Utils;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import groovy.lang.Closure;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static com.cleanroommc.gradle.Constants.*;

// TODO: This needs to be rewritten, very restrictive and awful from Forge
public class MergeJarsTask extends DefaultTask {

    public static void setupMergeJarsTask(Project project) {
        MergeJarsTask mergeJarsTask = Utils.createTask(project, MERGE_JARS_TASK, MergeJarsTask.class);
        mergeJarsTask.setClientJar(Utils.closure(() -> MINECRAFT_CLIENT_FILE.apply(MinecraftExtension.get(project).getVersion())));
        mergeJarsTask.setServerJar(Utils.closure(() -> MINECRAFT_SERVER_PURE_FILE.apply(MinecraftExtension.get(project).getVersion())));
        mergeJarsTask.setOutputJar(Utils.closure(() -> MINECRAFT_MERGED_FILE.apply(MinecraftExtension.get(project).getVersion())));
        mergeJarsTask.dependsOn(Utils.getTask(project, DL_MINECRAFT_CLIENT_TASK), project.getTasks().getByPath(SPLIT_SERVER_JAR_TASK));
    }

    @InputFile private Closure<File> clientJar, serverJar;

    private Closure<File> outputJar;

    @OutputFile private File outputJarResolved;

    @TaskAction
    public void merge() throws IOException {
        processJar(getClientJar(), getServerJar(), getOutputJar());
    }

    public File getClientJar() {
        return clientJar.call();
    }

    public void setClientJar(Closure<File> clientJar) {
        this.clientJar = clientJar;
    }

    public File getServerJar() {
        return serverJar.call();
    }

    public void setServerJar(Closure<File> serverJar) {
        this.serverJar = serverJar;
    }

    public File getOutputJar() {
        if (outputJarResolved == null) {
            outputJarResolved = outputJar.call();
            outputJarResolved.getParentFile().mkdirs();
            try {
                outputJarResolved.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return outputJarResolved;
    }

    public void setOutputJar(Closure<File> outputJar) {
        this.outputJar = outputJar;
    }

    private void processJar(File clientInFile, File serverInFile, File outFile) throws IOException {
        try (ZipFile cInJar = new ZipFile(clientInFile);
             ZipFile sInJar = new ZipFile(serverInFile);
             ZipOutputStream outJar = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)))) {
            // Read in the jars, and initalize some variables
            HashSet<String> resources = new HashSet<>();
            HashMap<String, ZipEntry> cClasses = getClassEntries(cInJar, outJar, resources);
            HashMap<String, ZipEntry> sClasses = getClassEntries(sInJar, outJar, resources);
            HashSet<String> cAdded = new HashSet<>();
            // Start processing
            for (Entry<String, ZipEntry> entry : cClasses.entrySet()) {
                String name = entry.getKey();
                ZipEntry cEntry = entry.getValue();
                ZipEntry sEntry = sClasses.get(name);
                if (sEntry == null) {
                    copyClass(cInJar, cEntry, outJar, true);
                    cAdded.add(name);
                    continue;
                }
                sClasses.remove(name);
                byte[] cData = readEntry(cInJar, entry.getValue());
                byte[] sData = readEntry(sInJar, sEntry);
                byte[] data = processClass(cData, sData);
                ZipEntry newEntry = new ZipEntry(cEntry.getName());
                try {
                    outJar.putNextEntry(newEntry);
                    outJar.write(data);
                } finally {
                    outJar.closeEntry();
                }
                cAdded.add(name);
            }
            for (Entry<String, ZipEntry> entry : sClasses.entrySet()) {
                copyClass(sInJar, entry.getValue(), outJar, false);
            }
            for (String name : new String[] { SideOnly.class.getName(), Side.class.getName() }) {
                String eName = name.replace(".", "/");
                String classPath = eName + ".class";
                ZipEntry newEntry = new ZipEntry(classPath);
                if (!cAdded.contains(eName)) {
                    try {
                        outJar.putNextEntry(newEntry);
                        outJar.write(getClassBytes(name));
                    } finally {
                        outJar.closeEntry();
                    }
                }
            }

        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("Could not open input/output file: " + e.getMessage());
        }
    }

    private void copyClass(ZipFile inJar, ZipEntry entry, ZipOutputStream outJar, boolean isClientOnly) throws IOException {
        ClassReader reader = new ClassReader(readEntry(inJar, entry));
        ClassNode classNode = new ClassNode();

        reader.accept(classNode, 0);

        if (classNode.visibleAnnotations == null) {
            classNode.visibleAnnotations = new ArrayList<AnnotationNode>();
        }
        classNode.visibleAnnotations.add(getSideAnn(isClientOnly));

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        byte[] data = writer.toByteArray();

        ZipEntry newEntry = new ZipEntry(entry.getName());
        if (outJar != null) {
            outJar.putNextEntry(newEntry);
            outJar.write(data);
        }
    }

    private byte[] readEntry(ZipFile inFile, ZipEntry entry) throws IOException {
        try (InputStream is = inFile.getInputStream(entry)) {
            return ByteStreams.toByteArray(is);
        }
    }

    private AnnotationNode getSideAnn(boolean isClientOnly) {
        AnnotationNode ann = new AnnotationNode(Type.getDescriptor(SideOnly.class));
        ann.values = new ArrayList<>();
        ann.values.add("value");
        ann.values.add(new String[] { Type.getDescriptor(Side.class), isClientOnly ? "CLIENT" : "SERVER" });
        return ann;
    }

    /**
     * @param inFile From which to read classes and resources
     * @param outFile The place to write resources and ignored classes
     * @param resources The registry to add resources to, and to check against.
     * @return HashMap of all the desired Classes and their ZipEntrys
     * @throws IOException
     */
    private HashMap<String, ZipEntry> getClassEntries(ZipFile inFile, ZipOutputStream outFile, HashSet<String> resources) throws IOException {
        HashMap<String, ZipEntry> ret = new HashMap<>();
        for (ZipEntry entry : Collections.list(inFile.entries())) {
            String entryName = entry.getName();
            // Always skip the manifest
            if ("META-INF/MANIFEST.MF".equals(entryName)) {
                continue;
            }
            if (entry.isDirectory()) {
                /*
                 * if (!resources.contains(entryName))
                 * {
                 * outFile.putNextEntry(entry);
                 * }
                 */
                continue;
            }
            if (!entryName.endsWith(".class") || entryName.startsWith(".")) {
                if (!resources.contains(entryName)) {
                    ZipEntry newEntry = new ZipEntry(entryName);
                    outFile.putNextEntry(newEntry);
                    outFile.write(readEntry(inFile, entry));
                    resources.add(entryName);
                }
            } else {
                ret.put(entryName.replace(".class", ""), entry);
            }
        }
        return ret;
    }

    private byte[] getClassBytes(String name) throws IOException {
        // @Forge TODO: rewrite
        try (InputStream classStream = MergeJarsTask.class.getResourceAsStream("/" + name.replace('.', '/').concat(".class"))) {
            return ByteStreams.toByteArray(classStream);
        }
    }

    public byte[] processClass(byte[] cIn, byte[] sIn) {
        ClassNode cClassNode = getClassNode(cIn);
        ClassNode sClassNode = getClassNode(sIn);

        processFields(cClassNode, sClassNode);
        processMethods(cClassNode, sClassNode);
        processInners(cClassNode, sClassNode);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cClassNode.accept(writer);
        return writer.toByteArray();
    }

    private static boolean innerMatches(InnerClassNode o, InnerClassNode o2) {
        if (o.innerName == null && o2.innerName != null) {
            return false;
        }
        if (o.innerName != null && !o.innerName.equals(o2.innerName)) {
            return false;
        }
        if (o.name == null && o2.name != null) {
            return false;
        }
        if (o.name != null && !o.name.equals(o2.name)) {
            return false;
        }
        if (o.outerName == null && o2.outerName != null) {
            return false;
        }
        return o.outerName == null || !o.outerName.equals(o2.outerName);
    }

    private static boolean contains(List<InnerClassNode> list, InnerClassNode node) {
        for (InnerClassNode n : list) {
            if (innerMatches(n, node)) {
                return true;
            }
        }
        return false;
    }

    private static void processInners(ClassNode cClass, ClassNode sClass) {
        List<InnerClassNode> cIners = cClass.innerClasses;
        List<InnerClassNode> sIners = sClass.innerClasses;
        for (InnerClassNode n : cIners) {
            if (!contains(sIners, n)) {
                sIners.add(n);
            }
        }
        for (InnerClassNode n : sIners) {
            if (!contains(cIners, n)) {
                cIners.add(n);
            }
        }
    }

    private ClassNode getClassNode(byte[] data) {
        ClassReader reader = new ClassReader(data);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        return classNode;
    }

    private void processFields(ClassNode cClass, ClassNode sClass) {
        List<FieldNode> cFields = cClass.fields;
        List<FieldNode> sFields = sClass.fields;
        int serverFieldIdx = 0;
        for (int clientFieldIdx = 0; clientFieldIdx < cFields.size(); clientFieldIdx++) {
            FieldNode clientField = cFields.get(clientFieldIdx);
            if (serverFieldIdx < sFields.size()) {
                FieldNode serverField = sFields.get(serverFieldIdx);
                if (!clientField.name.equals(serverField.name)) {
                    boolean foundServerField = false;
                    for (int serverFieldSearchIdx = serverFieldIdx + 1; serverFieldSearchIdx < sFields.size(); serverFieldSearchIdx++) {
                        if (clientField.name.equals(sFields.get(serverFieldSearchIdx).name)) {
                            foundServerField = true;
                            break;
                        }
                    }
                    // Found a server field match ahead in the list - walk to it and add the missing server fields to the client
                    if (foundServerField) {
                        boolean foundClientField = false;
                        for (int clientFieldSearchIdx = clientFieldIdx + 1; clientFieldSearchIdx < cFields.size(); clientFieldSearchIdx++) {
                            if (serverField.name.equals(cFields.get(clientFieldSearchIdx).name)) {
                                foundClientField = true;
                                break;
                            }
                        }
                        if (!foundClientField) {
                            if (serverField.visibleAnnotations == null) {
                                serverField.visibleAnnotations = new ArrayList<AnnotationNode>();
                            }
                            serverField.visibleAnnotations.add(getSideAnn(false));
                            cFields.add(clientFieldIdx, serverField);
                        }
                    } else {
                        if (clientField.visibleAnnotations == null) {
                            clientField.visibleAnnotations = new ArrayList<AnnotationNode>();
                        }
                        clientField.visibleAnnotations.add(getSideAnn(true));
                        sFields.add(serverFieldIdx, clientField);
                    }
                }
            } else {
                if (clientField.visibleAnnotations == null) {
                    clientField.visibleAnnotations = new ArrayList<AnnotationNode>();
                }
                clientField.visibleAnnotations.add(getSideAnn(true));
                sFields.add(serverFieldIdx, clientField);
            }
            serverFieldIdx++;
        }
        if (sFields.size() != cFields.size()) {
            for (int x = cFields.size(); x < sFields.size(); x++) {
                FieldNode sF = sFields.get(x);
                if (sF.visibleAnnotations == null) {
                    sF.visibleAnnotations = new ArrayList<>();
                }
                sF.visibleAnnotations.add(getSideAnn(true));
                cFields.add(x++, sF);
            }
        }
    }

    private static class FieldName implements Function<FieldNode, String> {

        private static FieldName instance = new FieldName();

        @Override
        public String apply(FieldNode in) {
            return in.name;
        }
    }

    private void processMethods(ClassNode cClass, ClassNode sClass) {
        List<MethodNode> cMethods = cClass.methods;
        List<MethodNode> sMethods = sClass.methods;
        LinkedHashSet<MethodWrapper> allMethods = Sets.newLinkedHashSet();
        int cPos = 0;
        int sPos = 0;
        int cLen = cMethods.size();
        int sLen = sMethods.size();
        String clientName = "";
        String lastName = clientName;
        String serverName;
        while (cPos < cLen || sPos < sLen) {
            do {
                if (sPos >= sLen) {
                    break;
                }
                MethodNode sM = sMethods.get(sPos);
                serverName = sM.name;
                if (!serverName.equals(lastName) && cPos != cLen) {
                    break;
                }
                MethodWrapper mw = new MethodWrapper(sM);
                mw.server = true;
                allMethods.add(mw);
                sPos++;
            } while (sPos < sLen);
            do {
                if (cPos >= cLen) {
                    break;
                }
                MethodNode cM = cMethods.get(cPos);
                lastName = clientName;
                clientName = cM.name;
                if (!clientName.equals(lastName) && sPos != sLen) {
                    break;
                }
                MethodWrapper mw = new MethodWrapper(cM);
                mw.client = true;
                allMethods.add(mw);
                cPos++;
            } while (cPos < cLen);
        }

        cMethods.clear();
        sMethods.clear();

        for (MethodWrapper mw : allMethods) {
            cMethods.add(mw.node);
            sMethods.add(mw.node);
            if (!(mw.server && mw.client)) {
                if (mw.node.visibleAnnotations == null) {
                    mw.node.visibleAnnotations = Lists.newArrayListWithExpectedSize(1);
                }
                mw.node.visibleAnnotations.add(getSideAnn(mw.client));
            }
        }
    }

    private static class MethodWrapper {

        public boolean client;
        public boolean server;

        private final MethodNode node;

        public MethodWrapper(MethodNode node) {
            this.node = node;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MethodWrapper)) {
                return false;
            }
            MethodWrapper mw = (MethodWrapper) obj;
            boolean eq = Objects.equal(node.name, mw.node.name) && Objects.equal(node.desc, mw.node.desc);
            if (eq) {
                mw.client = client | mw.client;
                mw.server = server | mw.server;
                client = client | mw.client;
                server = server | mw.server;
            }
            return eq;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(node.name, node.desc);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", node.name)
                    .add("desc", node.desc)
                    .add("server", server)
                    .add("client", client)
                    .toString();
        }

    }

}
