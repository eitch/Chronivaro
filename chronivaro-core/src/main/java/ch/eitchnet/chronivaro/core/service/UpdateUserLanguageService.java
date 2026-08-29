package ch.eitchnet.chronivaro.core.service;

import ch.eitchnet.chronivaro.core.model.ChronivaroAuditHelper;
import li.strolch.persistence.api.StrolchTransaction;
import li.strolch.privilege.handler.PrivilegeHandler;
import li.strolch.privilege.model.UserRep;
import li.strolch.service.api.AbstractService;
import li.strolch.service.api.ServiceArgument;
import li.strolch.service.api.ServiceResult;
import li.strolch.utils.dbc.DBC;

import java.util.Locale;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.AUDIT_ACTION_UPDATE;

public class UpdateUserLanguageService extends AbstractService<UpdateUserLanguageService.UpdateUserLanguageArgument, ServiceResult> {

	@Override
	protected ServiceResult internalDoService(UpdateUserLanguageArgument arg) throws Exception {
		DBC.PRE.assertNotEmpty("Language must not be empty", arg.language);

		String lang = arg.language.trim();
		Locale locale = Locale.forLanguageTag(lang);

		try (StrolchTransaction tx = openArgOrUserTx(arg)) {
			PrivilegeHandler privilegeHandler = tx.getContainer().getPrivilegeHandler().getPrivilegeHandler();
			UserRep user = privilegeHandler.setUserLocale(tx.getCertificate(), tx.getCertificate().getUsername(), locale);
			if (privilegeHandler.isPersistOnUserDataChanged())
				privilegeHandler.persist(tx.getCertificate());

			ChronivaroAuditHelper.audit(tx, "User", user.getUserId(), AUDIT_ACTION_UPDATE,
					"Updated user language for " + user.getUsername() + " to " + locale.toLanguageTag());

			tx.commitOnClose();
		}

		return ServiceResult.success();
	}

	@Override
	public UpdateUserLanguageArgument getArgumentInstance() {
		return new UpdateUserLanguageArgument();
	}

	@Override
	public ServiceResult getResultInstance() {
		return new ServiceResult();
	}

	public static class UpdateUserLanguageArgument extends ServiceArgument {
		public String language;
	}
}
