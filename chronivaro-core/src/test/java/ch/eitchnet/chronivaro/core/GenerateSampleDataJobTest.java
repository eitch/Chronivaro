package ch.eitchnet.chronivaro.core;

import ch.eitchnet.chronivaro.core.jobs.GenerateSampleDataJob;
import ch.eitchnet.chronivaro.core.model.ChronivaroConstants;
import li.strolch.job.JobMode;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.testbase.runtime.RuntimeMock;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GenerateSampleDataJobTest {

	private static final String TARGET_PATH = "target/" + GenerateSampleDataJobTest.class.getSimpleName();
	private static final String SOURCE_PATH = "src/test/resources";
	private static RuntimeMock runtimeMock;

	@BeforeClass
	public static void beforeClass() {
		runtimeMock = new RuntimeMock().mockRuntime(TARGET_PATH, SOURCE_PATH);
		runtimeMock.startContainer();
	}

	@AfterClass
	public static void afterClass() {
		if (runtimeMock != null)
			runtimeMock.destroyRuntime();
	}

	@Test
	public void shouldGenerateSampleDataAndBeIdempotent() throws Exception {
		GenerateSampleDataJob job = new GenerateSampleDataJob(runtimeMock.getAgent(),
				"GenerateSampleDataJob", "GenerateSampleDataJob", JobMode.Manual);

		// Execute job
		job.runNow();

		Certificate cert = runtimeMock.loginAdmin();
		try (StrolchTransaction tx = runtimeMock.openUserTx(cert, true)) {
			// Verify Locations
			assertTrue(tx.streamResources(ChronivaroConstants.TYPE_LOCATION).count() >= 2);

			// Verify Teams
			assertTrue(tx.streamResources(ChronivaroConstants.TYPE_TEAM).count() >= 3);

			// Verify Employees
			assertTrue(tx.streamResources(ChronivaroConstants.TYPE_EMPLOYEE).count() >= 5);

			// Verify Work entries
			assertTrue(tx.streamResources(ChronivaroConstants.TYPE_WORK_ENTRY).count() > 0);

			// Verify Absences
			assertTrue(tx.streamResources(ChronivaroConstants.TYPE_ABSENCE).count() >= 4);

			// Verify On-call periods
			assertTrue(tx.streamResources(ChronivaroConstants.TYPE_ON_CALL_PERIOD).count() >= 1);

			// Verify Working Location Defaults
			assertTrue(tx.streamResources(ChronivaroConstants.TYPE_WORKING_LOCATION_DEFAULT).count() >= 10);
		}

		// Run a second time to verify idempotency (no duplicate entries or errors)
		job.runNow();

		try (StrolchTransaction tx = runtimeMock.openUserTx(cert, true)) {
			// Employee count should remain the same
			assertEquals(5, tx.streamResources(ChronivaroConstants.TYPE_EMPLOYEE).count());
		}
	}
}
