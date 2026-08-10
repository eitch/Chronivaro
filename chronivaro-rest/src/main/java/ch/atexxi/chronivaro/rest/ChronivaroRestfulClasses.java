package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.rest.resource.*;
import li.strolch.rest.StrolchRestfulClasses;

import java.util.HashSet;
import java.util.Set;

public class ChronivaroRestfulClasses {

	public static Set<Class<?>> getRestfulClasses() {
		Set<Class<?>> restfulClasses = new HashSet<>(StrolchRestfulClasses.getRestfulClasses());
		restfulClasses.add(ChronivaroResource.class);
		restfulClasses.add(EmployeeResource.class);
		restfulClasses.add(TeamResource.class);
		restfulClasses.add(LocationResource.class);
		restfulClasses.add(AbsenceTypeResource.class);
		restfulClasses.add(AbsenceResource.class);
		restfulClasses.add(HolidayCalendarsResource.class);
		restfulClasses.add(PeriodResource.class);
		return restfulClasses;
	}

	public static Set<Class<?>> getProviderClasses() {
		return StrolchRestfulClasses.getProviderClasses();
	}
}
