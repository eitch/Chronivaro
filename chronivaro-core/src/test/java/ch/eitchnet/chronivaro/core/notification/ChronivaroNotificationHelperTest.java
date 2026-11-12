package ch.eitchnet.chronivaro.core.notification;

import ch.eitchnet.chronivaro.core.ChronivaroTestHelper;
import ch.eitchnet.chronivaro.core.service.ApprovePeriodService;
import ch.eitchnet.chronivaro.core.service.PeriodActionArgument;
import ch.eitchnet.chronivaro.core.service.RejectAbsenceService;
import ch.eitchnet.chronivaro.core.service.RejectPeriodService;
import ch.eitchnet.chronivaro.core.service.ReopenPeriodService;
import ch.eitchnet.chronivaro.core.service.RequestAbsenceService;
import ch.eitchnet.chronivaro.core.service.SubmitAbsenceService;
import ch.eitchnet.chronivaro.core.service.SubmitPeriodService;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.privilege.model.Group;
import li.strolch.privilege.model.UserRep;
import li.strolch.privilege.model.UserState;
import li.strolch.privilege.model.internal.UserHistory;
import li.strolch.service.StringArgument;
import li.strolch.service.StringResult;
import li.strolch.service.api.ServiceResult;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.privilege.base.PrivilegeConstants.EMAIL;
import static org.junit.Assert.*;

public class ChronivaroNotificationHelperTest {

	private static final String TARGET_EMPLOYEE_ID = "notification-target";
	private static final String TARGET_USER_ID = "notification-employee-user";
	private static final ZonedDateTime START = ZonedDateTime.parse("2026-04-06T00:00:00Z");
	private static final ZonedDateTime END = ZonedDateTime.parse("2026-04-08T23:59:59Z");

	private RuntimeMock runtimeMock;
	private Certificate certificate;

	@Before
	public void before() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + getClass().getSimpleName() + "-" + System.nanoTime(),
				"src/test/resources");
		runtimeMock.startContainer();
		certificate = runtimeMock.login("admin", "admin");

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource team = createTeam(tx, "notification-team");
			Resource otherTeam = createTeam(tx, "notification-other-team");
			Resource target = ChronivaroTestHelper.createEmployee(tx, TARGET_EMPLOYEE_ID, "Target Employee", true);
			target.setString(PARAM_USER_ID, TARGET_USER_ID);
			target.setRelation(PARAM_PRIMARY_TEAM, team);
			tx.update(target);

			Resource supervisor = ChronivaroTestHelper.createEmployee(tx, "notification-supervisor", "Team Supervisor",
					false);
			supervisor.setString(PARAM_USER_ID, "supervisor-user");
			supervisor.setRelation(PARAM_PRIMARY_TEAM, team);
			tx.update(supervisor);

			Resource otherSupervisor = ChronivaroTestHelper.createEmployee(tx, "notification-outside-supervisor",
					"Other Supervisor", false);
			otherSupervisor.setString(PARAM_USER_ID, "outside-supervisor-user");
			otherSupervisor.setRelation(PARAM_PRIMARY_TEAM, otherTeam);
			tx.update(otherSupervisor);
			ChronivaroTestHelper.createAbsenceType(tx, "SICK", "Sick");
			tx.commitOnClose();
		}

		addRuntimeUser("supervisor-user", Set.of(ROLE_SUPERVISOR, ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR),
				"supervisor@example.ch");
		addRuntimeUser(TARGET_USER_ID, Set.of(ROLE_EMPLOYEE, ROLE_MODEL_ACCESSOR), "employee@example.ch");
	}

	@After
	public void after() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Test
	public void shouldNotifyOnlyEligibleApproversAndOmitSensitiveAbsenceDetails() {
		List<UserRep> users = List.of(
				user("supervisor-user", "supervisor", UserState.ENABLED, Set.of(ROLE_SUPERVISOR), Set.of(),
						"supervisor@example.ch"),
				user("outside-supervisor-user", "outside-supervisor", UserState.ENABLED, Set.of(ROLE_SUPERVISOR),
						Set.of(), "outside@example.ch"),
				user("hr-user", "hr", UserState.ENABLED, Set.of(), Set.of("HR"), "hr@example.ch"),
				user("admin-user", "admin-notice", UserState.ENABLED, Set.of(ROLE_ADMIN), Set.of(),
						"admin@example.ch"),
				user("duplicate-admin", "admin-duplicate", UserState.ENABLED, Set.of(ROLE_ADMIN), Set.of(),
						"admin@example.ch"),
				user("disabled-user", "disabled", UserState.DISABLED, Set.of(ROLE_HR), Set.of(),
						"disabled@example.ch"),
				user("actor-user", "actor", UserState.ENABLED, Set.of(ROLE_HR), Set.of(), "actor@example.ch"),
				user(TARGET_USER_ID, "target-user", UserState.ENABLED, Set.of(ROLE_ADMIN), Set.of(),
						"target@example.ch"),
				user("missing-email", "missing-email", UserState.ENABLED, Set.of(ROLE_HR), Set.of(), null),
				user("invalid-email", "invalid-email", UserState.ENABLED, Set.of(ROLE_HR), Set.of(), "not an address"));
		List<Group> groups = List.of(new Group("HR", Set.of(ROLE_HR), Map.of()));

		List<Notification> notifications;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			notifications = ChronivaroNotificationHelper.createAbsenceSubmitted(tx, TARGET_EMPLOYEE_ID, START, END,
					"actor-user", "actor", users, groups);
		}

		assertEquals(Set.of("supervisor-user", "hr-user", "admin-user", "duplicate-admin", "missing-email",
				"invalid-email"),
				notifications.stream().map(n -> n.recipient().id()).collect(java.util.stream.Collectors.toSet()));
		assertTrue(notifications.stream().filter(n -> n.recipient().id().equals("invalid-email"))
				.allMatch(n -> n.recipient().destination(NotificationRecipient.EMAIL) == null));
		assertTrue(notifications.stream().allMatch(n -> "absence.submitted".equals(n.type())));
		assertTrue(notifications.stream().noneMatch(n -> n.message().contains("SICK")));
		assertTrue(notifications.stream().noneMatch(n -> n.message().contains("private reason")));
	}

	@Test
	public void shouldNotifyLinkedEmployeeOfAbsenceAndPeriodDecisionsWithoutReasons() {
		List<UserRep> users = List.of(
				user(TARGET_USER_ID, "target-user", UserState.ENABLED, Set.of(ROLE_EMPLOYEE), Set.of(),
						"employee@example.ch"),
				user("admin-user", "admin-notice", UserState.ENABLED, Set.of(ROLE_ADMIN), Set.of(),
						"admin@example.ch"));
		List<Notification> absenceNotifications;
		List<Notification> periodNotifications;
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			absenceNotifications = ChronivaroNotificationHelper.createAbsenceDecision(tx, TARGET_EMPLOYEE_ID, START, END,
					"rejected", "admin-user", "admin-notice", users, List.of());
			periodNotifications = ChronivaroNotificationHelper.createPeriodDecision(tx, TARGET_EMPLOYEE_ID,
					YearMonth.of(2026, 4), "reopened", "admin-user", "admin-notice", users, List.of());
		}

		assertEquals(1, absenceNotifications.size());
		assertEquals(TARGET_USER_ID, absenceNotifications.getFirst().recipient().id());
		assertEquals("absence.rejected", absenceNotifications.getFirst().type());
		assertFalse(absenceNotifications.getFirst().message().contains("reason"));
		assertEquals(1, periodNotifications.size());
		assertEquals("period.reopened", periodNotifications.getFirst().type());
	}

	@Test
	public void shouldNotDispatchWhenAbsenceSubmissionFails() {
		CapturingSubmitAbsenceService service = new CapturingSubmitAbsenceService();
		ServiceResult result = runtimeMock.getServiceHandler()
				.doService(certificate, service, new StringArgument("missing-absence"));

		assertFalse(result.getMessage(), result.isOk());
		assertFalse(service.dispatchCalled);
		assertTrue(service.notifications.isEmpty());
	}

	@Test
	public void shouldDispatchWorkflowNoticesOnlyAfterCommit() {
		RequestAbsenceService.RequestAbsenceArgument request = new RequestAbsenceService.RequestAbsenceArgument();
		request.employeeId = TARGET_EMPLOYEE_ID;
		request.absenceTypeCode = "SICK";
		request.start = START;
		request.end = END;
		request.durationType = DURATION_FULL_DAY;
		request.comment = "private symptom";
		CapturingRequestAbsenceService requestService = new CapturingRequestAbsenceService();
		StringResult requested = runtimeMock.getServiceHandler().doService(certificate, requestService, request);

		assertTrue(requested.getMessage(), requested.isOk());
		assertTrue(requestService.committedBeforeDispatch);
		assertEquals(1, requestService.notifications.size());
		Notification submitted = requestService.notifications.getFirst();
		assertEquals("absence.submitted", submitted.type());
		assertEquals("supervisor@example.ch", submitted.recipient().destination(NotificationRecipient.EMAIL));
		assertFalse(submitted.message().contains("SICK"));
		assertFalse(submitted.message().contains("private symptom"));

		RejectAbsenceService.RejectAbsenceArgument reject = new RejectAbsenceService.RejectAbsenceArgument();
		reject.absenceId = requested.getValue();
		reject.comment = "private reason";
		CapturingRejectAbsenceService rejectService = new CapturingRejectAbsenceService();
		ServiceResult rejected = runtimeMock.getServiceHandler().doService(certificate, rejectService, reject);

		assertTrue(rejected.getMessage(), rejected.isOk());
		assertTrue(rejectService.committedBeforeDispatch);
		assertEquals(1, rejectService.notifications.size());
		Notification decision = rejectService.notifications.getFirst();
		assertEquals("absence.rejected", decision.type());
		assertEquals("employee@example.ch", decision.recipient().destination(NotificationRecipient.EMAIL));
		assertFalse(decision.message().contains("private reason"));
	}

	@Test
	public void shouldSkipDraftAndNotifyEmployeeForDirectApproval() {
		RequestAbsenceService.RequestAbsenceArgument request = new RequestAbsenceService.RequestAbsenceArgument();
		request.employeeId = TARGET_EMPLOYEE_ID;
		request.absenceTypeCode = "SICK";
		request.start = START;
		request.end = END;
		request.durationType = DURATION_FULL_DAY;
		request.asDraft = true;
		CapturingRequestAbsenceService draftService = new CapturingRequestAbsenceService();
		StringResult draft = runtimeMock.getServiceHandler().doService(certificate, draftService, request);
		assertTrue(draft.getMessage(), draft.isOk());
		assertTrue(draftService.notifications.isEmpty());

		request.asDraft = false;
		request.directApprove = true;
		request.start = START.plusDays(10);
		request.end = END.plusDays(10);
		CapturingRequestAbsenceService approvalService = new CapturingRequestAbsenceService();
		StringResult approved = runtimeMock.getServiceHandler().doService(certificate, approvalService, request);
		assertTrue(approved.getMessage(), approved.isOk());
		assertNotice(approvalService.notifications, "absence.approved", "employee@example.ch");
	}

	@Test
	public void shouldNotifyForPeriodSubmissionDecisionsAndReopening() {
		YearMonth period = YearMonth.of(2026, 6);
		PeriodActionArgument arg = new PeriodActionArgument(TARGET_EMPLOYEE_ID, period, "private comment");
		CapturingSubmitPeriodService submit = new CapturingSubmitPeriodService();
		ServiceResult result = runtimeMock.getServiceHandler().doService(certificate, submit, arg);
		assertTrue(result.getMessage(), result.isOk());
		assertNotice(submit.notifications, "period.submitted", "supervisor@example.ch");

		CapturingRejectPeriodService reject = new CapturingRejectPeriodService();
		result = runtimeMock.getServiceHandler().doService(certificate, reject, arg);
		assertTrue(result.getMessage(), result.isOk());
		assertNotice(reject.notifications, "period.rejected", "employee@example.ch");
		assertFalse(reject.notifications.getFirst().message().contains("private comment"));

		result = runtimeMock.getServiceHandler().doService(certificate, new SubmitPeriodService(), arg);
		assertTrue(result.getMessage(), result.isOk());
		CapturingApprovePeriodService approve = new CapturingApprovePeriodService();
		result = runtimeMock.getServiceHandler().doService(certificate, approve, arg);
		assertTrue(result.getMessage(), result.isOk());
		assertNotice(approve.notifications, "period.approved", "employee@example.ch");

		CapturingReopenPeriodService reopen = new CapturingReopenPeriodService();
		result = runtimeMock.getServiceHandler().doService(certificate, reopen, arg);
		assertTrue(result.getMessage(), result.isOk());
		assertNotice(reopen.notifications, "period.reopened", "employee@example.ch");
	}

	private static void assertNotice(List<Notification> notifications, String type, String email) {
		assertEquals(1, notifications.size());
		assertEquals(type, notifications.getFirst().type());
		assertEquals(email, notifications.getFirst().recipient().destination(NotificationRecipient.EMAIL));
	}

	private void addRuntimeUser(String username, Set<String> roles, String email) {
		UserRep user = new UserRep(null, username, "First", "Last", UserState.ENABLED, Set.of(), roles,
				Locale.ENGLISH, Map.of(EMAIL, email), UserHistory.EMPTY);
		runtimeMock.getPrivilegeHandler().getPrivilegeHandler().addUser(certificate, user, "password".toCharArray());
	}

	private boolean absenceInState(String state) {
		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			return tx.streamResources(TYPE_ABSENCE).anyMatch(absence ->
					TARGET_EMPLOYEE_ID.equals(absence.getRelationId(PARAM_EMPLOYEE)) &&
							state.equals(absence.getString(PARAM_STATE)));
		}
	}

	private static Resource createTeam(StrolchTransaction tx, String id) {
		Resource team = tx.getResourceTemplate(TYPE_TEAM, true);
		team.setId(id);
		team.setName(id);
		team.setString(PARAM_NAME, id);
		tx.add(team);
		return team;
	}

	private static UserRep user(String userId, String username, UserState state, Set<String> roles, Set<String> groups,
			String email) {
		Map<String, String> properties = email == null ? Map.of() : Map.of(EMAIL, email);
		return new UserRep(userId, username, "First", "Last", state, groups, roles, Locale.ENGLISH, properties,
				UserHistory.EMPTY);
	}

	private static class CapturingSubmitAbsenceService extends SubmitAbsenceService {
		private boolean dispatchCalled;
		private List<Notification> notifications = List.of();

		@Override
		protected void dispatchNotifications(List<Notification> notifications) {
			this.dispatchCalled = true;
			this.notifications = notifications;
		}
	}

	private class CapturingRequestAbsenceService extends RequestAbsenceService {
		private boolean committedBeforeDispatch;
		private List<Notification> notifications = List.of();

		@Override
		protected void dispatchNotifications(List<Notification> notifications) {
			this.committedBeforeDispatch = absenceInState(STATE_SUBMITTED);
			this.notifications = notifications;
		}
	}

	private class CapturingRejectAbsenceService extends RejectAbsenceService {
		private boolean committedBeforeDispatch;
		private List<Notification> notifications = List.of();

		@Override
		protected void dispatchNotifications(List<Notification> notifications) {
			this.committedBeforeDispatch = absenceInState(STATE_REJECTED);
			this.notifications = notifications;
		}
	}

	private static class CapturingSubmitPeriodService extends SubmitPeriodService {
		private List<Notification> notifications = List.of();

		@Override
		protected void dispatchNotifications(List<Notification> notifications) {
			this.notifications = notifications;
		}
	}

	private static class CapturingRejectPeriodService extends RejectPeriodService {
		private List<Notification> notifications = List.of();

		@Override
		protected void dispatchNotifications(List<Notification> notifications) {
			this.notifications = notifications;
		}
	}

	private static class CapturingApprovePeriodService extends ApprovePeriodService {
		private List<Notification> notifications = List.of();

		@Override
		protected void dispatchNotifications(List<Notification> notifications) {
			this.notifications = notifications;
		}
	}

	private static class CapturingReopenPeriodService extends ReopenPeriodService {
		private List<Notification> notifications = List.of();

		@Override
		protected void dispatchNotifications(List<Notification> notifications) {
			this.notifications = notifications;
		}
	}
}