package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.Meta;
import org.gradle.api.Plugin;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.toolchains.foojay.FoojayToolchainsConventionPlugin;

/**
 * To inject the dependency repositories a Cleanroom workspace needs, for the consuming
 * builds do not have to declare them by hand.
 *
 * <p>Applied in {@code settings.gradle(.kts)} via {@code id("com.cleanroommc.gradle.settings")},
 * after the plugin itself has been resolved through the consumer's own
 * {@code pluginManagement.repositories} (that bootstrap step cannot be short-circuited by this
 * plugin, since it has to run first to make this class available at all).</p>
 *
 * <p>The repositories are registered on {@link Settings#getDependencyResolutionManagement()}, i.e.
 * as the build-wide defaults. Per Gradle's default {@code RepositoriesMode.PREFER_PROJECT}, any
 * project that still declares its own {@code repositories { ... }} block keeps using that instead.
 * These only take effect once a project (or the whole build) has none of its own.</p>
 */
public class CleanroomGradleSettings implements Plugin<Settings> {

    @Override
    public void apply(Settings settings) {
        this.addRepositories(settings);
        settings.getPluginManager().apply(FoojayToolchainsConventionPlugin.class);
    }

    private void addRepositories(Settings settings) {
        var repositories = settings.getDependencyResolutionManagement().getRepositories();
        repositories.mavenCentral();
        maven(repositories, "Mojang Libraries", Meta.MOJANG_REPO, false);
        maven(repositories, "MinecraftForge", Meta.FORGE_REPO, true);
        maven(repositories, "CleanroomMC", Meta.CLEANROOM_REPO, true);
    }

    private void maven(RepositoryHandler repositories, String name, String url, boolean artifactMetadata) {
        repositories.maven((MavenArtifactRepository repo) -> {
            repo.setName(name);
            repo.setUrl(url);
            if (artifactMetadata) {
                repo.getMetadataSources().artifact();
            }
        });
    }

}
