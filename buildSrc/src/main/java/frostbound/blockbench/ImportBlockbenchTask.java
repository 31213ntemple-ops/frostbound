package frostbound.blockbench;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Converts the Blockbench projects in {@link #getSourceDir()} into game files in {@link #getOutputDir()},
 * which build.gradle adds to the source set's resources, so they're part of every build and run.
 * See {@link BlockbenchImport} for what each kind of project becomes.
 */
public abstract class ImportBlockbenchTask extends DefaultTask {
	/** The folder with block/, item/ and entity/ (it may not exist yet). */
	@Internal
	public abstract DirectoryProperty getSourceDir();

	/** The hand-written resources, so a default blockstate or item model never shadows a real one. */
	@Internal
	public abstract DirectoryProperty getResourcesDir();

	@Input
	public abstract Property<String> getNamespace();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDir();

	@InputFiles
	@PathSensitive(PathSensitivity.RELATIVE)
	public FileTree getProjects() {
		return getSourceDir().getAsFileTree().matching(pattern -> pattern.include("**/*.bbmodel"));
	}

	/** Only the files whose presence changes what's generated. */
	@InputFiles
	@PathSensitive(PathSensitivity.RELATIVE)
	public FileTree getExistingDefaults() {
		String ns = getNamespace().get();
		return getResourcesDir().getAsFileTree().matching(pattern -> pattern
				.include("assets/" + ns + "/blockstates/**", "assets/" + ns + "/models/item/**"));
	}

	@TaskAction
	public void importProjects() throws IOException {
		Path output = getOutputDir().get().getAsFile().toPath();
		clear(output);
		try {
			Set<String> written = BlockbenchImport.run(getSourceDir().get().getAsFile().toPath(), output, getNamespace().get(),
					getResourcesDir().get().getAsFile().toPath(), message -> getLogger().warn("Blockbench: " + message));
			getLogger().info("Blockbench: wrote {} files", written.size());
		} catch (BlockbenchException e) {
			throw new GradleException(e.getMessage());
		}
	}

	private static void clear(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> files = Files.walk(dir)) {
			for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
				if (!path.equals(dir)) {
					Files.delete(path);
				}
			}
		}
	}
}
