package ch.eitchnet.chronivaro.core.model;

import li.strolch.model.StrolchRootElement;
import li.strolch.model.Version;
import li.strolch.persistence.api.StrolchTransaction;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

public final class ChronivaroVersionHelper {

	private ChronivaroVersionHelper() {
	}

	public static int getVersion(StrolchRootElement element) {
		if (element == null)
			return 0;
		if (element.hasParameter(BAG_PARAMETERS, PARAM_VERSION))
			return element.getInteger(BAG_PARAMETERS, PARAM_VERSION);
		if (element.hasVersion())
			return element.getVersion().getVersion();
		return 0;
	}

	public static void initVersion(StrolchRootElement element, StrolchTransaction tx) {
		if (element == null)
			return;
		String username = tx != null && tx.getCertificate() != null ? tx.getCertificate().getUsername() : "system";
		element.setInteger(PARAM_VERSION, 0);
		element.setString(PARAM_UPDATED_BY, username);
		if (!element.hasVersion()) {
			Version.setInitialVersionFor(element, username);
		}
	}

	public static void bumpVersion(StrolchRootElement element, StrolchTransaction tx) {
		if (element == null)
			return;
		String username = tx != null && tx.getCertificate() != null ? tx.getCertificate().getUsername() : "system";
		int nextVersion = getVersion(element) + 1;
		element.setInteger(PARAM_VERSION, nextVersion);
		element.setString(PARAM_UPDATED_BY, username);
		if (!element.hasVersion()) {
			Version.setInitialVersionFor(element, username);
		}
		Version.updateVersionFor(element, username, false);
	}
}
