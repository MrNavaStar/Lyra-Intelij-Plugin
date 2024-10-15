package me.mrnavastar.lyra;

import com.google.gson.Gson;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class Lyra implements ProjectActivity, BulkFileListener {

    public static final Gson GSON = new Gson();
    private final String LYRA = "lyra.json";

    @Override
    public @Nullable Object execute(@NotNull Project p, @NotNull Continuation<? super Unit> continuation) {
        try {
            LyraProject project = ReadAction.compute(() -> {
                Optional<VirtualFile> vfile = FilenameIndex.getVirtualFilesByName(LYRA, GlobalSearchScope.allScope(p)).stream().findFirst();
                return vfile.isPresent() ? LyraProject.fromInputStream(vfile.get().getInputStream()) : null;
            });

            if (project != null && project.isValid()) project.sync(p);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (VFileEvent event : events) {
            VirtualFile file = event.getFile();
            if (file == null || !file.getPath().endsWith(LYRA)) continue;

            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (project.isDisposed()
                        || !ProjectRootManager.getInstance(project).getFileIndex().isInContent(file)
                        && !ProjectRootManager.getInstance(project).getFileIndex().isInProject(file)) continue;

                try {
                    LyraProject meta = LyraProject.fromInputStream(file.getInputStream());
                    if (!meta.isValid()) return;
                    meta.sync(project);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}