package ch.eitchnet.chronivaro.core.notification;

import li.strolch.agent.api.StrolchAgent;
import li.strolch.handler.mail.MailHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

public class NotificationDispatcher {

	private static final Logger logger = LoggerFactory.getLogger(NotificationDispatcher.class);

	private final List<NotificationChannel> channels;

	public NotificationDispatcher(List<NotificationChannel> channels) {
		this.channels = List.copyOf(channels);
	}

	public static NotificationDispatcher createDefault(StrolchAgent agent) {
		List<NotificationChannel> channels = new ArrayList<>();
		try {
			if (agent != null) {
				agent.getComponentO(MailHandler.class).ifPresentOrElse(
						mailHandler -> channels.add(new EmailNotificationChannel(mailHandler)),
						() -> logger.warn("No MailHandler is configured; email notifications are disabled"));
			} else {
				logger.warn("No Strolch agent is available; email notifications are disabled");
			}
		} catch (RuntimeException e) {
			logger.warn("Could not initialize the email notification channel: {}", e.getMessage(), e);
		}
		try {
			for (NotificationChannel channel : ServiceLoader.load(NotificationChannel.class)) {
				if (channels.stream().noneMatch(existing -> existing.getClass().equals(channel.getClass())))
					channels.add(channel);
			}
		} catch (ServiceConfigurationError | RuntimeException e) {
			logger.warn("Could not load a notification channel: {}", e.getMessage(), e);
		}
		return new NotificationDispatcher(channels);
	}

	public void dispatch(Notification notification) {
		dispatch(List.of(notification));
	}

	public void dispatch(List<Notification> notifications) {
		for (NotificationChannel channel : this.channels) {
			Set<DeliveryKey> delivered = new HashSet<>();
			for (Notification notification : notifications) {
				try {
					String key = channel.recipientKey(notification);
					if (key == null || !delivered.add(new DeliveryKey(notification.type(), notification.title(),
							notification.message(), key)))
						continue;
					channel.send(notification);
				} catch (RuntimeException e) {
					logger.warn("Notification channel {} failed for type {}: {}", channel.getClass().getName(),
							notification.type(), e.getMessage(), e);
				}
			}
		}
	}

	private record DeliveryKey(String type, String title, String message, String recipientKey) {
	}
}