package ch.atexxi.chronivaro.rest;

import ch.atexxi.chronivaro.rest.providers.ChronivaroAuthenticationRequestFilter;
import ch.atexxi.chronivaro.rest.providers.ChronivaroRestfulExceptionMapper;
import ch.atexxi.chronivaro.rest.providers.CorrelationIdFilter;
import ch.atexxi.chronivaro.rest.resource.*;
import li.strolch.rest.StrolchRestfulClasses;
import li.strolch.rest.StrolchRestfulExceptionMapper;
import li.strolch.rest.filters.AuthenticationRequestFilter;

import java.util.HashSet;
import java.util.Set;

public class ChronivaroRestfulClasses {

	public static Set<Class<?>> getRestfulClasses() {
		Set<Class<?>> restfulClasses = new HashSet<>(StrolchRestfulClasses.getRestfulClasses());
		restfulClasses.add(AbsenceResource.class);
		restfulClasses.add(AbsenceTypeResource.class);
		restfulClasses.add(ChronivaroResource.class);
		restfulClasses.add(EmployeeResource.class);
		restfulClasses.add(HolidayCalendarsResource.class);
		restfulClasses.add(LocationResource.class);
		restfulClasses.add(PeriodResource.class);
		restfulClasses.add(ScheduleTemplateResource.class);
		restfulClasses.add(TeamResource.class);
		return restfulClasses;
	}

	public static Set<Class<?>> getProviderClasses() {
		Set<Class<?>> providerClasses = new HashSet<>(StrolchRestfulClasses.getProviderClasses());
		providerClasses.remove(AuthenticationRequestFilter.class);
		providerClasses.remove(StrolchRestfulExceptionMapper.class);
		providerClasses.add(CorrelationIdFilter.class);
		providerClasses.add(ChronivaroAuthenticationRequestFilter.class);
		providerClasses.add(ChronivaroRestfulExceptionMapper.class);
		return providerClasses;
	}
}
