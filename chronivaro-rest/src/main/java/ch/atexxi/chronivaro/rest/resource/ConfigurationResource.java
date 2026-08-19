package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.service.UpdateConfigurationService;
import ch.atexxi.chronivaro.rest.dto.ChronivaroMapper;
import ch.atexxi.chronivaro.rest.dto.ConfigurationDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.TYPE_GLOBAL_CONFIGURATION;

@Path("chronivaro/v1/admin/configuration")
public class ConfigurationResource {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getConfiguration(@Context HttpServletRequest request) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", true);
			return ConcurrencyHelper.toResponseWithETag(config, ChronivaroMapper.configurationToDto(config));
		}
	}

	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateConfiguration(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", true);
			ConcurrencyHelper.validateIfMatch(request, config);
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ConfigurationDto dto = ChronivaroRestHelper.createGson().fromJson(data, ConfigurationDto.class);

		UpdateConfigurationService.UpdateConfigurationArgument arg = new UpdateConfigurationService.UpdateConfigurationArgument();
		arg.weeklyTargetMinutes = dto.weeklyTargetMinutes();
		arg.annualVacationDays = dto.annualVacationDays();
		arg.minutesPerVacationDay = dto.minutesPerVacationDay();
		arg.vacationAbsenceTypeCode = dto.vacationAbsenceTypeCode();

		ServiceResult result = serviceHandler.doService(cert, new UpdateConfigurationService(), arg);
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", true);
				return ConcurrencyHelper.toResponseWithETag(config, ChronivaroMapper.configurationToDto(config));
			}
		}

		return ChronivaroRestHelper.toResponse(result);
	}
}
