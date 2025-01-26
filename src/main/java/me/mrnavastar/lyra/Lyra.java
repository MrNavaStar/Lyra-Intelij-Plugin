package me.mrnavastar.lyra;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Lyra {

    private static String parsePath(String path) {
        String url = VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(path));
        return url.replaceAll("file:", "jar:") + "!/";
    }

    public static CompletableFuture<List<String>> getProjectClasspath(Project project, Path projectRoot) {
        CompletableFuture<List<String>> future = new CompletableFuture<>();

        GeneralCommandLine cmd = new GeneralCommandLine("lyra", "classpath")
                .withCharset(StandardCharsets.UTF_8)
                .withWorkDirectory(projectRoot.toFile());

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Lyra : syncing dependencies") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    List<String> lines = ExecUtil.execAndGetOutput(cmd).getStdoutLines();
                    ArrayList<String> classpath = new ArrayList<>();
                    for (String s : lines.get(lines.size() -1).split(";")) classpath.add(parsePath(s));
                    future.complete(classpath);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return future;
    }
}
