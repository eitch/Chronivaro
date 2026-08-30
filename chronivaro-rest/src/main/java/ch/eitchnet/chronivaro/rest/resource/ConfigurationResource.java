package ch.eitchnet.chronivaro.rest.resource;

import ch.eitchnet.chronivaro.core.service.UpdateConfigurationService;
import ch.eitchnet.chronivaro.rest.dto.ChronivaroMapper;
import ch.eitchnet.chronivaro.rest.dto.ConfigurationDto;
import com.google.gson.JsonObject;
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.PARAM_COMPANY_LOGO;
import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.TYPE_GLOBAL_CONFIGURATION;

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

	@GET
	@Path("logo")
	@Produces({"image/png", "image/jpeg", "image/svg+xml", "image/gif", "image/webp", "image/x-icon", "*/*"})
	public Response getLogo(@Context HttpServletRequest request) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", true);
			if (config.hasParameter(PARAM_COMPANY_LOGO)) {
				String logo = config.getString(PARAM_COMPANY_LOGO);
				if (logo != null && !logo.isBlank()) {
					String trimmed = logo.trim();
					if (trimmed.startsWith("data:")) {
						int semicolonIdx = trimmed.indexOf(";base64,");
						if (semicolonIdx > 5) {
							String mimeType = trimmed.substring(5, semicolonIdx).trim();
							String base64Payload = trimmed.substring(semicolonIdx + 8);
							byte[] imageBytes = Base64.getDecoder().decode(base64Payload);
							return Response.ok(imageBytes, mimeType)
									.header("Cache-Control", "public, max-age=3600")
									.build();
						}
					} else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
						return Response.temporaryRedirect(URI.create(trimmed)).build();
					}
				}
			}
		}
		return Response.status(Response.Status.NOT_FOUND).build();
	}

	@POST
	@Path("logo")
	@Consumes({MediaType.APPLICATION_JSON, "image/png", "image/jpeg", "image/svg+xml", "image/gif", "image/webp", MediaType.TEXT_PLAIN, "*/*"})
	@Produces(MediaType.APPLICATION_JSON)
	public Response uploadLogo(@Context HttpServletRequest request, byte[] bodyBytes, @HeaderParam("Content-Type") String contentType) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		String logoDataUri;

		if (bodyBytes == null || bodyBytes.length == 0) {
			logoDataUri = "";
		} else if (contentType != null && contentType.startsWith(MediaType.APPLICATION_JSON)) {
			String json = new String(bodyBytes, StandardCharsets.UTF_8);
			JsonObject obj = ChronivaroRestHelper.createGson().fromJson(json, JsonObject.class);
			if (obj.has("companyLogo")) {
				logoDataUri = obj.get("companyLogo").getAsString();
			} else if (obj.has("logo")) {
				logoDataUri = obj.get("logo").getAsString();
			} else if (obj.has("data")) {
				logoDataUri = obj.get("data").getAsString();
			} else {
				logoDataUri = "";
			}
		} else if (contentType != null && (contentType.startsWith("image/") || contentType.startsWith("text/plain"))) {
			String mime = contentType.split(";")[0].trim();
			if (contentType.startsWith("text/plain")) {
				logoDataUri = new String(bodyBytes, StandardCharsets.UTF_8).trim();
			} else {
				logoDataUri = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bodyBytes);
			}
		} else {
			logoDataUri = new String(bodyBytes, StandardCharsets.UTF_8).trim();
		}

		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		UpdateConfigurationService.UpdateConfigurationArgument arg = new UpdateConfigurationService.UpdateConfigurationArgument();
		arg.companyLogo = logoDataUri;

		ServiceResult result = serviceHandler.doService(cert, new UpdateConfigurationService(), arg);
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", true);
				return ConcurrencyHelper.toResponseWithETag(config, ChronivaroMapper.configurationToDto(config));
			}
		}

		return ChronivaroRestHelper.toResponse(result);
	}

	@DELETE
	@Path("logo")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deleteLogo(@Context HttpServletRequest request) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();

		UpdateConfigurationService.UpdateConfigurationArgument arg = new UpdateConfigurationService.UpdateConfigurationArgument();
		arg.companyLogo = "";

		ServiceResult result = serviceHandler.doService(cert, new UpdateConfigurationService(), arg);
		if (result.isOk()) {
			try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
				Resource config = tx.getResourceBy(TYPE_GLOBAL_CONFIGURATION, "configuration", true);
				return ConcurrencyHelper.toResponseWithETag(config, ChronivaroMapper.configurationToDto(config));
			}
		}

		return ChronivaroRestHelper.toResponse(result);
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
		arg.companyName = dto.companyName();
		arg.companyLogo = dto.companyLogo();
		arg.defaultLanguage = dto.defaultLanguage();
		arg.serverBaseUrl = dto.serverBaseUrl();
		arg.officeHoursStart = dto.officeHoursStart();
		arg.officeHoursEnd = dto.officeHoursEnd();

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
