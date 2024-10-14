package me.mrnavastar.lyra;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static me.mrnavastar.lyra.Lyra.GSON;

public class LyraProject {

    public static class Artifact {
        public String Coordinate;
        public String Path;
    }

    public String Home;
    public List<Artifact> Libraries;

    public boolean isValid() {
        return Home != null && Libraries != null;
    }

    public static LyraProject fromInputStream(InputStream inputStream) {
        return GSON.fromJson(new InputStreamReader(inputStream), LyraProject.class);
    }

    private String getArtifactPath(Artifact artifact) {
        String url = VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(Home + "/libs/" + artifact.Path));
        return url.replaceAll("file:", "jar:") + "!/";
    }

    public void sync(Project project) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> {
                    Module[] modules = ModuleManager.getInstance(project).getModules();
                    ModifiableRootModel modifiableRootModel = ModuleRootManager.getInstance(modules[0]).getModifiableModel();
                    LibraryTable.ModifiableModel libraryTableModel = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getModifiableModel();

                    // Link missing libraries
                    for (Artifact lib : Libraries) {
                        if (Arrays.stream(libraryTableModel.getLibraries()).anyMatch(l -> Objects.equals(l.getName(), lib.Coordinate))) continue;

                        Library library = libraryTableModel.createLibrary(lib.Coordinate);
                        Library.ModifiableModel libraryModel = library.getModifiableModel();
                        libraryModel.addRoot(getArtifactPath(lib), OrderRootType.CLASSES);
                        libraryModel.commit();

                        LibraryOrderEntry libraryOrderEntry = modifiableRootModel.addLibraryEntry(library);
                        libraryOrderEntry.setScope(DependencyScope.COMPILE);
                    }

                    // Unlink unneeded Libraries
                    Arrays.stream(libraryTableModel.getLibraries())
                            .filter(l -> Libraries.stream().noneMatch(ll -> ll.Coordinate.equals(l.getName())))
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

                    modifiableRootModel.commit();
                    libraryTableModel.commit();
                },
                "Lyra - Synchronizing Project", false, project
        );
    }
}