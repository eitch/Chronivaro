package ch.eitchnet.chronivaro.core.notification;

import li.strolch.agent.api.ComponentContainer;
import li.strolch.handler.mail.MailHandler;
import li.strolch.handler.mail.SimulatedMailHandler;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class NotificationDispatcherTest {

	@Test
	public void shouldContinueDeliveryWhenAChannelFails() {
		Notification notification = new Notification("test.event",
				new NotificationRecipient("user-1", Map.of("push", "device-1")), "Title", "Message");
		List<Notification> delivered = new ArrayList<>();
		NotificationChannel failingChannel = ignored -> {
			throw new IllegalStateException("channel unavailable");
		};
		NotificationChannel recordingChannel = delivered::add;

		new NotificationDispatcher(List.of(failingChannel, recordingChannel)).dispatch(notification);

		assertEquals(List.of(notification), delivered);
		assertEquals("device-1", notification.recipient().destination("push"));
	}

	@Test
	public void shouldDeduplicateAndFilterPerChannel() {
		Notification first = new Notification("test.event", new NotificationRecipient("user-1",
				Map.of(NotificationRecipient.EMAIL, "shared@example.ch")), "Title", "Message");
		Notification second = new Notification("test.event", new NotificationRecipient("user-2",
				Map.of(NotificationRecipient.EMAIL, "SHARED@example.ch")), "Title", "Message");
		Notification third = new Notification("test.event", new NotificationRecipient("user-3", Map.of()), "Title",
				"Message");
		List<Notification> emailDelivered = new ArrayList<>();
		List<Notification> otherDelivered = new ArrayList<>();
		NotificationChannel emailChannel = new NotificationChannel() {
			@Override
			public String recipientKey(Notification notification) {
				return EmailNotificationChannel.emailRecipientKey(notification);
			}

			@Override
			public void send(Notification notification) {
				emailDelivered.add(notification);
			}
		};

		new NotificationDispatcher(List.of(emailChannel, otherDelivered::add)).dispatch(List.of(first, second, third));

		assertEquals(List.of(first), emailDelivered);
		assertEquals(List.of(first, second, third), otherDelivered);
	}

	@Test
	public void shouldDelegateMailAndPreserveUnencryptedChallengeDelivery() {
		RuntimeMock runtimeMock = new RuntimeMock().mockRuntime(
				"target/" + getClass().getSimpleName() + "-" + System.nanoTime(), "src/test/resources");
		try {
			runtimeMock.startContainer();
			RecordingMailHandler mailHandler = (RecordingMailHandler) runtimeMock.getContainer()
					.getComponent(MailHandler.class);
			EmailNotificationChannel channel = new EmailNotificationChannel(mailHandler);
			NotificationRecipient recipient = new NotificationRecipient("user-1",
					Map.of(NotificationRecipient.EMAIL, "user@example.ch"));

			channel.send(new Notification("period.approved", recipient, "Period", "Approved"));
			channel.send(new Notification(Notification.ACCOUNT_CHALLENGE, recipient, "Challenge", "Token"));

			assertEquals(List.of("mail:user@example.ch:Period:Approved", "unencrypted:user@example.ch:Challenge:Token"),
					mailHandler.deliveries);
		} finally {
			runtimeMock.destroyRuntime();
		}
	}

	public static class RecordingMailHandler extends SimulatedMailHandler {
		private final List<String> deliveries = new ArrayList<>();

		public RecordingMailHandler(ComponentContainer container, String name) {
			super(container, name);
		}

		@Override
		public void sendMailAsync(String recipients, String subject, String text) {
			this.deliveries.add("mail:" + recipients + ":" + subject + ":" + text);
		}

		@Override
		public void sendUnencryptedMailAsync(String recipients, String subject, String text) {
			this.deliveries.add("unencrypted:" + recipients + ":" + subject + ":" + text);
		}
	}
}