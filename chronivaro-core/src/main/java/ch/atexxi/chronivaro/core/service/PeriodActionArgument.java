package ch.atexxi.chronivaro.core.service;

import li.strolch.service.api.ServiceArgument;

import java.time.YearMonth;

public class PeriodActionArgument extends ServiceArgument {
	public String periodId;
	public String employeeId;
	public YearMonth yearMonth;
	public String comment;

	public PeriodActionArgument() {
	}

	public PeriodActionArgument(String periodId) {
		this.periodId = periodId;
	}

	public PeriodActionArgument(String periodId, String comment) {
		this.periodId = periodId;
		this.comment = comment;
	}

	public PeriodActionArgument(String employeeId, YearMonth yearMonth) {
		this.employeeId = employeeId;
		this.yearMonth = yearMonth;
	}

	public PeriodActionArgument(String employeeId, YearMonth yearMonth, String comment) {
		this.employeeId = employeeId;
		this.yearMonth = yearMonth;
		this.comment = comment;
	}
}
