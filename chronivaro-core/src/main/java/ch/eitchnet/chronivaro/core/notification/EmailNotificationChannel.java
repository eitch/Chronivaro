package ch.eitchnet.chronivaro.core.notification;

import li.strolch.handler.mail.MailHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Objects;

public class EmailNotificationChannel implements NotificationChannel {

	private static final Logger logger = LoggerFactory.getLogger(EmailNotificationChannel.class);
	private final MailHandler mailHandler;

	public EmailNotificationChannel(MailHandler mailHandler) {
		this.mailHandler = Objects.requireNonNull(mailHandler, "mailHandler must be set");
	}

	@Override
	public String recipientKey(Notification notification) {
		return emailRecipientKey(notification);
	}

	static String emailRecipientKey(Notification notification) {
		String email = notification.recipient().destination(NotificationRecipient.EMAIL);
		return email == null || email.isBlank() ? null : email.toLowerCase(Locale.ROOT);
	}

	@Override
	public void send(Notification notification) {
		String email = notification.recipient().destination(NotificationRecipient.EMAIL);
		if (email == null || email.isBlank())
			return;

		if (Notification.ACCOUNT_CHALLENGE.equals(notification.type()))
			this.mailHandler.sendUnencryptedMailAsync(email, notification.title(), notification.message());
		else
			this.mailHandler.sendMailAsync(email, notification.title(), notification.message());
		logger.debug("Dispatched notification (type={}) for user {}", notification.type(), notification.recipient().id());
	}
}