package com.cleanroommc.gradle.api.task.common;

import com.cleanroommc.gradle.api.task.MavenJarExec;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the Cleanflower decompiler. Decompiler options are passed as free-form key/value pairs through
 * {@link #getOptions()} rather than dedicated properties, since the tool can be swapped out
 * and each tool version (may) define its own option set.
 *
 * <p>The default tool is {@code com.cleanroommc:cleanflower:1.0.0}, resolved through the {@code decompiler}
 * configuration. Adding a dependency to that configuration replaces the default. The option names documented
 * on {@link #getOptions()} is the cleanflower v1.0.0 option set.</p>
 */
@CacheableTask
public abstract class Decompile extends MavenJarExec {

    private static String argValue(Object value) {
        return value instanceof Boolean bool ? (bool ? "1" : "0") : value.toString();
    }

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getCompiledJar();

    @Optional
    @Classpath
    @InputFiles
    public abstract ConfigurableFileCollection getLibraries();

    /**
     * Decompiler options, rendered as {@code --<key>=<value>} arguments in insertion order. Booleans are
     * rendered as {@code 1}/{@code 0}; other values via {@code toString()}. Unset options fallback to the
     * tool's own defaults. Values that only exist as bare flags (no {@code =value} form) belong in
     * {@link #getExtraArgs()} instead.
     *
     * <p><b>Valid option keys for cleanflower v1.0.0.</b> Overriding the tool through the {@code decompiler}
     * configuration may void this list, consult the replacement tool's documentation instead.</p>
     *
     * <p>Core options:</p>
     * <ul>
     * <li>{@code remove-bridge}: remove {@code bridge} methods from the output</li>
     * <li>{@code remove-synthetic}: remove {@code synthetic} methods and fields from the output</li>
     * <li>{@code decompile-inner}: process inner classes and add them to the output</li>
     * <li>{@code decompile-java4}: resugar the Java 1-4 class reference format</li>
     * <li>{@code decompile-assert}: decompile assert statements</li>
     * <li>{@code hide-empty-super}: hide {@code super()} calls with no parameters</li>
     * <li>{@code hide-default-constructor}: hide constructors with no parameters and no code</li>
     * <li>{@code decompile-generics}: decompile generics in classes, methods, fields and variables</li>
     * <li>{@code incorporate-returns}: integrate returns better in try-catch blocks</li>
     * <li>{@code ensure-synchronized-monitors}: deduce missing {@code monitorexits} for {@code synchronized} blocks</li>
     * <li>{@code decompile-enums}: decompile enums</li>
     * <li>{@code decompile-preview}: decompile preview/incubating Java features</li>
     * <li>{@code remove-getclass}: remove synthetic {@code getClass()} calls from {@code obj.new Inner()} constructs</li>
     * <li>{@code keep-literals}: keep NaN, infinities and pi values as is</li>
     * <li>{@code boolean-as-int}: represent integers 0 and 1 as booleans</li>
     * <li>{@code ascii-strings}: escape non-ASCII characters in string and character literals</li>
     * <li>{@code synthetic-not-set}: treat some known structures as synthetic even when not marked</li>
     * <li>{@code undefined-as-object}: treat nameless types as java.lang.Object</li>
     * <li>{@code use-lvt-names}: use LVT names for locals and parameters instead of var&lt;index&gt;_&lt;version&gt;</li>
     * <li>{@code use-method-parameters}: use names from the {@code MethodParameters} attribute</li>
     * <li>{@code remove-empty-try-catch}: remove {@code try-catch} blocks with no code</li>
     * <li>{@code decompile-finally}: decompile {@code finally} blocks</li>
     * <li>{@code lambda-to-anonymous-class}: decompile lambdas as anonymous classes</li>
     * <li>{@code bytecode-source-mapping}: map bytecode to source lines</li>
     * <li>{@code dump-code-lines}: dump line mappings to output archive zip entry extra data</li>
     * <li>{@code ignore-invalid-bytecode}: ignore malformed bytecode</li>
     * <li>{@code verify-anonymous-classes}: verify that anonymous classes are local</li>
     * <li>{@code ternary-constant-simplification}: fold ternary branches with boolean constants</li>
     * <li>{@code pattern-matching}: decompile with if and switch pattern matching</li>
     * <li>{@code try-loop-fix}: fix rare malformed decompilation of try blocks inside while loops</li>
     * <li>{@code ternary-in-if}: [experimental] collapse if statements with a ternary in their condition</li>
     * <li>{@code decompile-switch-expressions}: decompile switch expressions</li>
     * <li>{@code show-hidden-statements}: [debug] display hidden code blocks</li>
     * <li>{@code override-annotation}: display override annotations for methods known to the decompiler</li>
     * <li>{@code simplify-stack}: simplify variables across stack bounds to resugar complex statements</li>
     * <li>{@code verify-merges}: [experimental] verify the validity of variable merges harder</li>
     * <li>{@code old-try-dedup}: [experimental] use the old try deduplication algorithm</li>
     * <li>{@code include-classpath}: include the entire classpath when resolving references</li>
     * <li>{@code include-runtime}: '1' or 'current' for the current Java runtime, or a path to another runtime</li>
     * <li>{@code explicit-generics}: put explicit diamond generic arguments on method calls</li>
     * <li>{@code inline-simple-lambdas}: remove braces on simple one-line lambdas</li>
     * <li>{@code log-level}: one of 'info', 'debug', 'warn', 'error'</li>
     * <li>{@code max-time-per-method}: [deprecated] maximum time in seconds to process a method</li>
     * <li>{@code rename-members}: rename classes, fields and methods with a number suffix to help deobfuscation</li>
     * <li>{@code user-renamer-class}: path to a class implementing IIdentifierRenamer</li>
     * <li>{@code new-line-separator}: use \n line separators instead of the OS default</li>
     * <li>{@code indent-string}: indentation string</li>
     * <li>{@code preferred-line-length}: max line length before formatting is applied</li>
     * <li>{@code banner}: message to display at the top of each decompiled file</li>
     * <li>{@code error-message}: message to display when an error occurs in the decompiler</li>
     * <li>{@code thread-count}: decompilation thread count, -1 uses all available cores</li>
     * <li>{@code skip-extra-files}: skip copying non-class files to the output</li>
     * <li>{@code warn-inconsistent-inner-attributes}: warn about inconsistent inner class attributes</li>
     * <li>{@code dump-bytecode-on-error}: put the bytecode in the method body when an error occurs</li>
     * <li>{@code dump-exception-on-error}: put the exception message in the method body when an error occurs</li>
     * <li>{@code decompiler-comments}: add decompiler comments about odd bytecode or unfixable problems</li>
     * <li>{@code sourcefile-comments}: add debug comments showing the class SourceFile attribute</li>
     * <li>{@code decompile-complex-constant-dynamic}: decompile complex constant-dynamic expressions to a similar non-lazy expression</li>
     * <li>{@code force-jsr-inline}: force processing of JSR instructions even in Java 7+ class files</li>
     * <li>{@code dump-text-tokens}: dump text tokens on each class file</li>
     * <li>{@code remove-imports}: remove import statements from the decompiled code</li>
     * <li>{@code mark-corresponding-synthetics}: mark lambdas and anonymous/local classes with their synthetic constructs</li>
     * <li>{@code excluded-classes}: exclude classes whose fully qualified names match this regular expression</li>
     * <li>{@code validate-inner-classes-names}: skip inner classes whose names are not {@code '$'}-separated correctly</li>
     * </ul>
     *
     * <p>Bundled plugin options:</p>
     * <ul>
     * <li>{@code kt-enable}: enable the Kotlin plugin</li>
     * <li>{@code kt-show-public}: show public visibility in Kotlin output</li>
     * <li>{@code kt-unknown-defaults}: string to use for unknown Kotlin default arguments</li>
     * <li>{@code kt-collapse-string-concat}: collapse string concatenation in Kotlin output</li>
     * <li>{@code variable-renaming}: variable renamer to use (e.g. 'jad', 'tiny')</li>
     * <li>{@code rename-parameters}: rename parameters as well as locals</li>
     * <li>{@code jad-style-variable-naming}: use JAD-style variable naming</li>
     * <li>{@code jad-style-parameter-naming}: use JAD-style parameter naming</li>
     * <li>{@code resugar-idea-notnull}: resugar IntelliJ IDEA's @NotNull assertions</li>
     * </ul>
     */
    @Input
    public abstract MapProperty<String, Object> getOptions();

    /** {@code -only=<prefix>}: only decompile classes matching these prefixes */
    @Input
    @Optional
    public abstract ListProperty<String> getOnlyClasses();

    /** {@code --silent}: suppress the decompiler's console output */
    @Input
    @Optional
    public abstract Property<Boolean> getSilent();

    /**
     * Additional raw decompiler arguments, appended after {@link #getOptions()}
     */
    @Input
    public abstract ListProperty<String> getExtraArgs();

    @OutputFile
    public abstract RegularFileProperty getDecompiledJar();

    public Decompile() {
        this.getMainClass().convention("org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler");
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("new-line-separator", true);
        defaults.put("ascii-strings", true);
        defaults.put("include-classpath", true);
        defaults.put("jad-style-variable-naming", true);
        defaults.put("thread-count", -1);
        defaults.put("indent-string", "    ");
        this.getOptions().convention(defaults);
        this.getOnlyClasses().convention(List.of());
        this.getSilent().convention(false);
        this.getExtraArgs().convention(List.of());
    }

    @Override
    protected void beforeExec() {
        this.getLogger().lifecycle("Using Java {} to decompile", this.getJavaLauncher().get().getMetadata().getLanguageVersion());
        if (!this.getUseDefaultToolArguments().get()) {
            return;
        }
        for (var entry : this.getOptions().get().entrySet()) {
            this.args("--" + entry.getKey() + "=" + argValue(entry.getValue()));
        }
        // Extra args must precede --silent/-only/-e as the CLI stops parsing --option=value arguments after
        this.args(this.getExtraArgs().get());
        if (this.getSilent().getOrElse(false)) {
            this.args("--silent");
        }
        for (var prefix : this.getOnlyClasses().get()) {
            this.args("-only=" + prefix);
        }
        for (var file : this.getLibraries().getFiles()) {
            this.args("-e=" + file.getAbsolutePath());
        }
        this.args(this.getCompiledJar(), this.getDecompiledJar());
    }

}
