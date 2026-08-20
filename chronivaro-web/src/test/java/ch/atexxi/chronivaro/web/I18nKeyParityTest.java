package ch.atexxi.chronivaro.web;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class I18nKeyParityTest {

	private static final Gson gson = new Gson();
	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_-]+)\\}");

	private File getWebappDir() {
		File dir = new File("src/main/webapp");
		if (dir.exists()) return dir;
		dir = new File("chronivaro-web/src/main/webapp");
		if (dir.exists()) return dir;
		dir = new File("../chronivaro-web/src/main/webapp");
		if (dir.exists()) return dir;
		throw new IllegalStateException("Could not locate chronivaro-web/src/main/webapp directory");
	}

	@Test
	public void shouldVerifyI18nBundlesAndKeyParity() throws IOException {
		File webappDir = getWebappDir();
		File deFile = new File(webappDir, "i18n/de.json");
		File enFile = new File(webappDir, "i18n/en.json");

		assertTrue("de.json must exist at " + deFile.getAbsolutePath(), deFile.exists());
		assertTrue("en.json must exist at " + enFile.getAbsolutePath(), enFile.exists());

		String deJson = Files.readString(deFile.toPath());
		String enJson = Files.readString(enFile.toPath());

		assertFalse("de.json must not be empty", deJson.isBlank());
		assertFalse("en.json must not be empty", enJson.isBlank());

		JsonObject deRoot = JsonParser.parseString(deJson).getAsJsonObject();
		JsonObject enRoot = JsonParser.parseString(enJson).getAsJsonObject();

		Map<String, String> deFlatMap = new TreeMap<>();
		Map<String, String> enFlatMap = new TreeMap<>();

		flatten("", deRoot, deFlatMap);
		flatten("", enRoot, enFlatMap);

		assertFalse("DE dictionary must contain keys", deFlatMap.isEmpty());
		assertFalse("EN dictionary must contain keys", enFlatMap.isEmpty());

		// 1. Enforce 100% key parity
		Set<String> deKeys = deFlatMap.keySet();
		Set<String> enKeys = enFlatMap.keySet();

		Set<String> missingInEn = new TreeSet<>(deKeys);
		missingInEn.removeAll(enKeys);

		Set<String> missingInDe = new TreeSet<>(enKeys);
		missingInDe.removeAll(deKeys);

		assertTrue("Keys missing in en.json: " + missingInEn, missingInEn.isEmpty());
		assertTrue("Keys missing in de.json: " + missingInDe, missingInDe.isEmpty());
		assertEquals("Total key count must match", deFlatMap.size(), enFlatMap.size());

		// 2. Validate translation contents
		for (Map.Entry<String, String> entry : deFlatMap.entrySet()) {
			String key = entry.getKey();
			String deVal = entry.getValue();
			String enVal = enFlatMap.get(key);

			assertNotNull("DE translation value must not be null for key: " + key, deVal);
			assertNotNull("EN translation value must not be null for key: " + key, enVal);
			assertFalse("DE translation value must not be blank for key: " + key, deVal.isBlank());
			assertFalse("EN translation value must not be blank for key: " + key, enVal.isBlank());

			// Swiss German rule: Never use 'ß'
			assertFalse("German translation for key '" + key + "' contains forbidden 'ß' character: " + deVal,
					deVal.contains("ß"));

			// Check matching placeholders between DE and EN
			Set<String> dePlaceholders = extractPlaceholders(deVal);
			Set<String> enPlaceholders = extractPlaceholders(enVal);
			assertEquals("Placeholders must match for key '" + key + "'", dePlaceholders, enPlaceholders);
		}
	}

	@Test
	public void shouldVerifyI18nEngineAsset() throws IOException {
		File webappDir = getWebappDir();
		File i18nFile = new File(webappDir, "js/i18n/I18n.js");

		assertTrue("I18n.js must exist at " + i18nFile.getAbsolutePath(), i18nFile.exists());
		String content = Files.readString(i18nFile.toPath());

		assertTrue("Must export I18nEngine class", content.contains("export class I18nEngine"));
		assertTrue("Must export default singleton", content.contains("export default I18n"));
		assertTrue("Must define storage key", content.contains("chronivaro_lang"));
		assertTrue("Must define supported languages", content.contains("SUPPORTED_LANGUAGES"));
		assertTrue("Must implement resolveLanguage", content.contains("resolveLanguage"));
		assertTrue("Must implement loadBundle", content.contains("loadBundle"));
		assertTrue("Must implement t method", content.contains("t(key"));
		assertTrue("Must implement setLanguage", content.contains("setLanguage"));
		assertTrue("Must implement getLanguage", content.contains("getLanguage"));
		assertTrue("Must implement interpolate", content.contains("interpolate"));
	}

	private static void flatten(String prefix, JsonObject obj, Map<String, String> result) {
		for (String key : obj.keySet()) {
			JsonElement element = obj.get(key);
			String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
			if (element.isJsonObject()) {
				flatten(fullKey, element.getAsJsonObject(), result);
			} else if (element.isJsonPrimitive()) {
				result.put(fullKey, element.getAsString());
			} else {
				fail("Unexpected JSON element type at key: " + fullKey);
			}
		}
	}

	private static Set<String> extractPlaceholders(String text) {
		Set<String> placeholders = new TreeSet<>();
		Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
		while (matcher.find()) {
			placeholders.add(matcher.group(1));
		}
		return placeholders;
	}
}
