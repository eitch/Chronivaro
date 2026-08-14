package ch.atexxi.chronivaro.rest.providers;

import li.strolch.rest.filters.AuthenticationRequestFilter;

import java.util.Set;

public class ChronivaroAuthenticationRequestFilter extends AuthenticationRequestFilter {

	@Override
	protected Set<String> getUnsecuredPaths() {
		Set<String> unsecuredPaths = super.getUnsecuredPaths();
		unsecuredPaths.add("chronivaro/v1/complete-registration");
		return unsecuredPaths;
	}
}
