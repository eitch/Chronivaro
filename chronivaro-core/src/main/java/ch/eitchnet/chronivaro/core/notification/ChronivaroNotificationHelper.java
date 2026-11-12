package ch.eitchnet.chronivaro.core.notification;

import li.strolch.agent.api.StrolchAgent;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Group;
import li.strolch.privilege.model.UserRep;
import li.strolch.privilege.model.UserState;
import li.strolch.utils.helper.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

public final class ChronivaroNotificationHelper {

	private static final Logger logger = LoggerFactory.getLogger(ChronivaroNotificationHelper.class);

	private ChronivaroNotificationHelper() {
	}

	public static List<Notification> absenceSubmitted(StrolchTransaction tx, StrolchAgent agent, String employeeId,
			ZonedDateTime start, ZonedDateTime end, String actorId, String actorUsername) {
		return safely("absence submission", () -> createAbsenceSubmitted(tx, employeeId, start, end, actorId,
				actorUsername, loadDirectory(agent)));
	}

	public static List<Notification> absenceDecision(StrolchTransaction tx, StrolchAgent agent, String employeeId,
			ZonedDateTime start, ZonedDateTime end, String decision, String actorId, String actorUsername) {
		return safely("absence decision", () -> createAbsenceDecision(tx, employeeId, start, end, decision,
				actorId, actorUsername, loadDirectory(agent)));
	}

	public static List<Notification> periodSubmitted(StrolchTransaction tx, StrolchAgent agent, String employeeId,
			YearMonth period, String actorId, String actorUsername) {
		return safely("period submission", () -> createPeriodSubmitted(tx, employeeId, period, actorId, actorUsername,
				loadDirectory(agent)));
	}

	public static List<Notification> periodDecision(StrolchTransaction tx, StrolchAgent agent, String employeeId,
			YearMonth period, String decision, String actorId, String actorUsername) {
		return safely("period decision", () -> createPeriodDecision(tx, employeeId, period, decision, actorId,
				actorUsername, loadDirectory(agent)));
	}

	static List<Notification> createAbsenceSubmitted(StrolchTransaction tx, String employeeId, ZonedDateTime start,
			ZonedDateTime end, String actorId, String actorUsername, List<UserRep> users, List<Group> groups) {
		return createAbsenceSubmitted(tx, employeeId, start, end, actorId, actorUsername, directory(users, groups));
	}

	static List<Notification> createAbsenceDecision(StrolchTransaction tx, String employeeId, ZonedDateTime start,
			ZonedDateTime end, String decision, String actorId, String actorUsername, List<UserRep> users,
			List<Group> groups) {
		return createAbsenceDecision(tx, employeeId, start, end, decision, actorId, actorUsername,
				directory(users, groups));
	}

	static List<Notification> createPeriodDecision(StrolchTransaction tx, String employeeId, YearMonth period,
			String decision, String actorId, String actorUsername, List<UserRep> users, List<Group> groups) {
		return createPeriodDecision(tx, employeeId, period, decision, actorId, actorUsername, directory(users, groups));
	}

	private static List<Notification> createAbsenceSubmitted(StrolchTransaction tx, String employeeId,
			ZonedDateTime start, ZonedDateTime end, String actorId, String actorUsername, Directory directory) {
		Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, false);
		if (employee == null)
			return List.of();

		String title = "Absence request awaits review";
		String message = "An absence request for " + employee.getName() + " from " + start.toLocalDate() + " to " +
				end.toLocalDate() + " awaits your review.";
		return approverNotifications(tx, employee, directory, actorId, actorUsername, "absence.submitted", title,
				message);
	}

	private static List<Notification> createAbsenceDecision(StrolchTransaction tx, String employeeId,
			ZonedDateTime start, ZonedDateTime end, String decision, String actorId, String actorUsername,
			Directory directory) {
		Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, false);
		if (employee == null)
			return List.of();

		String title = "Absence request " + decision;
		String message = "Your absence request from " + start.toLocalDate() + " to " + end.toLocalDate() + " was " +
				decision + ".";
		return employeeNotifications(tx, employee, directory, actorId, actorUsername, "absence." + decision, title,
				message);
	}

	private static List<Notification> createPeriodSubmitted(StrolchTransaction tx, String employeeId, YearMonth period,
			String actorId, String actorUsername, Directory directory) {
		Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, false);
		if (employee == null)
			return List.of();

		String title = "Time period awaits review";
		String message = "The time period " + period + " for " + employee.getName() + " awaits your review.";
		return approverNotifications(tx, employee, directory, actorId, actorUsername, "period.submitted", title,
				message);
	}

	private static List<Notification> createPeriodDecision(StrolchTransaction tx, String employeeId, YearMonth period,
			String decision, String actorId, String actorUsername, Directory directory) {
		Resource employee = tx.getResourceBy(TYPE_EMPLOYEE, employeeId, false);
		if (employee == null)
			return List.of();

		String title = "Time period " + decision;
		String message = "Your time period " + period + " was " + decision + ".";
		return employeeNotifications(tx, employee, directory, actorId, actorUsername, "period." + decision, title,
				message);
	}

	private static List<Notification> approverNotifications(StrolchTransaction tx, Resource employee, Directory directory,
			String actorId, String actorUsername, String type, String title, String message) {
		List<UserRep> users = directory.users().stream()
				.filter(user -> user.getUserState() == UserState.ENABLED)
				.filter(user -> !isActor(user, actorId, actorUsername))
				.filter(user -> !isLinkedToEmployee(tx, user, employee.getId()))
				.filter(user -> isApprover(tx, employee, user, directory))
				.toList();
		return notifications(users, type, title, message);
	}

	private static List<Notification> employeeNotifications(StrolchTransaction tx, Resource employee,
			Directory directory, String actorId, String actorUsername, String type, String title, String message) {
		List<UserRep> users = directory.users().stream()
				.filter(user -> user.getUserState() == UserState.ENABLED)
				.filter(user -> !isActor(user, actorId, actorUsername))
				.filter(user -> isLinkedToEmployee(tx, user, employee.getId()))
				.toList();
		return notifications(users, type, title, message);
	}

	private static boolean isApprover(StrolchTransaction tx, Resource targetEmployee, UserRep user,
			Directory directory) {
		Set<String> roles = directory.rolesFor(user);
		if (roles.contains("Admin") || roles.contains(ROLE_ADMIN) || roles.contains(ROLE_ADMINISTRATOR) ||
				roles.contains(ROLE_STROLCH_ADMIN) || roles.contains(ROLE_PRIVILEGE_ADMIN) || roles.contains(ROLE_HR)) {
			return true;
		}
		return roles.contains(ROLE_SUPERVISOR) && supervises(tx, targetEmployee, user);
	}

	private static boolean supervises(StrolchTransaction tx, Resource targetEmployee, UserRep user) {
		if (!targetEmployee.hasRelation(PARAM_PRIMARY_TEAM))
			return false;

		Set<String> teamIds = new HashSet<>();
		List<Resource> supervisorEmployees = tx.streamResources(TYPE_EMPLOYEE)
				.filter(employee -> isLinkedUser(employee, user))
				.toList();
		for (Resource supervisor : supervisorEmployees) {
			if (supervisor.hasRelation(PARAM_PRIMARY_TEAM))
				teamIds.add(supervisor.getRelationId(PARAM_PRIMARY_TEAM));
			String supervisorId = supervisor.getId();
			tx.streamResources(TYPE_TEAM).forEach(team -> {
				if (team.hasRelation(PARAM_LEADER) && supervisorId.equals(team.getRelationId(PARAM_LEADER))) {
					teamIds.add(team.getId());
				} else if (team.hasParameter(PARAM_LEADER) &&
						(supervisorId.equals(team.getString(PARAM_LEADER)) ||
								user.getUsername().equals(team.getString(PARAM_LEADER)))) {
					teamIds.add(team.getId());
				}
			});
		}

		if (supervisorEmployees.isEmpty() && StringHelper.isNotEmpty(user.getUsername())) {
			tx.streamResources(TYPE_TEAM).forEach(team -> {
				if ((team.hasParameter(PARAM_LEADER) && user.getUsername().equals(team.getString(PARAM_LEADER))) ||
						(team.hasRelation(PARAM_LEADER) &&
								user.getUsername().equals(team.getRelationId(PARAM_LEADER)))) {
					teamIds.add(team.getId());
				}
			});
		}

		return teamIds.contains(targetEmployee.getRelationId(PARAM_PRIMARY_TEAM));
	}

	private static boolean isLinkedToEmployee(StrolchTransaction tx, UserRep user, String employeeId) {
		return tx.streamResources(TYPE_EMPLOYEE)
				.filter(employee -> employeeId.equals(employee.getId()))
				.anyMatch(employee -> isLinkedUser(employee, user));
	}

	private static boolean isLinkedUser(Resource employee, UserRep user) {
		return matches(employee, PARAM_USER_ID, user.getUserId()) ||
				matches(employee, PARAM_USER_ID, user.getUsername()) ||
				matches(employee, PARAM_USERNAME, user.getUserId()) ||
				matches(employee, PARAM_USERNAME, user.getUsername());
	}

	private static boolean matches(Resource resource, String parameter, String value) {
		return StringHelper.isNotEmpty(value) && resource.hasParameter(parameter) && value.equals(
				resource.getString(parameter));
	}

	private static boolean isActor(UserRep user, String actorId, String actorUsername) {
		return same(user.getUserId(), actorId) || same(user.getUsername(), actorUsername) ||
				same(user.getUsername(), actorId);
	}

	private static boolean same(String left, String right) {
		return StringHelper.isNotEmpty(left) && left.equals(right);
	}

	private static List<Notification> notifications(List<UserRep> users, String type, String title, String message) {
		List<Notification> notifications = new ArrayList<>();
		Set<String> recipientIds = new HashSet<>();
		for (UserRep user : users) {
			String recipientId = StringHelper.trimOrEmpty(user.getUserId());
			if (recipientId.isEmpty())
				recipientId = StringHelper.trimOrEmpty(user.getUsername());
			if (recipientId.isEmpty() || !recipientIds.add(recipientId))
				continue;

			String email = validEmail(user.getEmail());
			Map<String, String> destinations = email == null ? Map.of() : Map.of(NotificationRecipient.EMAIL, email);
			NotificationRecipient recipient = new NotificationRecipient(recipientId, destinations);
			notifications.add(new Notification(type, recipient, title, message));
		}
		return List.copyOf(notifications);
	}

	private static String validEmail(String value) {
		String email = StringHelper.trimOrEmpty(value);
		if (email.isEmpty())
			return null;
		try {
			InternetAddress[] addresses = InternetAddress.parse(email, true);
			if (addresses.length != 1)
				return null;
			addresses[0].validate();
			return addresses[0].getAddress();
		} catch (AddressException e) {
			return null;
		}
	}

	private static Directory loadDirectory(StrolchAgent agent) {
		try {
			return agent.runAsAgentWithResult(ctx -> {
				li.strolch.privilege.handler.PrivilegeHandler handler = agent.getPrivilegeHandler()
						.getPrivilegeHandler();
				List<UserRep> users = handler.getUsers(ctx.getCertificate());
				List<Group> groups;
				try {
					groups = handler.getGroups(ctx.getCertificate());
				} catch (Exception e) {
					logger.warn("Could not load notification groups; resolving recipients using direct roles only: {}",
							e.getMessage());
					groups = List.of();
				}
				return directory(users, groups);
			});
		} catch (Exception e) {
			throw new IllegalStateException("Could not load notification recipients", e);
		}
	}

	private static Directory directory(List<UserRep> users, List<Group> groups) {
		Map<String, Set<String>> groupRoles = new HashMap<>();
		for (Group group : groups)
			groupRoles.put(group.name(), group.roles());
		return new Directory(users, groupRoles);
	}

	private static List<Notification> safely(String event, Supplier<List<Notification>> operation) {
		try {
			return operation.get();
		} catch (RuntimeException e) {
			logger.warn("Could not resolve recipients for {} notification: {}", event, e.getMessage(), e);
			return List.of();
		}
	}

	private record Directory(List<UserRep> users, Map<String, Set<String>> groupRoles) {
		private Directory {
			users = List.copyOf(users);
			groupRoles = Map.copyOf(groupRoles);
		}

		private Set<String> rolesFor(UserRep user) {
			Set<String> roles = new HashSet<>(user.getRoles());
			for (String group : user.getGroups())
				roles.addAll(this.groupRoles.getOrDefault(group, Set.of()));
			return roles;
		}
	}
}