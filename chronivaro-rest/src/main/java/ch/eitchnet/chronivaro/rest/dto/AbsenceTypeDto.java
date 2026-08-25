package ch.eitchnet.chronivaro.rest.dto;

import java.util.List;

public record AbsenceTypeDto(String id, String code, String name, boolean countAsTargetTime,
                             boolean reduceVacationCredit, boolean paid, boolean approvalRequired,
                             boolean commentRequired, boolean visibleOnPublicStatus,
                             List<String> durationTypes, boolean active) {
}
