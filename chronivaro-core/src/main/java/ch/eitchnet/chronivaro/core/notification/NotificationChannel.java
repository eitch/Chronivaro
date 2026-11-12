package ch.eitchnet.chronivaro.core.notification;

public interface NotificationChannel {

	/** Returns a channel-specific deduplication key, or null when this recipient cannot be reached. */
	default String recipientKey(Notification notification) {
		return notification.recipient().id();
	}

	void send(Notification notification);
}