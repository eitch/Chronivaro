package ch.eitchnet.chronivaro.app;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class RuntimeArchiveGenerator {

	private static final Logger logger = LoggerFactory.getLogger(RuntimeArchiveGenerator.class);

	public static final String DEFAULT_SOURCE_DIR = "runtime";
	public static final String DEFAULT_OUTPUT_FILE = "runtime.tar.gz";
	public static final String PRIVILEGE_USERS_FILENAME = "PrivilegeUsers.xml";
	public static final String MODEL_XML_FILENAME = "Model.xml";
	public static final String ADMIN_USERNAME = "admin";
	public static final String SYSTEM_STATE = "SYSTEM";

	public static void main(String[] args) {
		String sourceDirPath = DEFAULT_SOURCE_DIR;
		String outputFilePath = DEFAULT_OUTPUT_FILE;

		if (args != null) {
			for (int i = 0; i < args.length; i++) {
				String arg = args[i];
				if (("-s".equalsIgnoreCase(arg) || "--source".equalsIgnoreCase(arg)) && i + 1 < args.length) {
					sourceDirPath = args[++i];
				} else if (("-o".equalsIgnoreCase(arg) || "--output".equalsIgnoreCase(arg) || "-t".equalsIgnoreCase(arg) || "--target".equalsIgnoreCase(arg))
						&& i + 1 < args.length) {
					outputFilePath = args[++i];
				} else if ("-h".equalsIgnoreCase(arg) || "--help".equalsIgnoreCase(arg)) {
					printUsage();
					return;
				}
			}
		}

		File sourceDir = new File(sourceDirPath);
		if (!sourceDir.exists()) {
			File parentSourceDir = new File("../" + sourceDirPath);
			if (parentSourceDir.exists()) {
				sourceDir = parentSourceDir;
			}
		}

		File outputFile = new File(outputFilePath);

		try {
			generateArchive(sourceDir, outputFile);
			logger.info("Successfully generated runtime archive at: {}", outputFile.getAbsolutePath());
		} catch (Exception e) {
			logger.error("Failed to generate runtime archive: {}", e.getMessage(), e);
			System.exit(1);
		}
	}

	private static void printUsage() {
		System.out.println("Usage: java " + RuntimeArchiveGenerator.class.getName() + " [options]");
		System.out.println("Options:");
		System.out.println("  -s, --source <dir>   Path to source runtime directory (default: runtime)");
		System.out.println("  -o, --output <file>  Path to output tar.gz file (default: runtime.tar.gz)");
		System.out.println("  -h, --help           Display this help message");
	}

	public static void generateArchive(File sourceRuntimeDir, File outputTarGzFile) throws Exception {
		Objects.requireNonNull(sourceRuntimeDir, "sourceRuntimeDir must not be null");
		Objects.requireNonNull(outputTarGzFile, "outputTarGzFile must not be null");

		if (!sourceRuntimeDir.exists() || !sourceRuntimeDir.isDirectory()) {
			throw new IllegalArgumentException("Source runtime directory does not exist or is not a directory: " + sourceRuntimeDir.getAbsolutePath());
		}

		File parentDir = outputTarGzFile.getParentFile();
		if (parentDir != null && !parentDir.exists()) {
			if (!parentDir.mkdirs()) {
				throw new IllegalStateException("Failed to create parent directories for output file: " + parentDir.getAbsolutePath());
			}
		}

		logger.info("Generating runtime tarball from {} to {}...", sourceRuntimeDir.getAbsolutePath(), outputTarGzFile.getAbsolutePath());

		Path rootPath = sourceRuntimeDir.toPath().toAbsolutePath().normalize();

		try (FileOutputStream fos = new FileOutputStream(outputTarGzFile);
			 BufferedOutputStream bos = new BufferedOutputStream(fos);
			 GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(bos);
			 TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzos)) {

			tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
			tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

			// Collect all valid files and directories
			List<Path> pathsToInclude = new ArrayList<>();
			try (Stream<Path> stream = Files.walk(rootPath)) {
				stream.forEach(path -> {
					Path relPath = rootPath.relativize(path);
					String relPathStr = relPath.toString().replace('\\', '/');

					if (shouldExclude(relPathStr)) {
						return;
					}
					pathsToInclude.add(path);
				});
			}

			// Sort to ensure deterministic order (directories first, then alphabetical)
			pathsToInclude.sort(Comparator.comparing((Path p) -> rootPath.relativize(p).toString().replace('\\', '/')));

			boolean hasTempDir = false;
			for (Path path : pathsToInclude) {
				Path relPath = rootPath.relativize(path);
				String relPathStr = relPath.toString().replace('\\', '/');

				if (relPathStr.equals("temp")) {
					hasTempDir = true;
				}

				String entryName = relPathStr.isEmpty() ? "runtime/" : "runtime/" + relPathStr;
				if (Files.isDirectory(path)) {
					if (!entryName.endsWith("/")) {
						entryName += "/";
					}
					TarArchiveEntry entry = new TarArchiveEntry(entryName);
					entry.setModTime(Files.getLastModifiedTime(path).toMillis());
					entry.setMode(0755);
					tarOut.putArchiveEntry(entry);
					tarOut.closeArchiveEntry();
				} else if (Files.isRegularFile(path)) {
					byte[] fileBytes;
					if (path.getFileName().toString().equals(PRIVILEGE_USERS_FILENAME)) {
						logger.info("Filtering {} to keep only SYSTEM state and admin users...", relPathStr);
						fileBytes = filterPrivilegeUsersXml(path.toFile());
					} else if (path.getFileName().toString().equals(MODEL_XML_FILENAME)) {
						logger.info("Filtering {} to remove StrolchJob resources...", relPathStr);
						fileBytes = filterModelXml(path.toFile());
					} else {
						fileBytes = Files.readAllBytes(path);
					}

					TarArchiveEntry entry = new TarArchiveEntry(entryName);
					entry.setSize(fileBytes.length);
					entry.setModTime(Files.getLastModifiedTime(path).toMillis());
					entry.setMode(0644);
					tarOut.putArchiveEntry(entry);
					tarOut.write(fileBytes);
					tarOut.closeArchiveEntry();
				}
			}

			if (!hasTempDir) {
				TarArchiveEntry tempEntry = new TarArchiveEntry("runtime/temp/");
				tempEntry.setModTime(System.currentTimeMillis());
				tempEntry.setMode(0755);
				tarOut.putArchiveEntry(tempEntry);
				tarOut.closeArchiveEntry();
			}

			tarOut.finish();
		}

		logger.info("Runtime archive successfully created: {} ({} bytes)", outputTarGzFile.getAbsolutePath(), outputTarGzFile.length());
	}

	public static boolean shouldExclude(String relativePath) {
		if (relativePath == null || relativePath.isEmpty()) {
			return false;
		}

		String normalized = relativePath.replace('\\', '/');
		if (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}

		// Exclude contents of temp directory, but allow the temp directory itself
		if (normalized.startsWith("temp/")) {
			return true;
		}

		// Exclude data/dbStore directory and its contents
		if (normalized.equals("data/dbStore") || normalized.startsWith("data/dbStore/")) {
			return true;
		}

		return false;
	}

	public static byte[] filterModelXml(File xmlFile) throws Exception {
		try (InputStream in = new FileInputStream(xmlFile)) {
			return filterModelXml(in);
		}
	}

	public static byte[] filterModelXml(InputStream xmlInputStream) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(false);
		dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.parse(xmlInputStream);

		Element root = doc.getDocumentElement();
		if (!"StrolchModel".equalsIgnoreCase(root.getTagName()) && !"Model".equalsIgnoreCase(root.getTagName())) {
			throw new IllegalArgumentException("Root element must be <StrolchModel> or <Model> but was: <" + root.getTagName() + ">");
		}

		NodeList resourceNodes = root.getElementsByTagName("Resource");
		List<Node> toRemove = new ArrayList<>();
		for (int i = 0; i < resourceNodes.getLength(); i++) {
			Element resElem = (Element) resourceNodes.item(i);
			String type = resElem.getAttribute("Type");
			if ("StrolchJob".equalsIgnoreCase(type)) {
				toRemove.add(resElem);
			}
		}

		for (Node node : toRemove) {
			root.removeChild(node);
		}

		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		transformer.transform(new DOMSource(doc), new StreamResult(baos));
		return baos.toByteArray();
	}

	public static byte[] filterPrivilegeUsersXml(File xmlFile) throws Exception {
		try (InputStream in = new FileInputStream(xmlFile)) {
			return filterPrivilegeUsersXml(in);
		}
	}

	public static byte[] filterPrivilegeUsersXml(InputStream xmlInputStream) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(false);
		dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.parse(xmlInputStream);

		Element root = doc.getDocumentElement();
		if (!"Users".equals(root.getTagName())) {
			throw new IllegalArgumentException("Root element must be <Users> but was: <" + root.getTagName() + ">");
		}

		NodeList userNodes = root.getElementsByTagName("User");
		List<Node> nodesToRemove = new ArrayList<>();

		for (int i = 0; i < userNodes.getLength(); i++) {
			Node userNode = userNodes.item(i);
			if (userNode.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}

			Element userElem = (Element) userNode;
			String username = userElem.getAttribute("username");
			String userId = userElem.getAttribute("userId");

			String state = null;
			NodeList stateNodes = userElem.getElementsByTagName("State");
			if (stateNodes.getLength() > 0) {
				state = stateNodes.item(0).getTextContent();
			}

			boolean isAdmin = (username != null && username.equalsIgnoreCase(ADMIN_USERNAME)) ||
					(userId != null && userId.equalsIgnoreCase(ADMIN_USERNAME));
			boolean isSystem = state != null && state.trim().equalsIgnoreCase(SYSTEM_STATE);

			if (!isAdmin && !isSystem) {
				nodesToRemove.add(userNode);
			}
		}

		for (Node node : nodesToRemove) {
			root.removeChild(node);
		}

		// Clean up empty whitespace text nodes between removed elements
		cleanWhitespaceNodes(root);

		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		transformer.transform(new DOMSource(doc), new StreamResult(baos));
		return baos.toByteArray();
	}

	private static void cleanWhitespaceNodes(Node node) {
		NodeList children = node.getChildNodes();
		List<Node> toRemove = new ArrayList<>();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().trim().isEmpty()) {
				// Only remove consecutive text nodes or leading/trailing text nodes around removed elements
				toRemove.add(child);
			}
		}
		for (Node textNode : toRemove) {
			node.removeChild(textNode);
		}
	}
}
