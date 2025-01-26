package me.mrnavastar.lyra;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Plugin implements BulkFileListener {

    private final String LYRA = "lyra.json";

    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (VFileEvent event : events) {
            VirtualFile file = event.getFile();
            if (file == null || !file.getPath().endsWith(LYRA)) continue;

            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (project.isDisposed()
                        || !ProjectRootManager.getInstance(project).getFileIndex().isInContent(file)
                        && !ProjectRootManager.getInstance(project).getFileIndex().isInProject(file)) continue;

                sync(project, file);
            }
        }
    }

    public void sync(Project project, VirtualFile lyra) {
        Lyra.getProjectClasspath(project, Path.of(lyra.getPath()).getParent()).whenComplete((artifacts, throwable) -> ApplicationManager.getApplication().invokeLater(() -> {
            Module[] modules = ModuleManager.getInstance(project).getModules();
            ModifiableRootModel modifiableRootModel = ModuleRootManager.getInstance(modules[0]).getModifiableModel();
            LibraryTable.ModifiableModel libraryTableModel = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getModifiableModel();

            System.out.println(project.getName());
            System.out.println("adding library: " + artifacts);

            // Link missing libraries
            for (String artifact : artifacts) {
                if (Arrays.stream(libraryTableModel.getLibraries()).anyMatch(l -> Objects.equals(l.getName(), artifact)))
                    continue;

                Library library = libraryTableModel.createLibrary(artifact);
                Library.ModifiableModel libraryModel = library.getModifiableModel();
                libraryModel.addRoot(artifact, OrderRootType.CLASSES);
                libraryModel.commit();

                LibraryOrderEntry libraryOrderEntry = modifiableRootModel.addLibraryEntry(library);
                libraryOrderEntry.setScope(DependencyScope.COMPILE);
            }

            // Unlink unneeded Libraries
            Arrays.stream(libraryTableModel.getLibraries())
                    .filter(l -> artifacts.stream().noneMatch(artifact -> Objects.equals(l.getName(), artifact)))
                    .forEach(l -> {
                        LibraryOrderEntry entry = null;
                        for (OrderEntry orderEntry : modifiableRootModel.getOrderEntries()) {
                            if (orderEntry instanceof LibraryOrderEntry libraryOrderEntry && Objects.equals(libraryOrderEntry.getLibrary(), l)) {
                                entry = libraryOrderEntry;
                            }
                        }
                        if (entry == null) return;

                        modifiableRootModel.removeOrderEntry(entry);
                        libraryTableModel.removeLibrary(l);
                    });

            WriteAction.run(() -> {
                modifiableRootModel.commit();
                libraryTableModel.commit();
            });
        }));
    }
}