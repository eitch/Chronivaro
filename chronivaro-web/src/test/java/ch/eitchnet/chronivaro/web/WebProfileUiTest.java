package ch.eitchnet.chronivaro.web;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class WebProfileUiTest {

	private File getWebappDir() {
		File dir = new File("src/main/webapp");
		if (dir.exists()) return dir;
		dir = new File("chronivaro-web/src/main/webapp");
		if (dir.exists()) return dir;
		dir = new File("../chronivaro-web/src/main/webapp");
		if (dir.exists()) return dir;
		throw new IllegalStateException("Could not locate chronivaro-web/src/main/webapp directory");
	}

	@Test
	public void shouldVerifyProfileViewComponent() throws IOException {
		File profileViewFile = new File(getWebappDir(), "js/pages/ProfileView.js");
		assertTrue("ProfileView.js must exist", profileViewFile.exists());
		String content = Files.readString(profileViewFile.toPath());

		assertTrue("ProfileView must import EmployeeApi", content.contains("import EmployeeApi from '../api/EmployeeApi.js'"));
		assertTrue("ProfileView must import AuthApi", content.contains("import AuthApi from '../api/AuthApi.js'"));
		assertTrue("ProfileView must import ConfigurationApi", content.contains("import ConfigurationApi from '../api/ConfigurationApi.js'"));
		assertTrue("ProfileView must call getMyProfile", content.contains("EmployeeApi.getMyProfile()"));
		assertTrue("ProfileView must call getMySchedules", content.contains("EmployeeApi.getMySchedules()"));
		assertTrue("ProfileView must dynamically calculate rate from weeklyTargetMinutes", content.contains("weeklyTargetMinutes"));
		org.junit.Assert.assertFalse("ProfileView must not contain hard-coded 2520", content.contains("/ 2520"));
		assertTrue("ProfileView must display personal number", content.contains("personalNumber"));
		assertTrue("ProfileView must display user account section", content.contains("profile.userAccount"));
		assertTrue("ProfileView must display master data section", content.contains("profile.masterData"));
		assertTrue("ProfileView must display employment schedule section", content.contains("profile.employmentSchedule"));
	}

	@Test
	public void shouldVerifyEmployeeApiMethods() throws IOException {
		File apiFile = new File(getWebappDir(), "js/api/EmployeeApi.js");
		assertTrue("EmployeeApi.js must exist", apiFile.exists());
		String content = Files.readString(apiFile.toPath());

		assertTrue("EmployeeApi must have getMyProfile", content.contains("getMyProfile()"));
		assertTrue("EmployeeApi must query rest/chronivaro/v1/me/profile", content.contains("rest/chronivaro/v1/me/profile"));
		assertTrue("EmployeeApi must have getMySchedules", content.contains("getMySchedules()"));
		assertTrue("EmployeeApi must query rest/chronivaro/v1/me/schedules", content.contains("rest/chronivaro/v1/me/schedules"));
	}

	@Test
	public void shouldVerifyProfileStyles() throws IOException {
		File cssFile = new File(getWebappDir(), "assets/css/style.css");
		assertTrue("style.css must exist", cssFile.exists());
		String css = Files.readString(cssFile.toPath());

		assertTrue("style.css must style #profile-view", css.contains("#profile-view"));
		assertTrue("style.css must style .profile-cards-grid", css.contains(".profile-cards-grid"));
		assertTrue("style.css must style .profile-card", css.contains(".profile-card"));
		assertTrue("style.css must style .profile-schedule-card", css.contains(".profile-schedule-card"));
	}
}
