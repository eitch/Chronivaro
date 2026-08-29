package ch.eitchnet.chronivaro.core;

import li.strolch.privilege.model.Usage;
import li.strolch.privilege.model.internal.User;
import li.strolch.privilege.model.internal.UserChallenge;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class TestUserChallengeHandler extends ChronivaroUserChallengeHandler {

	public record SentEmail(String recipient, String subject, String body, User user, String challenge, Usage usage) {
	}

	private static TestUserChallengeHandler instance;
	private final List<SentEmail> sentEmails = new CopyOnWriteArrayList<>();

	public TestUserChallengeHandler() {
		instance = this;
	}

	@Override
	public void sendChallengeToUser(User user, String challenge) {
		UserChallenge userChallenge = this.challenges != null ? this.challenges.get(user) : null;
		Usage usage = userChallenge != null ? userChallenge.getUsage() : Usage.SET_PASSWORD;
		String serverBaseUrl = resolveServerBaseUrl();
		String companyName = resolveCompanyName();
		String registrationUrl = buildRegistrationUrl(serverBaseUrl, user.getUsername(), challenge);
		String subject = buildSubject(user, usage, companyName);
		String body = buildBody(user, usage, challenge, companyName, registrationUrl);

		this.sentEmails.add(new SentEmail(user.getEmail(), subject, body, user, challenge, usage));
		super.sendChallengeToUser(user, challenge);
	}

	public List<SentEmail> getSentEmails() {
		return this.sentEmails;
	}

	public void clearSentEmails() {
		this.sentEmails.clear();
	}

	public Map<User, UserChallenge> getChallenges() {
		return this.challenges;
	}

	public static TestUserChallengeHandler getInstance() {
		return instance;
	}
}
