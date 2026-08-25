package ch.eitchnet.chronivaro.web;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebNavigationUiTest {

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
	public void shouldVerifyAppJsNavDropdownClosingLogic() throws IOException {
		File appJsFile = new File(getWebappDir(), "js/app.js");
		assertTrue("app.js must exist", appJsFile.exists());
		String appJs = Files.readString(appJsFile.toPath());

		// Verify dropdown closes on menu item selection
		assertTrue("app.js must have closeNavGroups method", appJs.contains("closeNavGroups()"));
		assertTrue("app.js must close nav groups on link click", appJs.contains("this.closeNavGroups()"));
		assertTrue("app.js must close nav groups when clicking outside", appJs.contains("if (this.nav && !e.target.closest('.nav-group'))"));
		assertTrue("app.js must close nav groups in route()", appJs.contains("this.closeNavGroups();"));
		assertTrue("app.js must coordinate single open dropdown", appJs.contains("other.removeAttribute('open')"));
	}

	@Test
	public void shouldVerifyNavigationCssDropdownStyling() throws IOException {
		File cssFile = new File(getWebappDir(), "assets/css/style.css");
		assertTrue("style.css must exist", cssFile.exists());
		String css = Files.readString(cssFile.toPath());

		// Verify nav-group dropdown styling prevents overlapping of highlighted items
		assertTrue("style.css must define .nav-group ul flex column or block layout",
				css.contains(".nav-group ul") && css.contains("flex-direction: column"));
		assertTrue("style.css must define .nav-group li a with display: block",
				css.contains(".nav-group li a") && css.contains("display: block"));
		assertTrue("style.css must define .nav-link with display: inline-block or block",
				css.contains(".nav-link") && css.contains("display: inline-block"));
	}

	@Test
	public void shouldVerifyTabButtonsHoverAndActiveStylingMatchesMainMenu() throws IOException {
		File cssFile = new File(getWebappDir(), "assets/css/style.css");
		assertTrue("style.css must exist", cssFile.exists());
		String css = Files.readString(cssFile.toPath());

		// Verify tab-btn hover highlight matches main menu look and feel (#f1f5f9 background and text color)
		assertTrue("style.css must define .tab-btn:hover with background-color #f1f5f9",
				css.contains(".tab-btn:hover") && css.contains("background-color: #f1f5f9;"));
		assertTrue("style.css must define .tab-btn.active with background-color #eef2ff",
				css.contains(".tab-btn.active") && css.contains("background-color: #eef2ff;"));
		assertTrue("style.css must define .tab-btn with border-radius 0.375rem",
				css.contains(".tab-btn") && css.contains("border-radius: 0.375rem;"));
	}

	@Test
	public void shouldVerifyHeaderBrandingNavigatesToDashboard() throws IOException {
		File htmlFile = new File(getWebappDir(), "index.html");
		assertTrue("index.html must exist", htmlFile.exists());
		String html = Files.readString(htmlFile.toPath());

		assertTrue("header branding must be an anchor link to #dashboard",
				html.contains("<a href=\"#dashboard\" class=\"header-branding\" id=\"header-branding\">"));
		assertTrue("header branding must contain chronivaro-logo-light.svg",
				html.contains("src=\"assets/icons/chronivaro-logo-light.svg\""));

		File logoFile = new File(getWebappDir(), "assets/icons/chronivaro-logo-light.svg");
		assertTrue("chronivaro-logo-light.svg must exist", logoFile.exists());

		File cssFile = new File(getWebappDir(), "assets/css/style.css");
		assertTrue("style.css must exist", cssFile.exists());
		String css = Files.readString(cssFile.toPath());

		assertTrue("style.css must style .header-branding as clickable link",
				css.contains(".header-branding") && css.contains("cursor: pointer;"));
		assertTrue("style.css must remove text decoration for .header-branding",
				css.contains(".header-branding") && css.contains("text-decoration: none;"));

		File appJsFile = new File(getWebappDir(), "js/app.js");
		assertTrue("app.js must exist", appJsFile.exists());
		String appJs = Files.readString(appJsFile.toPath());

		assertTrue("app.js must handle header-branding click",
				appJs.contains("headerBranding.addEventListener('click'"));
	}

	@Test
	public void shouldVerifyLoggedInUserDropdownInHeader() throws IOException {
		File htmlFile = new File(getWebappDir(), "index.html");
		assertTrue("index.html must exist", htmlFile.exists());
		String html = Files.readString(htmlFile.toPath());

		assertTrue("index.html must contain user-menu element", html.contains("id=\"user-menu\""));
		assertTrue("index.html must display username in header", html.contains("id=\"header-username\""));
		assertTrue("index.html must display user full name in dropdown", html.contains("id=\"user-dropdown-fullname\""));
		assertTrue("index.html must display roles in dropdown", html.contains("id=\"user-dropdown-roles\""));

		File cssFile = new File(getWebappDir(), "assets/css/style.css");
		assertTrue("style.css must exist", cssFile.exists());
		String css = Files.readString(cssFile.toPath());

		assertTrue("style.css must style .user-menu", css.contains(".user-menu"));
		assertTrue("style.css must style .user-dropdown-panel", css.contains(".user-dropdown-panel"));
		assertTrue("style.css must style .user-dropdown-role-badge", css.contains(".user-dropdown-role-badge"));

		File authApiFile = new File(getWebappDir(), "js/api/AuthApi.js");
		assertTrue("AuthApi.js must exist", authApiFile.exists());
		String authApiJs = Files.readString(authApiFile.toPath());

		assertTrue("AuthApi.js must store username", authApiJs.contains("localStorage.setItem('username'"));
		assertTrue("AuthApi.js must store firstname", authApiJs.contains("localStorage.setItem('firstname'"));
		assertTrue("AuthApi.js must store lastname", authApiJs.contains("localStorage.setItem('lastname'"));
		assertTrue("AuthApi.js must provide getUsername", authApiJs.contains("getUsername()"));

		File appJsFile = new File(getWebappDir(), "js/app.js");
		assertTrue("app.js must exist", appJsFile.exists());
		String appJs = Files.readString(appJsFile.toPath());

		assertTrue("app.js must have updateUserMenu method", appJs.contains("updateUserMenu()"));
		assertTrue("app.js must hide ModelAccessor role", appJs.contains("role !== 'ModelAccessor'"));
	}

	@Test
	public void shouldVerifyLogoutMovedToUserDropdown() throws IOException {
		File htmlFile = new File(getWebappDir(), "index.html");
		assertTrue("index.html must exist", htmlFile.exists());
		String html = Files.readString(htmlFile.toPath());

		// Verify logout button is inside user-dropdown-actions and not a standalone link outside user-menu
		assertTrue("index.html must contain logout-btn inside user dropdown",
				html.contains("id=\"logout-btn\"") && html.contains("user-dropdown-logout-btn"));
		assertFalse("index.html must not contain standalone logout-link in main header",
				html.contains("id=\"logout-link\""));

		File cssFile = new File(getWebappDir(), "assets/css/style.css");
		assertTrue("style.css must exist", cssFile.exists());
		String css = Files.readString(cssFile.toPath());

		assertTrue("style.css must style .user-dropdown-logout-btn",
				css.contains(".user-dropdown-logout-btn"));

		File appJsFile = new File(getWebappDir(), "js/app.js");
		assertTrue("app.js must exist", appJsFile.exists());
		String appJs = Files.readString(appJsFile.toPath());

		assertTrue("app.js must handle logout-btn click",
				appJs.contains("document.getElementById('logout-btn')"));
		assertTrue("app.js must call AuthApi.logout()",
				appJs.contains("AuthApi.logout()"));
		assertTrue("app.js must close nav groups on logout",
				appJs.contains("this.closeNavGroups();") && appJs.contains("AuthApi.logout();"));
	}
}
