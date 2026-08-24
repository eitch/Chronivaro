package ch.atexxi.chronivaro.web;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class WebConfigurationUiTest {

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
	public void shouldVerifyConfigurationApiMethods() throws IOException {
		File apiFile = new File(getWebappDir(), "js/api/ConfigurationApi.js");
		assertTrue("ConfigurationApi.js must exist", apiFile.exists());
		String apiJs = Files.readString(apiFile.toPath());

		assertTrue("ConfigurationApi must define getBranding", apiJs.contains("getBranding()"));
		assertTrue("ConfigurationApi must define getConfiguration", apiJs.contains("getConfiguration()"));
		assertTrue("ConfigurationApi must define updateConfiguration", apiJs.contains("updateConfiguration("));
		assertTrue("ConfigurationApi must define uploadLogo", apiJs.contains("uploadLogo("));
		assertTrue("ConfigurationApi must define deleteLogo", apiJs.contains("deleteLogo()"));
	}

	@Test
	public void shouldVerifyConfigurationViewComponentsAndLogoUpload() throws IOException {
		File viewFile = new File(getWebappDir(), "js/pages/ConfigurationView.js");
		assertTrue("ConfigurationView.js must exist", viewFile.exists());
		String viewJs = Files.readString(viewFile.toPath());

		// Verify elements
		assertTrue("ConfigurationView must have configuration-view container", viewJs.contains("id = 'configuration-view'") || viewJs.contains("id=\"configuration-view\""));
		assertTrue("ConfigurationView must have view-header with subtitle below title", viewJs.contains("class=\"view-header\"") && viewJs.contains("class=\"subtitle\""));
		assertTrue("ConfigurationView must have configuration-container", viewJs.contains("class=\"configuration-container\""));
		assertTrue("ConfigurationView must have config-company-name input", viewJs.contains("id=\"config-company-name\""));
		assertTrue("ConfigurationView must have config-logo-file input", viewJs.contains("id=\"config-logo-file\""));
		assertTrue("ConfigurationView must have config-logo-upload-btn", viewJs.contains("id=\"config-logo-upload-btn\""));
		assertTrue("ConfigurationView must have config-logo-remove-btn", viewJs.contains("id=\"config-logo-remove-btn\""));
		assertTrue("ConfigurationView must have config-company-logo input", viewJs.contains("id=\"config-company-logo\""));
		assertTrue("ConfigurationView must have config-logo-preview", viewJs.contains("id=\"config-logo-preview\""));
		assertTrue("ConfigurationView must have config-logo-preview-box", viewJs.contains("id=\"config-logo-preview-box\""));

		// Verify upload handling logic
		assertTrue("ConfigurationView must handle file selection", viewJs.contains("handleFileSelection"));
		assertTrue("ConfigurationView must handle remove logo", viewJs.contains("handleRemoveLogo"));
		assertTrue("ConfigurationView must validate file size (5MB)", viewJs.contains("5 * 1024 * 1024"));
		assertTrue("ConfigurationView must support FileReader for data URL generation", viewJs.contains("new FileReader()"));
		assertTrue("ConfigurationView must update preview on logo changes", viewJs.contains("updateLogoPreview()"));
	}

	@Test
	public void shouldVerifyConfigurationLayoutAndCss() throws IOException {
		File cssFile = new File(getWebappDir(), "assets/css/style.css");
		assertTrue("style.css must exist", cssFile.exists());
		String css = Files.readString(cssFile.toPath());

		// Verify centered container
		assertTrue("style.css must center .configuration-container", css.contains(".configuration-container") && css.contains("margin: 1.5rem auto"));

		// Verify centered column header with subtitle below title
		assertTrue("style.css must format #configuration-view .view-header as column", css.contains("#configuration-view .view-header") && css.contains("flex-direction: column"));

		// Verify logo upload controls and preview styling
		assertTrue("style.css must style .logo-upload-controls", css.contains(".logo-upload-controls"));
		assertTrue("style.css must style .config-logo-preview", css.contains(".config-logo-preview"));
	}
}
