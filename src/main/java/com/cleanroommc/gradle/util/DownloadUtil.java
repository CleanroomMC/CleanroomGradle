package com.cleanroommc.gradle.util;

import com.cleanroommc.gradle.json.schema.VersionMetadata;
import com.google.common.base.Suppliers;
import de.undercouch.gradle.tasks.download.Download;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class DownloadUtil {
    private final static Consumer<Config> defaultConfigurator = config -> {
        config.numberOfDownloadAttempts(5);
        config.alwaysCheckSha(false);
    };
    private final static DefaultConfig defaultConfig = new DefaultConfig();

    static {
        defaultConfigurator.accept(defaultConfig);
    }

    private final static String exceptionMessage = String.format("Illegal data type for download task creation, accepted data types List<%s>, %s",
            IDownload.class.getSimpleName(), IDownload.class.getSimpleName());


    /**
     * @param project   the project instance
     * @param taskName  the task name
     * @param data      a data Supplier, must be a IDownload object or a list of IDownload
     * @param targetDir the target download dir
     * @param dependsOn the tasks to depend on
     * @return the download task
     * @see IDownload
     */
    public static TaskProvider<Download>
    registerDynamicDownloadTaskWithSha1Validation(
            final Project project,
            final String taskName,
            final com.google.common.base.Supplier<Object> data,
            final com.google.common.base.Supplier<File> targetDir,
            final Object... dependsOn) {
        final Supplier<Object> memoizedData = Suppliers.memoize(data);
        final Supplier<File> memoizedTargetDir = Suppliers.memoize(targetDir);

        return project.getTasks().register(taskName, Download.class, task -> {
            task.dependsOn(dependsOn);
            // the provider is used to enable lazy evaluation
            // this is needed because ManifestExtension will not be initialized by the other tasks
            task.src(project.provider(() -> filterData(memoizedData)));
            task.dest(project.provider(memoizedTargetDir::get));
            task.overwrite(false);
            task.onlyIfModified(true);
            task.useETag(true);
            task.doLast("validateSha1", action -> {
                var obj = memoizedData.get();
                if (obj instanceof List<?> dataList) {
                    for (Object objectDownload : dataList) {
                        if (objectDownload instanceof IDownload download) {
                            var file = getFileForUrl(memoizedTargetDir.get(), download.url());
                            CacheUtil.checkShaForFileAndDeleteFile(project, file, download.sha1());
                        } else {
                            throw new IllegalArgumentException(exceptionMessage);
                        }
                    }
                } else if (obj instanceof IDownload download) {
                    var file = getFileForUrl(memoizedTargetDir.get(), download.url());
                    CacheUtil.checkShaForFileAndDeleteFile(project, file, download.sha1());
                } else {
                    throw new IllegalArgumentException(exceptionMessage);
                }
            });
        });
    }


    /**
     * @param project   the project instance
     * @param taskName  the task name
     * @param data      a data Supplier, must either be a URL, a CharSequence, a Collection or an array
     * @param targetDir the target download dir
     * @param dependsOn the tasks to depend on
     * @return the download task
     */
    public static TaskProvider<Download> registerDynamicDownloadTask(
            final Project project,
            final String taskName,
            final Supplier<Object> data,
            final Supplier<File> targetDir,
            final Object... dependsOn) {

        return project.getTasks().register(taskName, Download.class, task -> {
            task.dependsOn(dependsOn);
            // the provider is used to enable lazy evaluation
            // this is needed because ManifestExtension will not be initialized by the other tasks
            task.src(project.provider(data::get));
            task.dest(project.provider(targetDir::get));
            task.overwrite(false);
            task.onlyIfModified(true);
            task.useETag(true);
        });
    }

    private static Object filterData(Supplier<Object> data) {
        var objectData = data.get();

        if (objectData instanceof List<?> objectDataList) {
            return objectDataList.stream().map(objectDataElement -> {
                if (objectDataElement instanceof IDownload download) {
                    return download.url();
                } else {
                    throw new IllegalArgumentException(exceptionMessage);
                }
            }).toList();
        }

        if (objectData instanceof IDownload download) {
            return download.url();
        } else {
            throw new IllegalArgumentException(exceptionMessage);
        }
    }

    public static File getFileForUrl(File dir, String url) {
        return new File(dir, getNameForUrl(url));
    }

    public static String getNameForUrl(String url) {
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url.substring(url.lastIndexOf('/') + 1);
    }

    public static <D> void downloadFiles(List<D> downloads, Function<D, IDownload> mapper) {
        downloadFiles(downloads.stream().map(mapper).toList());
    }

    public static <D> void downloadFiles(List<D> downloads, Consumer<Config> config, Function<D, IDownload> mapper) {
        downloadFiles(downloads.stream().map(mapper).toList(), config);
    }

    public static <D> void downloadFile(D download, Function<D, IDownload> mapper) {
        downloadFile(mapper.apply(download));
    }

    public static <D> void downloadFile(D download, Consumer<Config> config, Function<D, IDownload> mapper) {
        downloadFile(mapper.apply(download), config);
    }

    public static void downloadFiles(List<IDownload> downloads) {
        for (IDownload download : downloads) downloadFile(download);
    }

    public static void downloadFiles(List<IDownload> downloads, Consumer<Config> config) {
        for (IDownload download : downloads) downloadFile(download, config);
    }

    public static void downloadFile(IDownload download) {
        downloadFile(download, defaultConfigurator);
    }

    public static void downloadFile(IDownload download, Consumer<Config> configurator) {
        final DefaultConfig config;
        if (configurator != defaultConfigurator) {
            var newConfig = new DefaultConfig();
            defaultConfigurator.accept(newConfig);
            configurator.accept(newConfig);
            config = newConfig;
        } else config = defaultConfig;

        if (config.alwaysCheckSha) {
            if (download.file().exists()) {
                if (!validSha(download, false)) return;
                download.file().delete();
            }
        } else if (download.file().exists()) return;

        for (int retry = 0; retry < config.attempts; retry++) {
            try (BufferedInputStream bis = new BufferedInputStream(new URL(download.url()).openStream());
                 FileOutputStream fos = new FileOutputStream(download.file());
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                IOUtils.copy(bis, bos);
                bos.flush();
                System.out.printf("Downloaded %s\n", download.name());
                break;
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                System.err.println(e);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
        }

        validSha(download, true);
    }

    private static boolean validSha(IDownload download, boolean doThrow) {
        try {
            CacheUtil.checkSha1ForFile(download.file(), download.sha1());
        } catch (CacheUtil.ChecksumMismatch e) {
                if (doThrow) throw new RuntimeException(e);
                System.err.println(e);
                return false;
        }
        return true;
    }

    public interface IDownload {
        String name();

        String sha1();

        String url();

        File file();
    }

    // TODO add more configs
    public interface Config {
        void alwaysCheckSha(boolean check);

        void numberOfDownloadAttempts(int retries);
    }

    private static class DefaultConfig implements Config {
        private boolean alwaysCheckSha;

        @Override
        public void alwaysCheckSha(boolean alwaysCheckSha) {
            this.alwaysCheckSha = alwaysCheckSha;
        }

        private int attempts;

        @Override
        public void numberOfDownloadAttempts(int attempts) {
            this.attempts = attempts;
        }
    }

    public static IDownload toIDownloadRelativeFile(final VersionMetadata.Download download, final File baseDir) {
        return toIDownload(download, download.relativeFile(baseDir));
    }

    public static IDownload toIDownload(final VersionMetadata.Download download, final File targetFile) {
        return new IDownload() {
            @Override
            public String name() {
                return download.path();
            }

            @Override
            public String sha1() {
                return download.sha1();
            }

            @Override
            public String url() {
                return download.url();
            }

            @Override
            public File file() {
                return targetFile;
            }
        };
    }

}