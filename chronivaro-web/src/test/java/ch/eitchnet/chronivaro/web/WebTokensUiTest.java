package ch.eitchnet.chronivaro.web;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class WebTokensUiTest {

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
	public void shouldVerifyTokensViewComponent() throws IOException {
		File tokensViewFile = new File(getWebappDir(), "js/pages/TokensView.js");
		assertTrue("TokensView.js must exist", tokensViewFile.exists());
		String content = Files.readString(tokensViewFile.toPath());

		assertTrue("TokensView must import TokenApi", content.contains("import TokenApi from '../api/TokenApi.js'"));
		assertTrue("TokensView must import NotificationDialog", content.contains("import NotificationDialog from '../utils/NotificationDialog.js'"));
		assertTrue("TokensView must import Format", content.contains("import Format from '../utils/Format.js'"));
		assertTrue("TokensView must import I18n", content.contains("import I18n from '../i18n/I18n.js'"));
		assertTrue("TokensView must call getMyTokens", content.contains("TokenApi.getMyTokens()"));
		assertTrue("TokensView must call createMyToken", content.contains("TokenApi.createMyToken("));
		assertTrue("TokensView must call deleteMyToken", content.contains("TokenApi.deleteMyToken("));
		assertTrue("TokensView must render create modal", content.contains("create-token-modal"));
		assertTrue("TokensView must render secret display modal", content.contains("token-secret-modal"));
		assertTrue("TokensView must support presets", content.contains("DESKTOP_TIMER") && content.contains("READ_ONLY_TIMES") && content.contains("FULL_PERSONAL"));
		assertTrue("TokensView must support duration in days, months, years", content.contains("token-duration-value") && content.contains("token-duration-unit"));
		assertTrue("TokensView must support no-expiry checkbox", content.contains("token-no-expiry"));
		assertTrue("TokensView must support copy to clipboard", content.contains("navigator.clipboard.writeText"));
	}

	@Test
	public void shouldVerifyTokenApiMethods() throws IOException {
		File apiFile = new File(getWebappDir(), "js/api/TokenApi.js");
		assertTrue("TokenApi.js must exist", apiFile.exists());
		String content = Files.readString(apiFile.toPath());

		assertTrue("TokenApi must have getMyTokens", content.contains("getMyTokens()"));
		assertTrue("TokenApi must query rest/chronivaro/v1/me/tokens", content.contains("rest/chronivaro/v1/me/tokens"));
		assertTrue("TokenApi must have createMyToken", content.contains("createMyToken("));
		assertTrue("TokenApi must have deleteMyToken", content.contains("deleteMyToken("));
		assertTrue("TokenApi must have getUserTokens", content.contains("getUserTokens("));
		assertTrue("TokenApi must query admin user tokens endpoint", content.contains("rest/chronivaro/v1/admin/users/"));
		assertTrue("TokenApi must have deleteUserToken", content.contains("deleteUserToken("));
	}

	@Test
	public void shouldVerifyUsersViewTokenManagement() throws IOException {
		File usersViewFile = new File(getWebappDir(), "js/pages/UsersView.js");
		assertTrue("UsersView.js must exist", usersViewFile.exists());
		String content = Files.readString(usersViewFile.toPath());

		assertTrue("UsersView must import TokenApi", content.contains("import TokenApi from '../api/TokenApi.js'"));
		assertTrue("UsersView must have manageTokens button", content.contains("tokens.manageTokens") || content.contains("tokens-user-btn"));
		assertTrue("UsersView must have user tokens modal", content.contains("user-tokens-modal"));
		assertTrue("UsersView must query user tokens via TokenApi", content.contains("TokenApi.getUserTokens("));
		assertTrue("UsersView must support admin token revocation", content.contains("TokenApi.deleteUserToken("));
	}

	@Test
	public void shouldVerifyProfileViewTokensLink() throws IOException {
		File profileViewFile = new File(getWebappDir(), "js/pages/ProfileView.js");
		assertTrue("ProfileView.js must exist", profileViewFile.exists());
		String content = Files.readString(profileViewFile.toPath());

		assertTrue("ProfileView must link to tokens view", content.contains("href=\"#tokens\""));
		assertTrue("ProfileView must reference tokens translation", content.contains("tokens.manageTokens") || content.contains("tokens.title"));
	}
}
