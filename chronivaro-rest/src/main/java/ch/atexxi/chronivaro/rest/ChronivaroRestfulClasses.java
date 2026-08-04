package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.rest.resource.ChronivaroResource;
import li.strolch.rest.StrolchRestfulClasses;

import java.util.HashSet;
import java.util.Set;

public class ChronivaroRestfulClasses {

	public static Set<Class<?>> getRestfulClasses() {
		Set<Class<?>> restfulClasses = new HashSet<>(StrolchRestfulClasses.getRestfulClasses());
		restfulClasses.add(ChronivaroResource.class);
		return restfulClasses;
	}

	public static Set<Class<?>> getProviderClasses() {
		return StrolchRestfulClasses.getProviderClasses();
	}
}
