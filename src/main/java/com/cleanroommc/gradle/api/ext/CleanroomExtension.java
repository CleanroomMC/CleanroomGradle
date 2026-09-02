package com.cleanroommc.gradle.api.ext;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.deobf.DeobfHandler;
import com.cleanroommc.gradle.api.deobf.DeobfSpec;
import com.cleanroommc.gradle.api.source.BundledVersionMetaValueSource;
import com.cleanroommc.gradle.api.source.VersionMetaValueSource;
import com.cleanroommc.gradle.api.userdev.UserdevDependency;
import com.cleanroommc.gradle.api.util.EnumValues;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class CleanroomExtension {

    public static CleanroomExtension get(Project project) {
        return project.getExtensions().getByType(CleanroomExtension.class);
    }

    private final Project project;
    private final CachesExtension caches;
    private final MinecraftExtension minecraft;
    private final MappingsExtension mappings;
    private final PatchesExtension patches;
    private final LoaderExtension loader;
    private final Property<ProjectMode> mode;
    private final List<Action<? super ProjectMode>> modeActions = new ArrayList<>();

    public Provider<ProjectMode> getMode() {
        return mode;
    }

    public void setMode(String mode) {
        select(EnumValues.parse(ProjectMode.class, mode));
    }

    public void onModeSelected(Action<? super ProjectMode> action) {
        if (this.mode.isPresent()) {
            action.execute(this.mode.get());
            return;
        }
        this.modeActions.add(action);
    }

    private void select(ProjectMode selected) {
        if (this.mode.isPresent()) {
            var current = this.mode.get();
            if (current == selected) {
                return;
            }
            throw new InvalidUserDataException("Cleanroom environment '" + current.name().toLowerCase()
                    + "' is already registered. A project can register only one Cleanroom environment.");
        }
        this.mode.set(selected);
        var pending = List.copyOf(this.modeActions);
        this.modeActions.clear();
        for (var action : pending) {
            action.execute(selected);
        }
    }

    public abstract NamedDomainObjectContainer<VanillaEnvironment> getVanilla();

    @Inject
    public CleanroomExtension(Project project, ObjectFactory objects) {
        this.project = project;
        this.caches = objects.newInstance(CachesExtension.class);
        this.minecraft = objects.newInstance(MinecraftExtension.class);
        this.mappings = objects.newInstance(MappingsExtension.class);
        this.patches = objects.newInstance(PatchesExtension.class);
        this.loader = objects.newInstance(LoaderExtension.class);
        this.mode = objects.property(ProjectMode.class);

        final var providers = project.getProviders();

        this.caches.getDirectory().fileValue(new File(project.getGradle().getGradleUserHomeDir(), "caches/" + Meta.CG_FOLDER));
        this.caches.getVersionDirectory().convention(this.caches.getDirectory().dir("versions/" + Meta.ONE_TRUE_MINECRAFT_VERSION));
        this.caches.getLocalDirectory().convention(project.getLayout().getBuildDirectory().dir(Meta.CG_FOLDER));

        this.caches.getDiscardIntermediates().convention(this.getMode().map(mode -> mode != ProjectMode.LOADER).orElse(true));

        var versionMetaCacheFile = this.caches.getVersionDirectory().file("meta.json");
        var offline = project.getGradle().getStartParameter().isOffline();
        this.minecraft.getVersionMeta().convention(
                this.minecraft.getVersionMetaUrl()
                        .flatMap(url -> providers.of(VersionMetaValueSource.class, spec -> {
                            spec.getParameters().getCacheFile().set(versionMetaCacheFile);
                            spec.getParameters().getVersionMetaUrl().set(url);
                            spec.getParameters().getOffline().set(offline);
                        }))
                        .orElse(providers.of(BundledVersionMetaValueSource.class, _ -> {}))
        );

        this.patches.getDevelopInitial().convention(false);
        this.patches.getPatchDev().all(env -> env.registerTasks(project, this.caches.getLocalDirectory()));

        this.getVanilla().all(env -> env.register(project, this.caches, this.minecraft));
    }

    /**
     * Kotlin DSL cannot see the {@code deobf} extension as a bare function inside a dependencies block,
     * so it reaches the same handler through {@code cleanroom.deobf(...)}.
     */
    public Dependency deobf(Object notation) {
        return deobfHandler().call(notation);
    }

    public Dependency deobf(Object notation, Action<? super DeobfSpec> action) {
        return deobfHandler().call(notation, action);
    }

    public CachesExtension getCaches() {
        return caches;
    }

    public MinecraftExtension getMinecraft() {
        return minecraft;
    }

    public MappingsExtension getMappings() {
        return mappings;
    }

    public PatchesExtension getPatches() {
        return patches;
    }

    public LoaderExtension getLoader() {
        return loader;
    }

    public void caches(Action<CachesExtension> action) {
        action.execute(caches);
    }

    public void minecraft(Action<MinecraftExtension> action) {
        action.execute(minecraft);
    }

    public void mappings(Action<MappingsExtension> action) {
        action.execute(mappings);
    }

    public void patches(Action<PatchesExtension> action) {
        action.execute(patches);
    }

    public void loader(Action<LoaderExtension> action) {
        action.execute(loader);
    }

    public UserdevDependency userdev(String version) {
        return userdev(version, _ -> { });
    }

    public UserdevDependency userdev(String version, Action<? super UserdevDependency> action) {
        if (getRegisteredUserdev().isPresent()) {
            throw new IllegalStateException("A Cleanroom userdev dependency is already registered for this project.");
        }
        var dependency = new UserdevDependency(this.project, version);
        action.execute(dependency);
        getRegisteredUserdev().set(dependency);
        select(ProjectMode.USERDEV);
        return dependency;
    }

    @Deprecated
    public void userdev(Action<?> ignored) {
        throw removedUserdevContract();
    }

    public abstract Property<UserdevDependency> getRegisteredUserdev();

    private DeobfHandler deobfHandler() {
        return this.project.getDependencies().getExtensions().getByType(DeobfHandler.class);
    }

    private static InvalidUserDataException removedUserdevContract() {
        return new InvalidUserDataException("The old cleanroom.userdev block was removed. Declare "
                + "dependencies { implementation cleanroom.userdev('version') { accessTransformers.from(...) } } instead.");
    }

}
