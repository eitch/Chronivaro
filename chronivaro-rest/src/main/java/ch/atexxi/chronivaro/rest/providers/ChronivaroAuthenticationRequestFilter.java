package ch.atexxi.chronivaro.rest.providers;

import li.strolch.rest.filters.AuthenticationRequestFilter;

import java.util.Set;

public class ChronivaroAuthenticationRequestFilter extends AuthenticationRequestFilter {

	@Override
	protected Set<String> getUnsecuredPaths() {
		Set<String> unsecuredPaths = super.getUnsecuredPaths();
		unsecuredPaths.add("chronivaro/v1/complete-registration");
		unsecuredPaths.add("chronivaro/v1/version");
		unsecuredPaths.add("chronivaro/v1/branding");
		unsecuredPaths.add("chronivaro/v1/system/branding");
		unsecuredPaths.add("chronivaro/v1/system/version");
		unsecuredPaths.add("chronivaro/v1/system/health");
		unsecuredPaths.add("chronivaro/v1/system/readiness");
		unsecuredPaths.add("chronivaro/v1/system/metrics");
		return unsecuredPaths;
	}
}
