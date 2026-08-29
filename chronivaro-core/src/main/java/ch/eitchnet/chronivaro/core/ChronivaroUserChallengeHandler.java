package ch.eitchnet.chronivaro.core;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import li.strolch.agent.api.StrolchAgent;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.handler.UserChallengeHandler;
import li.strolch.privilege.model.Usage;
import li.strolch.privilege.model.internal.User;
import li.strolch.privilege.model.internal.UserChallenge;
import li.strolch.utils.SmtpMailer;
import li.strolch.utils.helper.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

public class ChronivaroUserChallengeHandler extends UserChallengeHandler {

	private static final Logger logger = LoggerFactory.getLogger(ChronivaroUserChallengeHandler.class);

	private static volatile StrolchAgent strolchAgent;
	private static volatile ChronivaroUserChallengeHandler instance;

	public ChronivaroUserChallengeHandler() {
		instance = this;
	}

	public static ChronivaroUserChallengeHandler getInstance() {
		return instance;
	}

	public static void setAgent(StrolchAgent agent) {
		strolchAgent = agent;
	}

	public static StrolchAgent getAgent() {
		if (strolchAgent != null)
			return strolchAgent;
		try {
			Class<?> clazz = Class.forName("li.strolch.rest.RestfulStrolchComponent");
			Object comp = clazz.getMethod("getInstance").invoke(null);
			if (comp != null) {
				return (StrolchAgent) clazz.getMethod("getAgent").invoke(comp);
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	@Override
	public void initialize(Map<String, String> parameterMap) {
		super.initialize(parameterMap);
		instance = this;
	}

	public String resolveServerBaseUrl() {
		StrolchAgent agent = getAgent();
		if (agent != null && agent.getContainer() != null && agent.getContainer().getState().isStarted()) {
			try {
				return agent.runAsAgentWithResult(ctx -> {
					try (StrolchTransaction tx = agent.openTx(ctx.getCertificate(), ChronivaroUserChallengeHandler.class, true)) {
						Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", false);
						if (config != null && config.hasParameter(PARAM_SERVER_BASE_URL)) {
							String val = config.getString(PARAM_SERVER_BASE_URL);
							if (val != null && !val.isBlank()) {
								return val.trim();
							}
						}
					}
					return null;
				});
			} catch (Exception e) {
				logger.warn("Could not load serverBaseUrl from GlobalConfiguration: {}", e.getMessage());
			}
		}

		if (getParameterMap() != null && getParameterMap().containsKey(PARAM_SERVER_BASE_URL)) {
			String val = getParameterMap().get(PARAM_SERVER_BASE_URL);
			if (val != null && !val.isBlank()) {
				return val.trim();
			}
		}

		return DEFAULT_SERVER_BASE_URL;
	}

	public String resolveCompanyName() {
		StrolchAgent agent = getAgent();
		if (agent != null && agent.getContainer() != null && agent.getContainer().getState().isStarted()) {
			try {
				return agent.runAsAgentWithResult(ctx -> {
					try (StrolchTransaction tx = agent.openTx(ctx.getCertificate(), ChronivaroUserChallengeHandler.class, true)) {
						Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", false);
						if (config != null && config.hasParameter(PARAM_COMPANY_NAME)) {
							String val = config.getString(PARAM_COMPANY_NAME);
							if (val != null && !val.isBlank()) {
								return val.trim();
							}
						}
					}
					return null;
				});
			} catch (Exception e) {
				logger.warn("Could not load companyName from GlobalConfiguration: {}", e.getMessage());
			}
		}

		if (getParameterMap() != null && getParameterMap().containsKey(PARAM_COMPANY_NAME)) {
			String val = getParameterMap().get(PARAM_COMPANY_NAME);
			if (val != null && !val.isBlank()) {
				return val.trim();
			}
		}

		return DEFAULT_COMPANY_NAME;
	}

	public static String buildRegistrationUrl(String baseUrl, String username, String challenge) {
		String normalizedBase = baseUrl != null ? baseUrl.trim() : DEFAULT_SERVER_BASE_URL;
		while (normalizedBase.endsWith("/")) {
			normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
		}
		String encodedUser = URLEncoder.encode(username, StandardCharsets.UTF_8);
		String encodedToken = URLEncoder.encode(challenge, StandardCharsets.UTF_8);
		return normalizedBase + "/#complete-registration?user=" + encodedUser + "&token=" + encodedToken;
	}

	@Override
	public void sendChallengeToUser(User user, String challenge) {
		String email = StringHelper.trimOrEmpty(user.getEmail());
		if (StringHelper.isEmpty(email)) {
			logger.error("User {} has no or empty email, cannot send challenge!", user.getUsername());
			return;
		}

		try {
			InternetAddress.parse(email);
		} catch (AddressException e) {
			logger.error("Failed to parse email address for user {}: {}", user.getUsername(), email, e);
			throw new IllegalArgumentException("Invalid email address: " + email);
		}

		UserChallenge userChallenge = this.challenges != null ? this.challenges.get(user) : null;
		Usage usage = userChallenge != null ? userChallenge.getUsage() : Usage.SET_PASSWORD;

		String serverBaseUrl = resolveServerBaseUrl();
		String companyName = resolveCompanyName();
		String registrationUrl = buildRegistrationUrl(serverBaseUrl, user.getUsername(), challenge);

		String subject = buildSubject(user, usage, companyName);
		String body = buildBody(user, usage, challenge, companyName, registrationUrl);

		CompletableFuture.runAsync(() -> {
			SmtpMailer.getInstance().sendMailSignedIfAvailable(email, subject, body);
		}).whenComplete((v, t) -> {
			if (t == null) {
				logger.info("Sent challenge email (usage={}) for user {} to {}", usage, user.getUsername(), email);
			} else {
				logger.error("Failed to send challenge email for user {} to {}: {}", user.getUsername(), email, t.getMessage(), t);
			}
		});
	}

	protected String buildSubject(User user, Usage usage, String companyName) {
		Locale locale = user.getLocale() != null ? user.getLocale() : Locale.GERMAN;
		boolean isGerman = locale.getLanguage().equalsIgnoreCase("de");
		if (usage == Usage.SET_PASSWORD) {
			return isGerman ?
					"Willkommen bei " + companyName + " - Ihre Registrierung" :
					"Welcome to " + companyName + " - Complete your registration";
		} else {
			return isGerman ?
					"Sicherheitscode für " + companyName :
					"Security code for " + companyName;
		}
	}

	protected String buildBody(User user, Usage usage, String challenge, String companyName, String registrationUrl) {
		Locale locale = user.getLocale() != null ? user.getLocale() : Locale.GERMAN;
		boolean isGerman = locale.getLanguage().equalsIgnoreCase("de");

		String firstname = user.getFirstname() != null ? user.getFirstname().trim() : "";
		String lastname = user.getLastname() != null ? user.getLastname().trim() : "";
		String fullName = (firstname + " " + lastname).trim();
		if (fullName.isEmpty()) {
			fullName = user.getUsername();
		}

		if (usage == Usage.SET_PASSWORD) {
			if (isGerman) {
				return """
						Hallo %s

						Herzlich willkommen bei %s!

						Für Sie wurde ein Benutzerkonto im Zeiterfassungssystem Chronivaro eingerichtet. Bitte schliessen Sie Ihre Registrierung ab, indem Sie Ihr persönliches Passwort festlegen.

						Klicken Sie auf den folgenden Link, um direkt zur Passwortfestlegung zu gelangen:
						    %s

						Alternativ können Sie die Registrierungsseite manuell aufrufen und die folgenden Daten eingeben:
						    Benutzername: %s
						    Registrierungscode: %s

						Freundliche Grüsse
						Ihr %s-Team
						""".formatted(fullName, companyName, registrationUrl, user.getUsername(), challenge, companyName);
			} else {
				return """
						Hello %s

						Welcome to %s!

						A user account has been created for you in the Chronivaro time tracking system. Please complete your registration by setting your personal password.

						Click the following link to set your password directly:
						    %s

						Alternatively, you can open the registration page manually and enter the following details:
						    Username: %s
						    Registration code: %s

						Best regards,
						Your %s Team
						""".formatted(fullName, companyName, registrationUrl, user.getUsername(), challenge, companyName);
			}
		} else {
			if (isGerman) {
				return """
						Hallo %s

						Sie haben eine Aktion angefordert, die eine Bestätigung erfordert.

						Bitte verwenden Sie den folgenden Sicherheitscode:
						    %s

						Freundliche Grüsse
						Ihr %s-Team
						""".formatted(fullName, challenge, companyName);
			} else {
				return """
						Hello %s

						You have requested an action that requires confirmation.

						Please use the following security code:
						    %s

						Best regards,
						Your %s Team
						""".formatted(fullName, challenge, companyName);
			}
		}
	}
}
