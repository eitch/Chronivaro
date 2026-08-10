package ch.atexxi.chronivaro.core.service;

import ch.atexxi.chronivaro.core.model.AbsenceHelper;
import li.strolch.model.ParameterBag;
import li.strolch.model.Resource;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.time.ZonedDateTime;
import java.util.UUID;

import static ch.atexxi.chronivaro.core.model.ChronivaroConstants.*;

public class RequestAbsenceService
		extends AbstractService<RequestAbsenceService.RequestAbsenceArgument, ServiceResult> {

	public static class RequestAbsenceArgument extends ServiceArgument {
		public String employeeId;
		public String absenceTypeCode;
		public ZonedDateTime start;
		public ZonedDateTime end;
		public String durationType;
		public String dayPart;
		public int minutes;
		public String comment;
	}

	@Override
	protected ServiceResult internalDoService(RequestAbsenceArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("employeeId must be set", arg.employeeId);
		DBC.PRE.assertNotEmpty("absenceTypeCode must be set", arg.absenceTypeCode);
		DBC.PRE.assertNotNull("start must be set", arg.start);
		DBC.PRE.assertNotNull("end must be set", arg.end);
		DBC.PRE.assertNotEmpty("durationType must be set", arg.durationType);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			Resource absenceType = AbsenceHelper.getAbsenceType(tx, arg.absenceTypeCode);

			Resource absence = new Resource(UUID.randomUUID().toString(), "Absence " + arg.start, TYPE_ABSENCE);
			absence.addParameterBag(new ParameterBag(BAG_PARAMETERS, "Parameters", "Parameters"));
			absence.addParameterBag(new ParameterBag(BAG_RELATIONS, "Relations", "Relations"));

			absence.setString(BAG_RELATIONS, TYPE_EMPLOYEE, arg.employeeId);
			absence.setString(BAG_RELATIONS, TYPE_ABSENCE_TYPE, absenceType.getId());
			absence.setDate(PARAM_START, arg.start);
			absence.setDate(PARAM_END, arg.end);
			absence.setString(PARAM_DURATION_TYPE, arg.durationType);
			if (arg.dayPart != null)
				absence.setString(PARAM_DAY_PART, arg.dayPart);
			if (arg.minutes > 0)
				absence.setInteger(PARAM_MINUTES, arg.minutes);
			if (arg.comment != null)
				absence.setString(PARAM_COMMENT, arg.comment);
			absence.setString(PARAM_STATE, STATE_SUBMITTED);

			tx.add(absence);
			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public RequestAbsenceArgument getArgumentInstance() {
		return new RequestAbsenceArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}
}
