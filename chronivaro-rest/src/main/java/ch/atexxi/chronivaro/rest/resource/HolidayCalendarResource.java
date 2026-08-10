package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.service.CreateHolidayCalendarService;
import ch.atexxi.chronivaro.core.service.CreateHolidayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.rest.helper.ResponseUtil;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

@Path("chronivaro/v1/admin/holiday-calendars")
public class HolidayCalendarResource {

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createHolidayCalendar(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		CreateHolidayCalendarService.HolidayCalendarArgument arg = ChronivaroRestHelper
				.createGson()
				.fromJson(data, CreateHolidayCalendarService.HolidayCalendarArgument.class);
		ServiceResult result = serviceHandler.doService(cert, new CreateHolidayCalendarService(), arg);
		return ResponseUtil.toResponse(result);
	}

	@POST
	@Path("{id}/holidays")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createHoliday(@Context HttpServletRequest request, @PathParam("id") String calendarId,
			String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		CreateHolidayService.HolidayArgument arg = ChronivaroRestHelper
				.createGson()
				.fromJson(data, CreateHolidayService.HolidayArgument.class);
		arg.holidayCalendarId = calendarId;
		ServiceResult result = serviceHandler.doService(cert, new CreateHolidayService(), arg);
		return ResponseUtil.toResponse(result);
	}
}
