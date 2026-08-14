package ch.atexxi.chronivaro.core;

import li.strolch.privilege.handler.ConsoleUserChallengeHandler;
import li.strolch.privilege.model.internal.User;
import li.strolch.privilege.model.internal.UserChallenge;

import java.util.Map;

public class TestUserChallengeHandler extends ConsoleUserChallengeHandler {

	private static TestUserChallengeHandler instance;

	public TestUserChallengeHandler() {
		instance = this;
	}

	public Map<User, UserChallenge> getChallenges() {
		return this.challenges;
	}

	public static TestUserChallengeHandler getInstance() {
		return instance;
	}
}
