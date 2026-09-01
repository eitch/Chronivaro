package ch.eitchnet.chronivaro.app;

import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RuntimeUpgradeInstructionsGeneratorTest {

	@Test
	public void testGenerateInstructions() throws Exception {
		File repoDir = RuntimeUpgradeInstructionsGenerator.findGitRoot(new File("."));

		String instructions = RuntimeUpgradeInstructionsGenerator.generateInstructions(repoDir, "v0.1.0", "v0.2.0", "runtime");
		assertNotNull(instructions);
		assertTrue(instructions.contains("Runtime Upgrade Instructions (v0.1.0 → v0.2.0)"));
		assertTrue(instructions.contains("PrivilegeRoles.xml"));
		assertTrue(instructions.contains("Model.xml"));
		assertTrue(instructions.contains("Templates.xml"));
	}

	@Test
	public void testGetChangedRuntimeFiles() throws Exception {
		File repoDir = RuntimeUpgradeInstructionsGenerator.findGitRoot(new File("."));

		List<String> changedFiles = RuntimeUpgradeInstructionsGenerator.getChangedRuntimeFiles(repoDir, "v0.2.0", "HEAD", "runtime");
		assertNotNull(changedFiles);
		assertFalse(changedFiles.isEmpty());
		assertTrue(changedFiles.stream().anyMatch(f -> f.endsWith("PrivilegeRoles.xml")));
	}
}
