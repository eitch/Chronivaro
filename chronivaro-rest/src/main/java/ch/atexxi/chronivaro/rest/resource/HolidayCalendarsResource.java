package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.service.CreateHolidayCalendarService;
import ch.atexxi.chronivaro.core.service.CreateHolidayService;
import ch.atexxi.chronivaro.core.service.RemoveHolidayCalendarService;
import ch.atexxi.chronivaro.core.service.RemoveHolidayService;
import ch.atexxi.chronivaro.rest.dto.ChronivaroMapper;
import ch.atexxi.chronivaro.rest.dto.HolidayCalendarDto;
import ch.atexxi.chronivaro.rest.dto.HolidayDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.helper.ResponseUtil;
import li.strolch.service.StringArgument;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

import java.util.List;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;
import static li.strolch.model.StrolchModelConstants.BAG_RELATIONS;
import static li.strolch.rest.StrolchRestfulConstants.STROLCH_CERTIFICATE;

@Path("chronivaro/v1/admin/holiday-calendars")
public class HolidayCalendarsResource {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getHolidayCalendars(@Context HttpServletRequest request) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> calendars = tx.streamResources(TYPE_HOLIDAY_CALENDAR).toList();
			List<HolidayCalendarDto> dtos = calendars.stream().map(ChronivaroMapper::holidayCalendarToDto).toList();
			return Response.ok(ChronivaroRestHelper.createGson().toJson(dtos), MediaType.APPLICATION_JSON).build();
		}
	}

	@GET
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getHolidayCalendar(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource calendar = tx.getResourceBy(TYPE_HOLIDAY_CALENDAR, id, true);
			HolidayCalendarDto dto = ChronivaroMapper.holidayCalendarToDto(calendar);
			return Response.ok(ChronivaroRestHelper.createGson().toJson(dto), MediaType.APPLICATION_JSON).build();
		}
	}

	@GET
	@Path("{id}/holidays")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getHolidays(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			List<Resource> holidays = tx
					.streamResources(TYPE_HOLIDAY)
					.filter(h -> h.getString(BAG_RELATIONS, PARAM_HOLIDAY_CALENDAR).equals(id))
					.toList();
			List<HolidayDto> dtos = holidays.stream().map(ChronivaroMapper::holidayToDto).toList();
			return Response.ok(ChronivaroRestHelper.createGson().toJson(dtos), MediaType.APPLICATION_JSON).build();
		}
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createHolidayCalendar(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		CreateHolidayCalendarService.HolidayCalendarArgument arg = ChronivaroRestHelper
				.createGson()
				.fromJson(data, CreateHolidayCalendarService.HolidayCalendarArgument.class);
		CreateHolidayCalendarService service = new CreateHolidayCalendarService();
		ServiceResult result = serviceHandler.doService(cert, service, arg);
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource calendar = tx
						.streamResources(TYPE_HOLIDAY_CALENDAR)
						.filter(r -> r
								.getString(ch.atexxi.chronivaro.core.model.ChronivaroConstants.PARAM_NAME)
								.equals(arg.name))
						.findFirst()
						.orElse(null);
				if (calendar != null)
					return ResponseUtil.toResponse(li.strolch.model.Tags.Json.VALUE, calendar.getId());
			}
		}
		return ResponseUtil.toResponse(result);
	}

	@POST
	@Path("{id}/holidays")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createHoliday(@Context HttpServletRequest request, @PathParam("id") String calendarId,
			String data) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		CreateHolidayService.HolidayArgument arg = ChronivaroRestHelper
				.createGson()
				.fromJson(data, CreateHolidayService.HolidayArgument.class);
		arg.holidayCalendarId = calendarId;
		ServiceResult result = serviceHandler.doService(cert, new CreateHolidayService(), arg);
		return ResponseUtil.toResponse(result);
	}

	@DELETE
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response removeHolidayCalendar(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new RemoveHolidayCalendarService(),
				new StringArgument(id));
		return ResponseUtil.toResponse(result);
	}

	@DELETE
	@Path("{calendarId}/holidays/{holidayId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response removeHoliday(@Context HttpServletRequest request, @PathParam("calendarId") String calendarId,
			@PathParam("holidayId") String holidayId) {
		Certificate cert = (Certificate) request.getAttribute(STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new RemoveHolidayService(),
				new StringArgument(holidayId));
		return ResponseUtil.toResponse(result);
	}
}
