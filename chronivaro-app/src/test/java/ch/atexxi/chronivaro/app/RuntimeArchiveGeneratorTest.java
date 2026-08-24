package ch.atexxi.chronivaro.app;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.*;

public class RuntimeArchiveGeneratorTest {

	private Path tempDir;

	@Before
	public void setUp() throws Exception {
		this.tempDir = Files.createTempDirectory("runtime-archive-test-");
	}

	@After
	public void tearDown() throws Exception {
		if (this.tempDir != null && Files.exists(this.tempDir)) {
			try (var stream = Files.walk(this.tempDir)) {
				stream.sorted(Comparator.reverseOrder()).forEach(p -> {
					try {
						Files.deleteIfExists(p);
					} catch (Exception ignored) {
					}
				});
			}
		}
	}

	@Test
	public void shouldFilterPrivilegeUsersXml() throws Exception {
		String testXml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<Users>
				    <User userId="agent" username="agent">
				        <Firstname>Strolch Agent</Firstname>
				        <Lastname>System User</Lastname>
				        <State>SYSTEM</State>
				        <Roles>
				            <Role>agent</Role>
				        </Roles>
				    </User>
				    <User userId="system_backup" username="sysbackup">
				        <Firstname>Backup</Firstname>
				        <Lastname>Service</Lastname>
				        <State>system</State>
				        <Roles>
				            <Role>agent</Role>
				        </Roles>
				    </User>
				    <User userId="1" username="admin">
				        <Firstname>Admin</Firstname>
				        <Lastname>Admin</Lastname>
				        <State>ENABLED</State>
				        <Roles>
				            <Role>Administrator</Role>
				        </Roles>
				    </User>
				    <User userId="666954056572" username="bob">
				        <Firstname>Bob</Firstname>
				        <Lastname>Someone</Lastname>
				        <State>ENABLED</State>
				        <Roles>
				            <Role>Employee</Role>
				        </Roles>
				    </User>
				    <User userId="666872618952" username="jill">
				        <Firstname>Jill</Firstname>
				        <Lastname>Someone</Lastname>
				        <State>ENABLED</State>
				        <Roles>
				            <Role>Employee</Role>
				        </Roles>
				    </User>
				</Users>
				""";

		byte[] filteredBytes = RuntimeArchiveGenerator.filterPrivilegeUsersXml(
				new ByteArrayInputStream(testXml.getBytes(StandardCharsets.UTF_8)));
		String filteredXml = new String(filteredBytes, StandardCharsets.UTF_8);

		assertTrue("Filtered XML must contain agent", filteredXml.contains("username=\"agent\""));
		assertTrue("Filtered XML must contain admin", filteredXml.contains("username=\"admin\""));
		assertTrue("Filtered XML must contain sysbackup", filteredXml.contains("username=\"sysbackup\""));
		assertFalse("Filtered XML must NOT contain bob", filteredXml.contains("username=\"bob\""));
		assertFalse("Filtered XML must NOT contain jill", filteredXml.contains("username=\"jill\""));
	}

	@Test
	public void shouldGenerateArchiveExcludingTempAndDbStore() throws Exception {
		Path runtimeSource = this.tempDir.resolve("runtime");
		Files.createDirectories(runtimeSource.resolve("config"));
		Files.createDirectories(runtimeSource.resolve("data/dbStore/Resource"));
		Files.createDirectories(runtimeSource.resolve("temp"));

		// Config files
		Files.writeString(runtimeSource.resolve("config/PrivilegeConfig.xml"), "<Privilege/>");
		Files.writeString(runtimeSource.resolve("config/StrolchConfiguration.xml"), "<StrolchConfiguration/>");
		Files.writeString(runtimeSource.resolve("config/PrivilegeUsers.xml"), """
				<?xml version="1.0" encoding="UTF-8"?>
				<Users>
				    <User userId="agent" username="agent">
				        <Firstname>Agent</Firstname>
				        <State>SYSTEM</State>
				    </User>
				    <User userId="1" username="admin">
				        <Firstname>Admin</Firstname>
				        <State>ENABLED</State>
				    </User>
				    <User userId="123" username="regular_user">
				        <Firstname>Regular</Firstname>
				        <State>ENABLED</State>
				    </User>
				</Users>
				""");

		// Data files
		Files.writeString(runtimeSource.resolve("data/Model.xml"), "<Model/>");
		Files.writeString(runtimeSource.resolve("data/Templates.xml"), "<Templates/>");

		// Excluded files
		Files.writeString(runtimeSource.resolve("data/dbStore/Resource/test.xml"), "<Resource/>");
		Files.writeString(runtimeSource.resolve("temp/sessions.dat"), "session-data");

		File outputFile = this.tempDir.resolve("runtime.tar.gz").toFile();
		RuntimeArchiveGenerator.generateArchive(runtimeSource.toFile(), outputFile);

		assertTrue("Archive file should exist", outputFile.exists());
		assertTrue("Archive file size should be > 0", outputFile.length() > 0);

		List<String> entryNames = new ArrayList<>();
		String extractedUsersXml = null;

		try (InputStream fis = new FileInputStream(outputFile);
			 GzipCompressorInputStream gzis = new GzipCompressorInputStream(fis);
			 TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {

			TarArchiveEntry entry;
			while ((entry = tais.getNextEntry()) != null) {
				entryNames.add(entry.getName());
				if (entry.getName().equals("runtime/config/PrivilegeUsers.xml")) {
					byte[] content = tais.readAllBytes();
					extractedUsersXml = new String(content, StandardCharsets.UTF_8);
				}
			}
		}

		// Verify included files
		assertTrue("Should contain runtime/", entryNames.contains("runtime/"));
		assertTrue("Should contain runtime/temp/", entryNames.contains("runtime/temp/"));
		assertTrue("Should contain runtime/config/PrivilegeConfig.xml", entryNames.contains("runtime/config/PrivilegeConfig.xml"));
		assertTrue("Should contain runtime/config/StrolchConfiguration.xml", entryNames.contains("runtime/config/StrolchConfiguration.xml"));
		assertTrue("Should contain runtime/config/PrivilegeUsers.xml", entryNames.contains("runtime/config/PrivilegeUsers.xml"));
		assertTrue("Should contain runtime/data/Model.xml", entryNames.contains("runtime/data/Model.xml"));
		assertTrue("Should contain runtime/data/Templates.xml", entryNames.contains("runtime/data/Templates.xml"));

		// Verify excluded files
		for (String name : entryNames) {
			if (name.startsWith("runtime/temp")) {
				assertEquals("Only runtime/temp/ directory entry should exist for temp", "runtime/temp/", name);
			}
			assertFalse("Must not contain files in dbStore: " + name, name.startsWith("runtime/data/dbStore"));
		}

		// Verify PrivilegeUsers content inside archive
		assertNotNull("Extracted PrivilegeUsers.xml should not be null", extractedUsersXml);
		assertTrue("Should contain agent", extractedUsersXml.contains("username=\"agent\""));
		assertTrue("Should contain admin", extractedUsersXml.contains("username=\"admin\""));
		assertFalse("Should NOT contain regular_user", extractedUsersXml.contains("username=\"regular_user\""));

		// Verify extraction
		Path extractDir = this.tempDir.resolve("extracted");
		Files.createDirectories(extractDir);
		try (InputStream fis = new FileInputStream(outputFile);
			 GzipCompressorInputStream gzis = new GzipCompressorInputStream(fis);
			 TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {

			TarArchiveEntry entry;
			while ((entry = tais.getNextEntry()) != null) {
				Path dest = extractDir.resolve(entry.getName());
				if (entry.isDirectory()) {
					Files.createDirectories(dest);
				} else {
					Files.createDirectories(dest.getParent());
					Files.write(dest, tais.readAllBytes());
				}
			}
		}

		assertTrue("Temp directory must exist after extraction", Files.isDirectory(extractDir.resolve("runtime/temp")));
		try (var stream = Files.list(extractDir.resolve("runtime/temp"))) {
			assertEquals("Temp directory must be empty after extraction", 0, stream.count());
		}
	}

	@Test
	public void shouldGenerateArchiveFromProjectRuntime() throws Exception {
		File projectRuntime = new File("runtime");
		if (!projectRuntime.exists()) {
			projectRuntime = new File("../runtime");
		}
		assertTrue("Project runtime directory should exist", projectRuntime.exists());

		File outputFile = this.tempDir.resolve("runtime.tar.gz").toFile();
		RuntimeArchiveGenerator.generateArchive(projectRuntime, outputFile);

		assertTrue(outputFile.exists());
		assertTrue(outputFile.length() > 0);

		List<String> entryNames = new ArrayList<>();
		try (InputStream fis = new FileInputStream(outputFile);
			 GzipCompressorInputStream gzis = new GzipCompressorInputStream(fis);
			 TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {

			TarArchiveEntry entry;
			while ((entry = tais.getNextEntry()) != null) {
				entryNames.add(entry.getName());
			}
		}

		assertTrue(entryNames.contains("runtime/"));
		assertTrue(entryNames.contains("runtime/temp/"));
		assertTrue(entryNames.contains("runtime/config/PrivilegeUsers.xml"));
		assertTrue(entryNames.contains("runtime/config/PrivilegeConfig.xml"));
		assertTrue(entryNames.contains("runtime/config/PrivilegeRoles.xml"));
		assertTrue(entryNames.contains("runtime/config/StrolchConfiguration.xml"));
		assertTrue(entryNames.contains("runtime/data/Model.xml"));
		assertTrue(entryNames.contains("runtime/data/Templates.xml"));

		for (String name : entryNames) {
			if (name.startsWith("runtime/temp")) {
				assertEquals("Only runtime/temp/ directory entry should exist for temp", "runtime/temp/", name);
			}
			assertFalse("Must not contain dbStore: " + name, name.startsWith("runtime/data/dbStore"));
		}
	}

	@Test
	public void shouldCreateTempDirEvenIfMissingInSource() throws Exception {
		Path runtimeSource = this.tempDir.resolve("runtime-no-temp");
		Files.createDirectories(runtimeSource.resolve("config"));
		Files.writeString(runtimeSource.resolve("config/PrivilegeConfig.xml"), "<Privilege/>");

		File outputFile = this.tempDir.resolve("runtime-no-temp.tar.gz").toFile();
		RuntimeArchiveGenerator.generateArchive(runtimeSource.toFile(), outputFile);

		List<String> entryNames = new ArrayList<>();
		try (InputStream fis = new FileInputStream(outputFile);
			 GzipCompressorInputStream gzis = new GzipCompressorInputStream(fis);
			 TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {

			TarArchiveEntry entry;
			while ((entry = tais.getNextEntry()) != null) {
				entryNames.add(entry.getName());
			}
		}

		assertTrue("Archive must contain runtime/temp/ even when missing in source", entryNames.contains("runtime/temp/"));
	}
}
