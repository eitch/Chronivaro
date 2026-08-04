package ch.atexxi.chronivaro.rest.resource;

import com.google.gson.*;
import li.strolch.agent.api.ComponentContainer;
import li.strolch.agent.api.StrolchAgent;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.model.Certificate;
import li.strolch.rest.RestfulStrolchComponent;
import li.strolch.service.api.ServiceHandler;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class ChronivaroRestHelper {

	public static Gson createGson() {
		return new GsonBuilder()
				.registerTypeAdapter(LocalDate.class, (JsonSerializer<LocalDate>) (src, typeOfSrc, context) -> new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE)))
				.registerTypeAdapter(LocalDate.class, (JsonDeserializer<LocalDate>) (json, typeOfT, context) -> LocalDate.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE))
				.registerTypeAdapter(YearMonth.class, (JsonSerializer<YearMonth>) (src, typeOfSrc, context) -> new JsonPrimitive(src.toString()))
				.registerTypeAdapter(YearMonth.class, (JsonDeserializer<YearMonth>) (json, typeOfT, context) -> YearMonth.parse(json.getAsString()))
				.registerTypeAdapter(ZonedDateTime.class, (JsonSerializer<ZonedDateTime>) (src, typeOfSrc, context) -> new JsonPrimitive(src.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
				.registerTypeAdapter(ZonedDateTime.class, (JsonDeserializer<ZonedDateTime>) (json, typeOfT, context) -> ZonedDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME))
				.create();
	}

	public static StrolchTransaction openTx(Certificate cert) {
		return RestfulStrolchComponent.getInstance().openTx(cert, ChronivaroRestHelper.class, false);
	}

	public static ServiceHandler getServiceHandler() {
		return RestfulStrolchComponent.getInstance().getComponent(ServiceHandler.class);
	}
}
