package ch.atexxi.chronivaro.core.model;

import li.strolch.agent.api.StrolchAgent;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static ch.atexxi.chronivaro.core.ChronivaroTestHelper.createEmployee;
import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static org.junit.Assert.assertEquals;

public class WorkEntryHelperTest {

	private static RuntimeMock runtimeMock;
	private static Certificate certificate;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime("target/" + WorkEntryHelperTest.class.getSimpleName(),
				"src/test/resources");
		runtimeMock.startContainer();
		certificate = runtimeMock.login("admin", "admin");
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Test
	public void shouldFindWorkEntriesAcrossMultipleDays() {
		String empId = "test-emp-1";
		ZonedDateTime day1 = ZonedDateTime.parse("2026-08-01T10:00:00Z");
		ZonedDateTime day2 = ZonedDateTime.parse("2026-08-02T10:00:00Z");

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, empId, "Test Employee", day1);

			// Day 1 entry
			Resource workEntry1 = createWorkEntry(tx, employee, day1, day1.plusHours(2));
			// Day 2 entry
			Resource workEntry2 = createWorkEntry(tx, employee, day2, day2.plusHours(2));

			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			ZonedDateTime from = ZonedDateTime.parse("2026-08-01T00:00:00Z");
			ZonedDateTime to = ZonedDateTime.parse("2026-08-03T00:00:00Z");

			List<Resource> entries = WorkEntryHelper.findWorkEntries(tx, empId, from, to);
			assertEquals(2, entries.size());
			assertEquals(day1, entries.get(0).getDate(PARAM_START));
			assertEquals(day2, entries.get(1).getDate(PARAM_START));
		}
	}

	@Test
	public void shouldFindWorkEntriesAtBoundaries() {
		String empId = "test-emp-2";
		ZonedDateTime start = ZonedDateTime.parse("2026-08-10T10:00:00Z");
		ZonedDateTime end = ZonedDateTime.parse("2026-08-10T12:00:00Z");

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, empId, "Test Employee", start);
			createWorkEntry(tx, employee, start, end);
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			// Range ends exactly when entry starts -> should be found (inclusive)
			List<Resource> entries = WorkEntryHelper.findWorkEntries(tx, empId, start.minusHours(1), start);
			assertEquals(1, entries.size());

			// Range starts exactly when entry ends -> should be found (inclusive)
			entries = WorkEntryHelper.findWorkEntries(tx, empId, end, end.plusHours(1));
			assertEquals(1, entries.size());

			// Range entirely before -> not found
			entries = WorkEntryHelper.findWorkEntries(tx, empId, start.minusHours(2), start.minusHours(1));
			assertEquals(0, entries.size());

			// Range entirely after -> not found
			entries = WorkEntryHelper.findWorkEntries(tx, empId, end.plusHours(1), end.plusHours(2));
			assertEquals(0, entries.size());
		}
	}

	@Test
	public void shouldFindActiveWorkEntry() {
		String empId = "test-emp-3";
		ZonedDateTime start = ZonedDateTime.now().minusHours(1);

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, empId, "Test Employee", start);
			// Active entry (end is 1970)
			createWorkEntry(tx, employee, start, ZonedDateTime.parse("1970-01-01T00:00:00Z"));
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			ZonedDateTime from = start.minusHours(1);
			ZonedDateTime to = start.plusHours(2);

			List<Resource> entries = WorkEntryHelper.findWorkEntries(tx, empId, from, to);
			assertEquals(1, entries.size());
		}
	}

	@Test
	public void shouldFindWorkEntrySpanningMultipleDays() {
		String empId = "test-emp-4";
		ZonedDateTime start = ZonedDateTime.parse("2026-08-20T22:00:00Z");
		ZonedDateTime end = ZonedDateTime.parse("2026-08-21T02:00:00Z");

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, empId, "Test Employee", start);
			createWorkEntry(tx, employee, start, end);
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			// Search for day 1
			List<Resource> entries = WorkEntryHelper.findWorkEntries(tx, empId,
					ZonedDateTime.parse("2026-08-20T00:00:00Z"), ZonedDateTime.parse("2026-08-20T23:59:59Z"));
			assertEquals(1, entries.size());

			// Search for day 2
			entries = WorkEntryHelper.findWorkEntries(tx, empId, ZonedDateTime.parse("2026-08-21T00:00:00Z"),
					ZonedDateTime.parse("2026-08-21T23:59:59Z"));
			assertEquals(1, entries.size());
		}
	}

	@Test
	public void shouldFindEntryWhenRangeIsInsideEntry() {
		String empId = "test-emp-5";
		ZonedDateTime start = ZonedDateTime.parse("2026-08-25T08:00:00Z");
		ZonedDateTime end = ZonedDateTime.parse("2026-08-25T17:00:00Z");

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, empId, "Test Employee", start);
			createWorkEntry(tx, employee, start, end);
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			// Range is entirely within the entry
			List<Resource> entries = WorkEntryHelper.findWorkEntries(tx, empId, start.plusHours(1), end.minusHours(1));
			assertEquals(1, entries.size());
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldFailOnOverlap() {
		String empId = "test-emp-6";
		ZonedDateTime start = ZonedDateTime.parse("2026-08-30T08:00:00Z");
		ZonedDateTime end = ZonedDateTime.parse("2026-08-30T12:00:00Z");

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, empId, "Test Employee", start);
			createWorkEntry(tx, employee, start, end);
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			// Overlapping entry (starts during existing)
			WorkEntryHelper.validateNoOverlap(tx, empId, start.plusHours(1), end.plusHours(1), null);
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldFailOnOverlapWithActiveTimer() {
		String empId = "test-emp-7";
		ZonedDateTime start = ZonedDateTime.now().minusHours(2);

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, empId, "Test Employee", start);
			createWorkEntry(tx, employee, start, ZonedDateTime.parse("1970-01-01T00:00:00Z"));
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			// Overlapping entry with active timer
			WorkEntryHelper.validateNoOverlap(tx, empId, start.plusHours(1), start.plusHours(2), null);
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldFailOnOverlapAcrossMidnight() {
		String empId = "test-emp-8";
		ZonedDateTime start = ZonedDateTime.parse("2026-08-31T22:00:00Z");
		ZonedDateTime end = ZonedDateTime.parse("2026-09-01T02:00:00Z");

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, empId, "Test Employee", start);
			createWorkEntry(tx, employee, start, end);
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			// Overlap on the second day
			WorkEntryHelper.validateNoOverlap(tx, empId, 
					ZonedDateTime.parse("2026-09-01T01:00:00Z"), 
					ZonedDateTime.parse("2026-09-01T03:00:00Z"), null);
		}
	}

	@Test
	public void shouldNotFailOnNonOverlappingEntries() {
		String empId = "test-emp-9";
		ZonedDateTime start = ZonedDateTime.parse("2026-09-05T08:00:00Z");
		ZonedDateTime end = ZonedDateTime.parse("2026-09-05T12:00:00Z");

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, false)) {
			Resource employee = createEmployee(tx, empId, "Test Employee", start);
			createWorkEntry(tx, employee, start, end);
			tx.commitOnClose();
		}

		try (StrolchTransaction tx = runtimeMock.openUserTx(certificate, true)) {
			// Entry exactly after
			WorkEntryHelper.validateNoOverlap(tx, empId, end, end.plusHours(1), null);
			// Entry exactly before
			WorkEntryHelper.validateNoOverlap(tx, empId, start.minusHours(1), start, null);
		}
	}

	private Resource createWorkEntry(StrolchTransaction tx, Resource employee, ZonedDateTime start, ZonedDateTime end) {
		Resource workDay = WorkDayHelper.getOrCreateWorkDay(tx, employee, start);
		Resource workEntry = tx.getResourceTemplate(TYPE_WORK_ENTRY, true);
		workEntry.setId(StrolchAgent.getUniqueId());
		workEntry.setName("WorkEntry " + start);
		workEntry.setRelation(PARAM_EMPLOYEE, employee);
		workEntry.setRelation(PARAM_WORK_DAY, workDay);
		workEntry.setDate(PARAM_START, start);
		workEntry.setDate(PARAM_END, end);

		tx.add(workEntry);
		workDay.addRelation(PARAM_WORK_ENTRIES, workEntry);
		tx.update(workDay);

		return workEntry;
	}
}
