package com.cleanroommc.gradle.util;

import com.cleanroommc.gradle.json.schema.IDownload;
import com.google.common.base.Suppliers;
import de.undercouch.gradle.tasks.download.Download;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

public final class TaskUtil {
    private final static String exceptionMessage = String.format("Illegal data type for download task creation, accepted data types List<%s>, %s",
            IDownload.class.getSimpleName(), IDownload.class.getSimpleName());

    private TaskUtil() {
    }

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
                            var file = TaskUtil.getFileForUrl(memoizedTargetDir.get(), download.url());
                            CacheUtil.checkShaForFileAndDeleteFile(project, file, download.sha1());
                        } else {
                            throw new IllegalArgumentException(exceptionMessage);
                        }
                    }
                } else if (obj instanceof IDownload download) {
                    var file = TaskUtil.getFileForUrl(memoizedTargetDir.get(), download.url());
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

}
