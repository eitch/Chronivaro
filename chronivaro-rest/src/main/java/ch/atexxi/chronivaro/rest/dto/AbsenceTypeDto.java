package ch.atexxi.chronivaro.rest.dto;

import java.util.List;

public record AbsenceTypeDto(String id, String code, String name, boolean countAsTargetTime,
                             boolean reduceVacationCredit, boolean paid, boolean approvalRequired,
                             List<String> durationTypes, boolean active) {
}
