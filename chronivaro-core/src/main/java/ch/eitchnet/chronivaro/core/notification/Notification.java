package ch.eitchnet.chronivaro.core.notification;

import java.util.Objects;

public record Notification(String type, NotificationRecipient recipient, String title, String message) {

	public static final String ACCOUNT_CHALLENGE = "account.challenge";

	public Notification {
		if (type == null || type.isBlank())
			throw new IllegalArgumentException("Notification type must be set");
		Objects.requireNonNull(recipient, "recipient must be set");
		Objects.requireNonNull(title, "title must be set");
		Objects.requireNonNull(message, "message must be set");
	}
}