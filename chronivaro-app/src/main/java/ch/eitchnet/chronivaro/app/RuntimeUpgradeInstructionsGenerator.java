package ch.eitchnet.chronivaro.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Analyses git changes in the runtime directory between two git revisions (tags/branches/commits)
 * and generates Markdown upgrade instructions.
 */
public class RuntimeUpgradeInstructionsGenerator {

	public static final String DEFAULT_SOURCE_DIR = "runtime";

	public static void main(String[] args) {
		String fromRev = null;
		String toRev = "HEAD";
		String runtimePath = DEFAULT_SOURCE_DIR;
		String outputFile = null;

		if (args != null) {
			for (int i = 0; i < args.length; i++) {
				String arg = args[i];
				if (("-f".equalsIgnoreCase(arg) || "--from".equalsIgnoreCase(arg) || "--from-tag".equalsIgnoreCase(arg)) && i + 1 < args.length) {
					fromRev = args[++i];
				} else if (("-t".equalsIgnoreCase(arg) || "--to".equalsIgnoreCase(arg) || "--to-tag".equalsIgnoreCase(arg)) && i + 1 < args.length) {
					toRev = args[++i];
				} else if (("-r".equalsIgnoreCase(arg) || "--runtime".equalsIgnoreCase(arg) || "-s".equalsIgnoreCase(arg)) && i + 1 < args.length) {
					runtimePath = args[++i];
				} else if (("-o".equalsIgnoreCase(arg) || "--output".equalsIgnoreCase(arg)) && i + 1 < args.length) {
					outputFile = args[++i];
				} else if ("-h".equalsIgnoreCase(arg) || "--help".equalsIgnoreCase(arg)) {
					printUsage();
					return;
				} else if (fromRev == null && !arg.startsWith("-")) {
					fromRev = arg;
				} else if (toRev.equals("HEAD") && !arg.startsWith("-")) {
					toRev = arg;
				}
			}
		}

		File repoDir = findGitRoot(new File(".").getAbsoluteFile());

		if (fromRev == null || fromRev.isBlank()) {
			fromRev = detectLatestTag(repoDir);
			if (fromRev == null || fromRev.isBlank()) {
				System.err.println("[ERROR] No previous tag found and none specified via -f/--from!");
				printUsage();
				System.exit(1);
			}
		}

		try {
			String instructions = generateInstructions(repoDir, fromRev, toRev, runtimePath);
			if (outputFile != null && !outputFile.isBlank()) {
				File out = new File(outputFile);
				if (out.getParentFile() != null && !out.getParentFile().exists()) {
					out.getParentFile().mkdirs();
				}
				try (FileWriter fw = new FileWriter(out, StandardCharsets.UTF_8)) {
					fw.write(instructions);
				}
			} else {
				System.out.println(instructions);
			}
		} catch (Exception e) {
			System.err.println("[ERROR] Failed to generate upgrade instructions: " + e.getMessage());
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	private static void printUsage() {
		System.out.println("Usage: java " + RuntimeUpgradeInstructionsGenerator.class.getName() + " [options] [fromRev] [toRev]");
		System.out.println("Options:");
		System.out.println("  -f, --from <tag/commit>     Source git revision (e.g. v0.2.0, default: latest tag)");
		System.out.println("  -t, --to <tag/commit>       Target git revision (e.g. HEAD or v0.3.0, default: HEAD)");
		System.out.println("  -r, --runtime <path>        Path to runtime directory in git repo (default: runtime)");
		System.out.println("  -o, --output <file>         Output markdown file (default: stdout)");
		System.out.println("  -h, --help                  Show this help message");
	}

	public static String generateInstructions(File repoDir, String fromRev, String toRev, String runtimePath) throws IOException, InterruptedException {
		Objects.requireNonNull(repoDir, "repoDir must not be null");
		Objects.requireNonNull(fromRev, "fromRev must not be null");
		Objects.requireNonNull(toRev, "toRev must not be null");

		List<String> changedFiles = getChangedRuntimeFiles(repoDir, fromRev, toRev, runtimePath);

		StringBuilder sb = new StringBuilder();
		sb.append("### \uD83D\uDD04 Runtime Upgrade Instructions (").append(fromRev).append(" \u2192 ").append(toRev).append(")\n\n");

		if (changedFiles.isEmpty()) {
			sb.append("No configuration or template changes in the `").append(runtimePath)
					.append("` directory between `").append(fromRev).append("` and `").append(toRev).append("`.\n");
			return sb.toString();
		}

		sb.append("The following files in the `").append(runtimePath)
				.append("` directory have been modified and require review or migration when upgrading an existing instance:\n\n");

		for (String filePath : changedFiles) {
			String summary = describeFileChange(filePath);
			sb.append("- **`").append(filePath).append("`**: ").append(summary).append("\n");
		}

		sb.append("\n#### Runtime Changes Diff\n\n");
		sb.append("Review and apply the relevant diffs to your deployment environment:\n\n");

		for (String filePath : changedFiles) {
			String diff = getFileDiff(repoDir, fromRev, toRev, filePath);
			if (diff.isBlank()) {
				continue;
			}
			sb.append("```diff\n");
			sb.append("# Diff for ").append(filePath).append("\n");
			sb.append(diff.stripTrailing()).append("\n");
			sb.append("```\n\n");
		}

		return sb.toString();
	}

	public static List<String> getChangedRuntimeFiles(File repoDir, String fromRev, String toRev, String runtimePath) throws IOException, InterruptedException {
		String cleanRuntimePath = runtimePath.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
		String pathFilter = cleanRuntimePath.isEmpty() ? "runtime" : cleanRuntimePath;

		List<String> rawOutput = runGitCommand(repoDir, "diff", "--name-only", fromRev + ".." + toRev, "--", pathFilter);
		List<String> filtered = new ArrayList<>();
		for (String line : rawOutput) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			// Ignore dbStore or temporary files if tracked
			if (trimmed.contains("dbStore/") || trimmed.contains("temp/")) {
				continue;
			}
			filtered.add(trimmed);
		}
		Collections.sort(filtered);
		return filtered;
	}

	public static String getFileDiff(File repoDir, String fromRev, String toRev, String filePath) throws IOException, InterruptedException {
		List<String> lines = runGitCommand(repoDir, "diff", "-u", fromRev + ".." + toRev, "--", filePath);
		return String.join("\n", lines);
	}

	public static String describeFileChange(String filePath) {
		String filename = new File(filePath).getName();
		return switch (filename) {
			case "PrivilegeRoles.xml" -> "Role definitions and service/search privilege permissions.";
			case "PrivilegeConfig.xml" -> "Privilege container configuration, encryption handlers, and challenge handlers.";
			case "PrivilegeUsers.xml" -> "Default user definitions and administrative accounts.";
			case "StrolchConfiguration.xml" -> "Strolch agent and component configuration parameters.";
			case "StrolchPolicies.xml" -> "Strolch policy mappings and implementations.";
			case "Model.xml" -> "Default system configuration resources and initial master data.";
			case "Templates.xml" -> "Resource, Order, and Activity model templates and element definitions.";
			default -> "Configuration or data file in the runtime environment.";
		};
	}

	public static File findGitRoot(File startDir) {
		File current = startDir != null ? startDir.getAbsoluteFile() : new File(".").getAbsoluteFile();
		while (current != null) {
			if (new File(current, ".git").exists()) {
				return current;
			}
			current = current.getParentFile();
		}
		return new File(".").getAbsoluteFile();
	}

	public static String detectLatestTag(File repoDir) {
		try {
			List<String> tags = runGitCommand(repoDir, "describe", "--tags", "--abbrev=0");
			if (!tags.isEmpty() && !tags.getFirst().isBlank()) {
				return tags.getFirst().trim();
			}
		} catch (Exception ignored) {
		}

		try {
			List<String> tags = runGitCommand(repoDir, "tag", "--sort=-v:refname");
			if (!tags.isEmpty() && !tags.getFirst().isBlank()) {
				return tags.getFirst().trim();
			}
		} catch (Exception ignored) {
		}

		return null;
	}

	private static List<String> runGitCommand(File repoDir, String... args) throws IOException, InterruptedException {
		List<String> command = new ArrayList<>();
		command.add("git");
		Collections.addAll(command, args);

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(repoDir);
		pb.redirectErrorStream(true);

		Process process = pb.start();
		List<String> output = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				output.add(line);
			}
		}

		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IOException("Git command failed with exit code " + exitCode + ": " + String.join(" ", command)
					+ "\nOutput: " + String.join("\n", output));
		}

		return output;
	}
}
