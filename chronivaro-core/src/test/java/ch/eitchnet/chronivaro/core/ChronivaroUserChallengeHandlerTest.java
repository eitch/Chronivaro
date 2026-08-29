package ch.eitchnet.chronivaro.core;

import li.strolch.privilege.model.Usage;
import li.strolch.privilege.model.internal.User;
import li.strolch.privilege.model.internal.UserHistory;
import org.junit.Test;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChronivaroUserChallengeHandlerTest {

	@Test
	public void shouldBuildRegistrationUrl() {
		String url1 = ChronivaroUserChallengeHandler.buildRegistrationUrl("http://localhost:8080", "john.doe", "token123");
		assertEquals("http://localhost:8080/#complete-registration?user=john.doe&token=token123", url1);

		String url2 = ChronivaroUserChallengeHandler.buildRegistrationUrl("https://chronivaro.example.com/", "jane doe", "abc!def");
		assertEquals("https://chronivaro.example.com/#complete-registration?user=jane+doe&token=abc%21def", url2);
	}

	@Test
	public void shouldFormatGermanOnboardingEmail() {
		ChronivaroUserChallengeHandler handler = new ChronivaroUserChallengeHandler();
		handler.initialize(Map.of("serverBaseUrl", "https://app.chronivaro.ch", "companyName", "Eitchnet"));

		User user = new User("1", "jdoe", null, "John", "Doe", li.strolch.privilege.model.UserState.REMOTE,
				Set.of("Employee"), Set.of(), Locale.GERMAN, Map.of(), false, UserHistory.EMPTY);

		String subject = handler.buildSubject(user, Usage.SET_PASSWORD, "Eitchnet");
		assertEquals("Willkommen bei Eitchnet - Ihre Registrierung", subject);

		String registrationUrl = ChronivaroUserChallengeHandler.buildRegistrationUrl("https://app.chronivaro.ch", user.getUsername(), "CHALLENGE_CODE_123");
		String body = handler.buildBody(user, Usage.SET_PASSWORD, "CHALLENGE_CODE_123", "Eitchnet", registrationUrl);

		assertTrue(body.contains("Hallo John Doe"));
		assertTrue(body.contains("Herzlich willkommen bei Eitchnet!"));
		assertTrue(body.contains("https://app.chronivaro.ch/#complete-registration?user=jdoe&token=CHALLENGE_CODE_123"));
		assertTrue(body.contains("CHALLENGE_CODE_123"));
		assertTrue(body.contains("Ihr Eitchnet-Team"));
	}

	@Test
	public void shouldFormatEnglishOnboardingEmail() {
		ChronivaroUserChallengeHandler handler = new ChronivaroUserChallengeHandler();
		handler.initialize(Map.of("serverBaseUrl", "https://app.chronivaro.com", "companyName", "Chronivaro Corp"));

		User user = new User("2", "alice", null, "Alice", "Smith", li.strolch.privilege.model.UserState.REMOTE,
				Set.of("Employee"), Set.of(), Locale.ENGLISH, Map.of(), false, UserHistory.EMPTY);

		String subject = handler.buildSubject(user, Usage.SET_PASSWORD, "Chronivaro Corp");
		assertEquals("Welcome to Chronivaro Corp - Complete your registration", subject);

		String registrationUrl = ChronivaroUserChallengeHandler.buildRegistrationUrl("https://app.chronivaro.com", user.getUsername(), "XYZ_789");
		String body = handler.buildBody(user, Usage.SET_PASSWORD, "XYZ_789", "Chronivaro Corp", registrationUrl);

		assertTrue(body.contains("Hello Alice Smith"));
		assertTrue(body.contains("Welcome to Chronivaro Corp!"));
		assertTrue(body.contains("https://app.chronivaro.com/#complete-registration?user=alice&token=XYZ_789"));
		assertTrue(body.contains("XYZ_789"));
		assertTrue(body.contains("Your Chronivaro Corp Team"));
	}
}
