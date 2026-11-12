package ch.eitchnet.chronivaro.core.notification;

import java.util.Map;
import java.util.Objects;

public record NotificationRecipient(String id, Map<String, String> destinations) {

	public static final String EMAIL = "email";

	public NotificationRecipient {
		if (id == null || id.isBlank())
			throw new IllegalArgumentException("Recipient id must be set");
		Objects.requireNonNull(destinations, "destinations must be set");
		destinations = Map.copyOf(destinations);
	}

	public String destination(String channel) {
		return destinations.get(channel);
	}
}