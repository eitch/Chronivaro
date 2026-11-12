package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.notification.Notification;
import ch.eitchnet.chronivaro.core.notification.NotificationDispatcher;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;

import java.util.List;

public abstract class AbstractChronivaroService<A extends ServiceArgument, R extends ServiceResult>
		extends AbstractService<A, R> {

	protected void dispatchNotifications(List<Notification> notifications) {
		if (notifications.isEmpty())
			return;
		NotificationDispatcher dispatcher = NotificationDispatcher.createDefault(getAgent());
		dispatcher.dispatch(notifications);
	}
}