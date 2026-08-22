package ch.atexxi.chronivaro.rest.resource;

import ch.atexxi.chronivaro.core.service.CreateUserService;
import ch.atexxi.chronivaro.core.service.InitiateUserRegistrationService;
import ch.atexxi.chronivaro.core.service.UpdateUserService;
import ch.atexxi.chronivaro.rest.dto.UserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.base.PrivilegeConstants;
import li.strolch.privilege.handler.PrivilegeHandler;
import li.strolch.privilege.model.Certificate;
import li.strolch.privilege.model.UserRep;
import li.strolch.privilege.model.UserState;
import li.strolch.rest.StrolchRestfulConstants;
import li.strolch.service.StringArgument;
import li.strolch.service.StringResult;
import li.strolch.service.api.ServiceHandler;
import li.strolch.service.api.ServiceResult;

import java.util.List;
import java.util.function.Function;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

@Path("chronivaro/v1/admin/users")
public class UserResource {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getUsers(
			@Context HttpServletRequest request,
			@QueryParam("query") String query,
			@QueryParam("offset") Integer offset,
			@QueryParam("limit") Integer limit) {

		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			PrivilegeHandler privilegeHandler = tx.getContainer().getPrivilegeHandler().getPrivilegeHandler();
			List<UserRep> allUsers = privilegeHandler.getUsers(tx.getCertificate());

			List<Resource> employees = tx.streamResources(TYPE_EMPLOYEE).toList();

			List<UserDto> dtos = allUsers.stream()
					.filter(u -> u.getUserState() != UserState.SYSTEM)
					.filter(u -> filterUser(u, query))
					.map(u -> userToDto(u, employees))
					.toList();

			return PaginationHelper.toPagedOrListResponse(dtos, offset, limit, Function.identity());
		}
	}

	@GET
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getUser(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			PrivilegeHandler privilegeHandler = tx.getContainer().getPrivilegeHandler().getPrivilegeHandler();
			UserRep user = findUser(privilegeHandler, tx.getCertificate(), id);
			if (user == null) {
				return ChronivaroRestHelper.toErrorResponse(Response.Status.NOT_FOUND, "NOT_FOUND", "User " + id + " not found");
			}
			List<Resource> employees = tx.streamResources(TYPE_EMPLOYEE).toList();
			return Response.ok(ChronivaroRestHelper.createGson().toJson(userToDto(user, employees)), MediaType.APPLICATION_JSON).build();
		}
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response createUser(@Context HttpServletRequest request, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		UserDto dto = ChronivaroRestHelper.createGson().fromJson(data, UserDto.class);

		CreateUserService.UserArgument arg = new CreateUserService.UserArgument();
		arg.username = dto.username();
		arg.firstname = dto.firstname();
		arg.lastname = dto.lastname();
		arg.email = dto.email();
		arg.roles = dto.roles();
		if (dto.state() != null && !dto.state().isBlank()) {
			arg.state = UserState.valueOf(dto.state().toUpperCase());
		}
		arg.locale = dto.locale();

		StringResult result = serviceHandler.doService(cert, new CreateUserService(), arg);
		if (result.isNok()) {
			return ChronivaroRestHelper.toResponse(result);
		}

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			PrivilegeHandler privilegeHandler = tx.getContainer().getPrivilegeHandler().getPrivilegeHandler();
			UserRep user = findUser(privilegeHandler, tx.getCertificate(), result.getValue());
			List<Resource> employees = tx.streamResources(TYPE_EMPLOYEE).toList();
			return Response.status(Response.Status.CREATED)
					.entity(ChronivaroRestHelper.createGson().toJson(userToDto(user, employees)))
					.type(MediaType.APPLICATION_JSON)
					.build();
		}
	}

	@PUT
	@Path("{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateUser(@Context HttpServletRequest request, @PathParam("id") String id, String data) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		UserDto dto = ChronivaroRestHelper.createGson().fromJson(data, UserDto.class);

		UpdateUserService.UpdateUserArgument arg = new UpdateUserService.UpdateUserArgument();
		arg.userId = id;
		arg.firstname = dto.firstname();
		arg.lastname = dto.lastname();
		arg.email = dto.email();
		arg.roles = dto.roles();
		if (dto.state() != null && !dto.state().isBlank()) {
			arg.state = UserState.valueOf(dto.state().toUpperCase());
		}
		arg.locale = dto.locale();

		ServiceResult result = serviceHandler.doService(cert, new UpdateUserService(), arg);
		if (result.isNok()) {
			return ChronivaroRestHelper.toResponse(result);
		}

		try (StrolchTransaction tx = ChronivaroRestHelper.openTx(cert)) {
			PrivilegeHandler privilegeHandler = tx.getContainer().getPrivilegeHandler().getPrivilegeHandler();
			UserRep user = findUser(privilegeHandler, tx.getCertificate(), id);
			List<Resource> employees = tx.streamResources(TYPE_EMPLOYEE).toList();
			return Response.ok(ChronivaroRestHelper.createGson().toJson(userToDto(user, employees)), MediaType.APPLICATION_JSON).build();
		}
	}

	@POST
	@Path("{id}/register")
	@Produces(MediaType.APPLICATION_JSON)
	public Response initiateRegistration(@Context HttpServletRequest request, @PathParam("id") String id) {
		Certificate cert = (Certificate) request.getAttribute(StrolchRestfulConstants.STROLCH_CERTIFICATE);
		ServiceHandler serviceHandler = ChronivaroRestHelper.getServiceHandler();
		ServiceResult result = serviceHandler.doService(cert, new InitiateUserRegistrationService(), new StringArgument(id));
		return ChronivaroRestHelper.toResponse(result);
	}

	private static boolean filterUser(UserRep u, String query) {
		if (query == null || query.isBlank()) {
			return true;
		}
		String q = query.toLowerCase().trim();
		return (u.getUsername() != null && u.getUsername().toLowerCase().contains(q))
				|| (u.getFirstname() != null && u.getFirstname().toLowerCase().contains(q))
				|| (u.getLastname() != null && u.getLastname().toLowerCase().contains(q))
				|| (u.getProperty(PrivilegeConstants.EMAIL) != null && u.getProperty(PrivilegeConstants.EMAIL).toLowerCase().contains(q));
	}

	private static UserRep findUser(PrivilegeHandler privilegeHandler, Certificate cert, String id) {
		UserRep user = privilegeHandler.getUser(cert, id);
		if (user != null && user.getUserState() != UserState.SYSTEM) return user;
		for (UserRep u : privilegeHandler.getUsers(cert)) {
			if (u.getUserState() != UserState.SYSTEM && (id.equals(u.getUserId()) || id.equalsIgnoreCase(u.getUsername()))) {
				return u;
			}
		}
		return null;
	}

	private static UserDto userToDto(UserRep user, List<Resource> employees) {
		String email = user.getProperty(PrivilegeConstants.EMAIL);

		Resource emp = null;
		for (Resource e : employees) {
			String uName = e.getString(PARAM_USERNAME);
			String uId = e.getString(PARAM_USER_ID);
			if ((uName != null && uName.equalsIgnoreCase(user.getUsername())) || (uId != null && uId.equals(user.getUserId()))) {
				emp = e;
				break;
			}
		}

		boolean hasLinkedEmployee = emp != null;
		String employeeId = emp != null ? emp.getId() : null;
		String employeeName = emp != null ? emp.getName() : null;

		return new UserDto(
				user.getUserId(),
				user.getUsername(),
				user.getFirstname(),
				user.getLastname(),
				email,
				user.getRoles(),
				user.getUserState() != null ? user.getUserState().name() : "ENABLED",
				user.getLocale() != null ? user.getLocale().toLanguageTag() : "de",
				hasLinkedEmployee,
				employeeId,
				employeeName
		);
	}
}
