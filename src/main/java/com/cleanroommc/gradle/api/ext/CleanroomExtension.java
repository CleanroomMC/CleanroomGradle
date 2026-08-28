package com.cleanroommc.gradle.api.ext;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.source.BundledVersionMetaValueSource;
import com.cleanroommc.gradle.api.source.VersionMetaValueSource;
import com.cleanroommc.gradle.api.util.EnumValues;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.io.File;

public abstract class CleanroomExtension {

    public static CleanroomExtension get(Project project) {
        return project.getExtensions().getByType(CleanroomExtension.class);
    }

    private final CachesExtension caches;
    private final MinecraftExtension minecraft;
    private final MappingsExtension mappings;
    private final PatchesExtension patches;
    private final LoaderExtension loader;
    private final UserdevExtension userdev;
    private final Property<ProjectMode> mode;

    public Property<ProjectMode> getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode.set(EnumValues.parse(ProjectMode.class, mode));
    }

    public abstract NamedDomainObjectContainer<VanillaEnvironment> getVanilla();

    @Inject
    public CleanroomExtension(Project project, ObjectFactory objects) {
        this.caches = objects.newInstance(CachesExtension.class);
        this.minecraft = objects.newInstance(MinecraftExtension.class);
        this.mappings = objects.newInstance(MappingsExtension.class);
        this.patches = objects.newInstance(PatchesExtension.class);
        this.loader = objects.newInstance(LoaderExtension.class);
        this.userdev = objects.newInstance(UserdevExtension.class);
        this.mode = objects.property(ProjectMode.class);

        final var providers = project.getProviders();

        this.caches.getDirectory().fileValue(new File(project.getGradle().getGradleUserHomeDir(), "caches/" + Meta.CG_FOLDER));
        this.caches.getVersionDirectory().convention(this.caches.getDirectory().dir("versions/" + Meta.ONE_TRUE_MINECRAFT_VERSION));
        this.caches.getLocalDirectory().convention(project.getLayout().getBuildDirectory().dir(Meta.CG_FOLDER));

        this.getMode().convention(ProjectMode.USERDEV);
        this.caches.getDiscardIntermediates().convention(this.getMode().map(mode -> mode != ProjectMode.LOADER));

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

    public UserdevExtension getUserdev() {
        return userdev;
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

    public void userdev(Action<UserdevExtension> action) {
        action.execute(userdev);
    }

}
