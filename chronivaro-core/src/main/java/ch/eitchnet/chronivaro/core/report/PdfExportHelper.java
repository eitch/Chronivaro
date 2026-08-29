package ch.eitchnet.chronivaro.core.report;

import ch.eitchnet.chronivaro.core.model.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import li.strolch.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import static ch.eitchnet.chronivaro.core.model.ChronivaroConstants.*;

/**
 * Server-side native PDF report generator for Chronivaro.
 * Produces deterministic, A4-formatted PDF documents with embedded fonts, company branding,
 * localization (DE/EN), employee metadata, and accessible monochrome-compatible negative value indicators.
 */
public class PdfExportHelper {

	private static final Logger logger = LoggerFactory.getLogger(PdfExportHelper.class);

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	// Palette & Fonts
	private static final Color COLOR_PRIMARY = new Color(15, 23, 42);       // #0F172A Dark Slate
	private static final Color COLOR_SECONDARY = new Color(71, 85, 105);   // #475569 Slate Grey
	private static final Color COLOR_HEADER_BG = new Color(241, 245, 249); // #F1F5F9 Light Grey
	private static final Color COLOR_ALT_ROW = new Color(248, 250, 252);   // #F8FAFC Very Light Grey
	private static final Color COLOR_BORDER = new Color(203, 213, 225);    // #CBD5E1 Border Slate
	private static final Color COLOR_TEXT = new Color(30, 41, 59);         // #1E293B Text
	private static final Color COLOR_NEGATIVE = new Color(185, 28, 28);    // #B91C1C Dark Red
	private static final Color COLOR_POSITIVE = new Color(21, 128, 61);    // #15803D Dark Green
	private static final Color COLOR_KPI_BG = new Color(243, 244, 246);      // #F3F4F6 Card BG

	private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, COLOR_PRIMARY);
	private static final Font FONT_SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_SECONDARY);
	private static final Font FONT_SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_PRIMARY);
	private static final Font FONT_TH = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, COLOR_PRIMARY);
	private static final Font FONT_TD = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, COLOR_TEXT);
	private static final Font FONT_TD_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, COLOR_TEXT);
	private static final Font FONT_TD_NEG = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, COLOR_NEGATIVE);
	private static final Font FONT_TD_POS = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, COLOR_POSITIVE);
	private static final Font FONT_META_LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, COLOR_SECONDARY);
	private static final Font FONT_META_VAL = FontFactory.getFont(FontFactory.HELVETICA, 8, COLOR_TEXT);
	private static final Font FONT_KPI_NUM = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_PRIMARY);
	private static final Font FONT_KPI_NUM_NEG = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_NEGATIVE);
	private static final Font FONT_FOOTER = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, COLOR_SECONDARY);

	public static String formatDuration(int minutes) {
		int hours = Math.abs(minutes) / 60;
		int remainingMinutes = Math.abs(minutes) % 60;
		String prefix = minutes < 0 ? "-" : "";
		return String.format("%s%02d:%02d", prefix, hours, remainingMinutes);
	}

	public static String getMonthReportPdfFileName(String employeeId, YearMonth yearMonth) {
		String emp = employeeId != null && !employeeId.isBlank() ? employeeId : "all";
		return "month-report-" + emp + "-" + yearMonth + ".pdf";
	}

	public static String getVacationReportPdfFileName(String employeeId, int year) {
		String emp = employeeId != null && !employeeId.isBlank() ? employeeId : "all";
		return "vacation-report-" + emp + "-" + year + ".pdf";
	}

	public static String getAbsenceReportPdfFileName(String context, LocalDate from, LocalDate to) {
		String ctx = context != null && !context.isBlank() ? context : "summary";
		String range = (from != null ? from.toString() : "start") + "_" + (to != null ? to.toString() : "end");
		return "absence-report-" + ctx + "-" + range + ".pdf";
	}

	// -------------------------------------------------------------------------
	// 1. Month Report PDF
	// -------------------------------------------------------------------------

	public static byte[] exportMonthReportToPdf(MonthSummary summary,
												String periodState,
												ZonedDateTime approvalDate,
												String approvedBy,
												Resource employee,
												Resource companyConfig,
												String language) {
		I18nTexts i18n = getI18n(language);
		String companyName = resolveCompanyName(companyConfig);
		String companyLogo = resolveCompanyLogo(companyConfig);

		String employeeId = employee != null ? employee.getId() : (summary != null ? summary.employeeId() : "");
		String employeeName = employee != null ? employee.getName() : "";
		String persNr = employee != null && employee.hasParameter(PARAM_PERSONAL_NUMBER) ? employee.getString(PARAM_PERSONAL_NUMBER) : "";
		String team = employee != null && employee.hasRelation(PARAM_PRIMARY_TEAM) ? employee.getRelationId(PARAM_PRIMARY_TEAM) : "";
		String location = employee != null && employee.hasRelation(PARAM_LOCATION) ? employee.getRelationId(PARAM_LOCATION) : "";

		YearMonth yearMonth = summary != null ? summary.yearMonth() : YearMonth.now();
		String periodStr = yearMonth.toString();
		String stateStr = periodState != null && !periodState.isBlank() ? periodState : STATE_OPEN;

		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Document document = new Document(PageSize.A4, 36, 36, 40, 40);
			PdfWriter writer = PdfWriter.getInstance(document, out);
			PdfHeaderFooterEvent event = new PdfHeaderFooterEvent(companyName, i18n.monthReportTitle + " – " + periodStr, i18n);
			writer.setPageEvent(event);

			document.addTitle(i18n.monthReportTitle + " " + periodStr + " - " + employeeName);
			document.addSubject("Chronivaro Time Tracking Month Report");
			document.addAuthor("Chronivaro");
			document.addCreator("Chronivaro System");

			document.open();

			// Top Brand & Title Box
			addReportHeader(document, companyName, companyLogo, i18n.monthReportTitle, i18n.period + ": " + periodStr);

			// Employee & Period Meta Box
			PdfPTable metaTable = new PdfPTable(4);
			metaTable.setWidthPercentage(100);
			metaTable.setWidths(new float[]{22, 28, 22, 28});
			metaTable.setSpacingBefore(8f);
			metaTable.setSpacingAfter(8f);

			addMetaCell(metaTable, i18n.employee + ":", employeeName + (employeeId.isEmpty() ? "" : " (" + employeeId + ")"));
			addMetaCell(metaTable, i18n.personalNumber + ":", persNr.isEmpty() ? "-" : persNr);
			addMetaCell(metaTable, i18n.team + ":", team.isEmpty() ? "-" : team);
			addMetaCell(metaTable, i18n.location + ":", location.isEmpty() ? "-" : location);
			addMetaCell(metaTable, i18n.status + ":", stateStr);

			if (STATE_APPROVED.equals(stateStr) || STATE_LOCKED.equals(stateStr)) {
				String appDate = approvalDate != null ? approvalDate.format(DATE_TIME_FORMATTER) : "-";
				String appBy = approvedBy != null && !approvedBy.isBlank() ? approvedBy : "-";
				addMetaCell(metaTable, i18n.approvedBy + ":", appBy);
				addMetaCell(metaTable, i18n.approvalDate + ":", appDate);
				addEmptyMetaCell(metaTable);
			} else {
				addEmptyMetaCell(metaTable);
				addEmptyMetaCell(metaTable);
				addEmptyMetaCell(metaTable);
			}
			document.add(metaTable);

			// Monthly Summary KPI Grid (3 columns x 3 rows)
			if (summary != null) {
				Paragraph kpiHeading = new Paragraph(i18n.summary, FONT_SECTION);
				kpiHeading.setSpacingBefore(4f);
				kpiHeading.setSpacingAfter(4f);
				document.add(kpiHeading);

				PdfPTable kpiTable = new PdfPTable(4);
				kpiTable.setWidthPercentage(100);
				kpiTable.setWidths(new float[]{25, 25, 25, 25});
				kpiTable.setSpacingAfter(8f);

				addKpiCell(kpiTable, i18n.targetTime, formatDuration(summary.totalTargetMinutes()), false);
				addKpiCell(kpiTable, i18n.actualTime, formatDuration(summary.totalActualMinutes()), false);
				addKpiCell(kpiTable, i18n.holidayCredit, formatDuration(summary.totalHolidayMinutes()), false);
				addKpiCell(kpiTable, i18n.totalAbsences, formatDuration(summary.totalAbsenceMinutes()), false);

				addKpiCell(kpiTable, i18n.paidAbsence, formatDuration(summary.paidAbsenceMinutes()), false);
				addKpiCell(kpiTable, i18n.unpaidAbsence, formatDuration(summary.unpaidAbsenceMinutes()), false);
				addKpiCell(kpiTable, i18n.vacationUsage, formatDuration(summary.vacationMinutes()), false);
				addKpiCell(kpiTable, i18n.initialBalance, formatDuration(summary.initialBalanceMinutes()), summary.initialBalanceMinutes() < 0);

				addKpiCell(kpiTable, i18n.periodVariance, formatDuration(summary.getPeriodBalance()), summary.getPeriodBalance() < 0);
				addKpiCell(kpiTable, i18n.manualCorrections, formatDuration(summary.manualCorrectionsMinutes()), summary.manualCorrectionsMinutes() < 0);
				addKpiCell(kpiTable, i18n.closingBalance, formatDuration(summary.getEndBalance()), summary.getEndBalance() < 0);
				addKpiCell(kpiTable, i18n.periodState, stateStr, false);

				document.add(kpiTable);
			}

			// Daily Breakdown Table
			Paragraph tableHeading = new Paragraph(i18n.dailyBreakdown, FONT_SECTION);
			tableHeading.setSpacingBefore(4f);
			tableHeading.setSpacingAfter(4f);
			document.add(tableHeading);

			PdfPTable dayTable = new PdfPTable(9);
			dayTable.setWidthPercentage(100);
			dayTable.setWidths(new float[]{14, 10, 11, 11, 11, 11, 12, 10, 10});
			dayTable.setHeaderRows(1);
			dayTable.setSpacingAfter(8f);

			addTh(dayTable, i18n.date, Element.ALIGN_LEFT);
			addTh(dayTable, i18n.day, Element.ALIGN_CENTER);
			addTh(dayTable, i18n.target, Element.ALIGN_RIGHT);
			addTh(dayTable, i18n.actual, Element.ALIGN_RIGHT);
			addTh(dayTable, i18n.holiday, Element.ALIGN_RIGHT);
			addTh(dayTable, i18n.absence, Element.ALIGN_RIGHT);
			addTh(dayTable, i18n.balance, Element.ALIGN_RIGHT);
			addTh(dayTable, i18n.status, Element.ALIGN_CENTER);
			addTh(dayTable, i18n.workingLocation, Element.ALIGN_CENTER);

			if (summary != null && summary.daySummaries() != null) {
				int rowIdx = 0;
				for (DaySummary day : summary.daySummaries()) {
					Color bg = rowIdx % 2 == 1 ? COLOR_ALT_ROW : Color.WHITE;
					String dayOfWeek = getDayOfWeekShort(day.date(), language);

					addTd(dayTable, day.date().toString(), Element.ALIGN_LEFT, bg);
					addTd(dayTable, dayOfWeek, Element.ALIGN_CENTER, bg);
					addTd(dayTable, formatDuration(day.targetMinutes()), Element.ALIGN_RIGHT, bg);
					addTd(dayTable, formatDuration(day.actualMinutes()), Element.ALIGN_RIGHT, bg);
					addTd(dayTable, formatDuration(day.holidayMinutes()), Element.ALIGN_RIGHT, bg);
					addTd(dayTable, formatDuration(day.absenceMinutes()), Element.ALIGN_RIGHT, bg);

					int bal = day.getBalance();
					String balStr = formatDuration(bal);
					if (bal < 0) {
						addTdCustom(dayTable, balStr, Element.ALIGN_RIGHT, bg, FONT_TD_NEG);
					} else if (bal > 0) {
						addTdCustom(dayTable, balStr, Element.ALIGN_RIGHT, bg, FONT_TD_POS);
					} else {
						addTd(dayTable, balStr, Element.ALIGN_RIGHT, bg);
					}

					addTd(dayTable, day.stateLabel() != null ? day.stateLabel() : "", Element.ALIGN_CENTER, bg);
					addTd(dayTable, day.workingLocation() != null ? day.workingLocation().name() : "-", Element.ALIGN_CENTER, bg);

					rowIdx++;
				}

				// Total Row
				addTdCustom(dayTable, i18n.total, Element.ALIGN_LEFT, COLOR_HEADER_BG, FONT_TD_BOLD);
				addTdCustom(dayTable, "", Element.ALIGN_CENTER, COLOR_HEADER_BG, FONT_TD_BOLD);
				addTdCustom(dayTable, formatDuration(summary.totalTargetMinutes()), Element.ALIGN_RIGHT, COLOR_HEADER_BG, FONT_TD_BOLD);
				addTdCustom(dayTable, formatDuration(summary.totalActualMinutes()), Element.ALIGN_RIGHT, COLOR_HEADER_BG, FONT_TD_BOLD);
				addTdCustom(dayTable, formatDuration(summary.totalHolidayMinutes()), Element.ALIGN_RIGHT, COLOR_HEADER_BG, FONT_TD_BOLD);
				addTdCustom(dayTable, formatDuration(summary.totalAbsenceMinutes()), Element.ALIGN_RIGHT, COLOR_HEADER_BG, FONT_TD_BOLD);

				int periodBal = summary.getPeriodBalance();
				Font balFont = periodBal < 0 ? FONT_TD_NEG : FONT_TD_BOLD;
				addTdCustom(dayTable, formatDuration(periodBal), Element.ALIGN_RIGHT, COLOR_HEADER_BG, balFont);
				addTdCustom(dayTable, "", Element.ALIGN_CENTER, COLOR_HEADER_BG, FONT_TD_BOLD);
				addTdCustom(dayTable, "", Element.ALIGN_CENTER, COLOR_HEADER_BG, FONT_TD_BOLD);
			}

			document.add(dayTable);
			document.close();
			return out.toByteArray();
		} catch (Exception e) {
			logger.error("Failed to generate Month Report PDF: {}", e.getMessage(), e);
			throw new RuntimeException("Failed to generate Month Report PDF: " + e.getMessage(), e);
		}
	}

	// -------------------------------------------------------------------------
	// 2. Vacation Summary Report PDF
	// -------------------------------------------------------------------------

	public static byte[] exportVacationReportToPdf(VacationAccountSummary summary,
												   List<Resource> entries,
												   Resource employee,
												   int year,
												   Resource companyConfig,
												   String language) {
		I18nTexts i18n = getI18n(language);
		String companyName = resolveCompanyName(companyConfig);
		String companyLogo = resolveCompanyLogo(companyConfig);

		String employeeId = employee != null ? employee.getId() : (summary != null ? summary.employeeId() : "");
		String employeeName = employee != null ? employee.getName() : "";
		String persNr = employee != null && employee.hasParameter(PARAM_PERSONAL_NUMBER) ? employee.getString(PARAM_PERSONAL_NUMBER) : "";
		String team = employee != null && employee.hasRelation(PARAM_PRIMARY_TEAM) ? employee.getRelationId(PARAM_PRIMARY_TEAM) : "";
		String location = employee != null && employee.hasRelation(PARAM_LOCATION) ? employee.getRelationId(PARAM_LOCATION) : "";

		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Document document = new Document(PageSize.A4, 36, 36, 40, 40);
			PdfWriter writer = PdfWriter.getInstance(document, out);
			PdfHeaderFooterEvent event = new PdfHeaderFooterEvent(companyName, i18n.vacationReportTitle + " " + year, i18n);
			writer.setPageEvent(event);

			document.addTitle(i18n.vacationReportTitle + " " + year + " - " + employeeName);
			document.addSubject("Chronivaro Vacation Report");
			document.addAuthor("Chronivaro");
			document.addCreator("Chronivaro System");

			document.open();

			// Header
			addReportHeader(document, companyName, companyLogo, i18n.vacationReportTitle, i18n.year + ": " + year);

			// Meta Box
			PdfPTable metaTable = new PdfPTable(4);
			metaTable.setWidthPercentage(100);
			metaTable.setWidths(new float[]{22, 28, 22, 28});
			metaTable.setSpacingBefore(8f);
			metaTable.setSpacingAfter(8f);

			addMetaCell(metaTable, i18n.employee + ":", employeeName + (employeeId.isEmpty() ? "" : " (" + employeeId + ")"));
			addMetaCell(metaTable, i18n.personalNumber + ":", persNr.isEmpty() ? "-" : persNr);
			addMetaCell(metaTable, i18n.team + ":", team.isEmpty() ? "-" : team);
			addMetaCell(metaTable, i18n.location + ":", location.isEmpty() ? "-" : location);
			document.add(metaTable);

			// Vacation Summary KPIs
			Paragraph kpiHeading = new Paragraph(i18n.summary, FONT_SECTION);
			kpiHeading.setSpacingBefore(4f);
			kpiHeading.setSpacingAfter(4f);
			document.add(kpiHeading);

			PdfPTable kpiTable = new PdfPTable(3);
			kpiTable.setWidthPercentage(100);
			kpiTable.setWidths(new float[]{33, 33, 34});
			kpiTable.setSpacingAfter(8f);

			int entitlement = summary != null ? summary.entitlementMinutes() : 0;
			int carryOver = summary != null ? summary.carryOverMinutes() : 0;
			int corrections = summary != null ? summary.correctionsMinutes() : 0;
			int usage = summary != null ? summary.usageMinutes() : 0;
			int remaining = summary != null ? summary.remainingMinutes() : 0;
			int totalEntitlement = entitlement + carryOver + corrections;

			addKpiCell(kpiTable, i18n.annualEntitlement, formatDuration(entitlement), false);
			addKpiCell(kpiTable, i18n.carryOver, formatDuration(carryOver), false);
			addKpiCell(kpiTable, i18n.corrections, formatDuration(corrections), corrections < 0);
			addKpiCell(kpiTable, i18n.totalEntitlement, formatDuration(totalEntitlement), false);
			addKpiCell(kpiTable, i18n.vacationTaken, formatDuration(usage), false);
			addKpiCell(kpiTable, i18n.remainingBalance, formatDuration(remaining), remaining < 0);

			document.add(kpiTable);

			// Vacation Journal Table
			Paragraph tableHeading = new Paragraph(i18n.vacationJournal, FONT_SECTION);
			tableHeading.setSpacingBefore(4f);
			tableHeading.setSpacingAfter(4f);
			document.add(tableHeading);

			PdfPTable journalTable = new PdfPTable(6);
			journalTable.setWidthPercentage(100);
			journalTable.setWidths(new float[]{16, 18, 16, 20, 16, 14});
			journalTable.setHeaderRows(1);
			journalTable.setSpacingAfter(8f);

			addTh(journalTable, i18n.date, Element.ALIGN_LEFT);
			addTh(journalTable, i18n.type, Element.ALIGN_LEFT);
			addTh(journalTable, i18n.amount, Element.ALIGN_RIGHT);
			addTh(journalTable, i18n.source, Element.ALIGN_LEFT);
			addTh(journalTable, i18n.comment, Element.ALIGN_LEFT);
			addTh(journalTable, i18n.createdBy, Element.ALIGN_LEFT);

			if (entries != null) {
				int rowIdx = 0;
				for (Resource entry : entries) {
					Color bg = rowIdx % 2 == 1 ? COLOR_ALT_ROW : Color.WHITE;
					String date = entry.hasParameter(PARAM_DATE) ? entry.getDate(PARAM_DATE).toLocalDate().toString() : "-";
					String type = entry.hasParameter(PARAM_VACATION_TYPE) ? entry.getString(PARAM_VACATION_TYPE) : "-";
					int amount = entry.hasParameter(PARAM_VALUE) ? entry.getInteger(PARAM_VALUE) : 0;
					String source = entry.hasRelation(PARAM_ABSENCE) ? entry.getRelationId(PARAM_ABSENCE) : "-";
					String comment = entry.hasParameter(PARAM_COMMENT) ? entry.getString(PARAM_COMMENT) : "-";
					String createdBy = entry.hasParameter(PARAM_CREATED_BY) ? entry.getString(PARAM_CREATED_BY) : "-";

					addTd(journalTable, date, Element.ALIGN_LEFT, bg);
					addTd(journalTable, type, Element.ALIGN_LEFT, bg);

					String amtStr = formatDuration(amount);
					if (amount < 0) {
						addTdCustom(journalTable, amtStr, Element.ALIGN_RIGHT, bg, FONT_TD_NEG);
					} else {
						addTd(journalTable, amtStr, Element.ALIGN_RIGHT, bg);
					}

					addTd(journalTable, source, Element.ALIGN_LEFT, bg);
					addTd(journalTable, comment, Element.ALIGN_LEFT, bg);
					addTd(journalTable, createdBy, Element.ALIGN_LEFT, bg);
					rowIdx++;
				}
			}

			document.add(journalTable);
			document.close();
			return out.toByteArray();
		} catch (Exception e) {
			logger.error("Failed to generate Vacation Report PDF: {}", e.getMessage(), e);
			throw new RuntimeException("Failed to generate Vacation Report PDF: " + e.getMessage(), e);
		}
	}

	// -------------------------------------------------------------------------
	// 3. Absence Report PDF
	// -------------------------------------------------------------------------

	public static byte[] exportAbsenceReportToPdf(List<AbsenceReportItem> items,
												  String teamName,
												  String employeeName,
												  LocalDate from,
												  LocalDate to,
												  Resource companyConfig,
												  String language) {
		I18nTexts i18n = getI18n(language);
		String companyName = resolveCompanyName(companyConfig);
		String companyLogo = resolveCompanyLogo(companyConfig);

		String dateRange = (from != null ? from.toString() : "-") + " " + i18n.to + " " + (to != null ? to.toString() : "-");

		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Document document = new Document(PageSize.A4, 36, 36, 40, 40);
			PdfWriter writer = PdfWriter.getInstance(document, out);
			PdfHeaderFooterEvent event = new PdfHeaderFooterEvent(companyName, i18n.absenceReportTitle, i18n);
			writer.setPageEvent(event);

			document.addTitle(i18n.absenceReportTitle);
			document.addSubject("Chronivaro Absence Report");
			document.addAuthor("Chronivaro");
			document.addCreator("Chronivaro System");

			document.open();

			// Header
			addReportHeader(document, companyName, companyLogo, i18n.absenceReportTitle, i18n.period + ": " + dateRange);

			// Meta Box
			PdfPTable metaTable = new PdfPTable(4);
			metaTable.setWidthPercentage(100);
			metaTable.setWidths(new float[]{22, 28, 22, 28});
			metaTable.setSpacingBefore(8f);
			metaTable.setSpacingAfter(8f);

			if (employeeName != null && !employeeName.isBlank()) {
				addMetaCell(metaTable, i18n.employee + ":", employeeName);
			} else {
				addMetaCell(metaTable, i18n.employee + ":", i18n.all);
			}

			if (teamName != null && !teamName.isBlank()) {
				addMetaCell(metaTable, i18n.team + ":", teamName);
			} else {
				addMetaCell(metaTable, i18n.team + ":", i18n.all);
			}

			addMetaCell(metaTable, i18n.period + ":", dateRange);
			addMetaCell(metaTable, i18n.totalEntries + ":", items != null ? String.valueOf(items.size()) : "0");
			document.add(metaTable);

			// Absences List Table
			PdfPTable table = new PdfPTable(8);
			table.setWidthPercentage(100);
			table.setWidths(new float[]{18, 16, 12, 12, 12, 10, 10, 10});
			table.setHeaderRows(1);
			table.setSpacingBefore(4f);
			table.setSpacingAfter(8f);

			addTh(table, i18n.employee, Element.ALIGN_LEFT);
			addTh(table, i18n.absenceType, Element.ALIGN_LEFT);
			addTh(table, i18n.from, Element.ALIGN_LEFT);
			addTh(table, i18n.to, Element.ALIGN_LEFT);
			addTh(table, i18n.durationType, Element.ALIGN_CENTER);
			addTh(table, i18n.duration, Element.ALIGN_RIGHT);
			addTh(table, i18n.paid, Element.ALIGN_CENTER);
			addTh(table, i18n.status, Element.ALIGN_CENTER);

			if (items != null) {
				int rowIdx = 0;
				for (AbsenceReportItem item : items) {
					Color bg = rowIdx % 2 == 1 ? COLOR_ALT_ROW : Color.WHITE;
					String emp = item.employeeName() != null ? item.employeeName() : item.employeeId();
					String type = item.absenceTypeName() != null ? item.absenceTypeName() : item.absenceTypeCode();
					String start = item.start() != null ? item.start().toString() : "-";
					String end = item.end() != null ? item.end().toString() : "-";
					String durType = item.durationType() != null ? item.durationType() : "-";
					String dur = formatDuration(item.minutes());
					String paid = item.paid() ? i18n.yes : i18n.no;
					String state = item.state() != null ? item.state() : "-";

					addTd(table, emp, Element.ALIGN_LEFT, bg);
					addTd(table, type, Element.ALIGN_LEFT, bg);
					addTd(table, start, Element.ALIGN_LEFT, bg);
					addTd(table, end, Element.ALIGN_LEFT, bg);
					addTd(table, durType, Element.ALIGN_CENTER, bg);
					addTd(table, dur, Element.ALIGN_RIGHT, bg);
					addTd(table, paid, Element.ALIGN_CENTER, bg);
					addTd(table, state, Element.ALIGN_CENTER, bg);

					rowIdx++;
				}
			}

			document.add(table);
			document.close();
			return out.toByteArray();
		} catch (Exception e) {
			logger.error("Failed to generate Absence Report PDF: {}", e.getMessage(), e);
			throw new RuntimeException("Failed to generate Absence Report PDF: " + e.getMessage(), e);
		}
	}

	// -------------------------------------------------------------------------
	// Layout & Rendering Helpers
	// -------------------------------------------------------------------------

	private static void addReportHeader(Document document, String companyName, String companyLogo, String title, String subtitle) throws DocumentException {
		PdfPTable headerTable = new PdfPTable(2);
		headerTable.setWidthPercentage(100);
		headerTable.setWidths(new float[]{70, 30});

		PdfPCell titleCell = new PdfPCell();
		titleCell.setBorder(Rectangle.NO_BORDER);
		titleCell.setPadding(0);

		if (companyName != null && !companyName.isBlank()) {
			Paragraph companyP = new Paragraph(companyName.toUpperCase(Locale.ROOT), FONT_SUBTITLE);
			companyP.setSpacingAfter(2f);
			titleCell.addElement(companyP);
		}

		Paragraph titleP = new Paragraph(title, FONT_TITLE);
		titleP.setSpacingAfter(2f);
		titleCell.addElement(titleP);

		if (subtitle != null && !subtitle.isBlank()) {
			Paragraph subtitleP = new Paragraph(subtitle, FONT_SUBTITLE);
			titleCell.addElement(subtitleP);
		}

		headerTable.addCell(titleCell);

		PdfPCell logoCell = new PdfPCell();
		logoCell.setBorder(Rectangle.NO_BORDER);
		logoCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
		logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		logoCell.setPadding(0);

		Image logoImg = tryLoadLogo(companyLogo);
		if (logoImg != null) {
			logoImg.scaleToFit(120, 36);
			logoImg.setAlignment(Element.ALIGN_RIGHT);
			logoCell.addElement(logoImg);
		}

		headerTable.addCell(logoCell);
		document.add(headerTable);
	}

	private static Image tryLoadLogo(String logoData) {
		if (logoData == null || logoData.isBlank()) {
			return null;
		}
		try {
			String trimmed = logoData.trim();
			if (trimmed.startsWith("data:image/") && trimmed.contains(";base64,")) {
				int base64Idx = trimmed.indexOf(";base64,");
				String base64Content = trimmed.substring(base64Idx + 8);
				byte[] imageBytes = Base64.getDecoder().decode(base64Content);
				return Image.getInstance(imageBytes);
			}
			if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("file:/")) {
				return Image.getInstance(trimmed);
			}
		} catch (Exception e) {
			logger.warn("Could not load company logo for PDF rendering: {}", e.getMessage());
		}
		return null;
	}

	private static void addMetaCell(PdfPTable table, String label, String value) {
		PdfPCell cell = new PdfPCell();
		cell.setBorder(Rectangle.BOX);
		cell.setBorderColor(COLOR_BORDER);
		cell.setBackgroundColor(COLOR_HEADER_BG);
		cell.setPadding(4f);

		Paragraph p = new Paragraph();
		p.add(new Chunk(label + " ", FONT_META_LABEL));
		p.add(new Chunk(value != null ? value : "-", FONT_META_VAL));
		cell.addElement(p);
		table.addCell(cell);
	}

	private static void addEmptyMetaCell(PdfPTable table) {
		PdfPCell cell = new PdfPCell();
		cell.setBorder(Rectangle.BOX);
		cell.setBorderColor(COLOR_BORDER);
		cell.setBackgroundColor(COLOR_HEADER_BG);
		cell.setPadding(4f);
		table.addCell(cell);
	}

	private static void addKpiCell(PdfPTable table, String label, String value, boolean isNegative) {
		PdfPCell cell = new PdfPCell();
		cell.setBorder(Rectangle.BOX);
		cell.setBorderColor(COLOR_BORDER);
		cell.setBackgroundColor(COLOR_KPI_BG);
		cell.setPadding(5f);

		Paragraph lblP = new Paragraph(label, FONT_META_LABEL);
		lblP.setSpacingAfter(2f);
		cell.addElement(lblP);

		Font numFont = isNegative ? FONT_KPI_NUM_NEG : FONT_KPI_NUM;
		Paragraph valP = new Paragraph(value != null ? value : "-", numFont);
		cell.addElement(valP);

		table.addCell(cell);
	}

	private static void addTh(PdfPTable table, String title, int align) {
		PdfPCell cell = new PdfPCell(new Phrase(title, FONT_TH));
		cell.setBorder(Rectangle.BOX);
		cell.setBorderColor(COLOR_BORDER);
		cell.setBackgroundColor(COLOR_HEADER_BG);
		cell.setHorizontalAlignment(align);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(4.5f);
		table.addCell(cell);
	}

	private static void addTd(PdfPTable table, String text, int align, Color bg) {
		addTdCustom(table, text, align, bg, FONT_TD);
	}

	private static void addTdCustom(PdfPTable table, String text, int align, Color bg, Font font) {
		PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
		cell.setBorder(Rectangle.BOX);
		cell.setBorderColor(COLOR_BORDER);
		cell.setBackgroundColor(bg != null ? bg : Color.WHITE);
		cell.setHorizontalAlignment(align);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(4.5f);
		table.addCell(cell);
	}

	private static String resolveCompanyName(Resource config) {
		if (config != null && config.hasParameter(PARAM_COMPANY_NAME)) {
			String name = config.getString(PARAM_COMPANY_NAME);
			if (name != null && !name.isBlank()) {
				return name.trim();
			}
		}
		return "Chronivaro";
	}

	private static String resolveCompanyLogo(Resource config) {
		if (config != null && config.hasParameter(PARAM_COMPANY_LOGO)) {
			String logo = config.getString(PARAM_COMPANY_LOGO);
			if (logo != null && !logo.isBlank()) {
				return logo.trim();
			}
		}
		return null;
	}

	private static String getDayOfWeekShort(LocalDate date, String language) {
		int dow = date.getDayOfWeek().getValue();
		boolean isDe = !"en".equalsIgnoreCase(language);
		return switch (dow) {
			case 1 -> isDe ? "Mo" : "Mon";
			case 2 -> isDe ? "Di" : "Tue";
			case 3 -> isDe ? "Mi" : "Wed";
			case 4 -> isDe ? "Do" : "Thu";
			case 5 -> isDe ? "Fr" : "Fri";
			case 6 -> isDe ? "Sa" : "Sat";
			case 7 -> isDe ? "So" : "Sun";
			default -> "";
		};
	}

	// -------------------------------------------------------------------------
	// Page Event for Header / Footer / Total Page Count
	// -------------------------------------------------------------------------

	private static class PdfHeaderFooterEvent extends PdfPageEventHelper {
		private final String companyName;
		private final String documentTitle;
		private final I18nTexts i18n;
		private PdfTemplate totalPagesTemplate;
		private BaseFont baseFont;

		public PdfHeaderFooterEvent(String companyName, String documentTitle, I18nTexts i18n) {
			this.companyName = companyName;
			this.documentTitle = documentTitle;
			this.i18n = i18n;
		}

		@Override
		public void onOpenDocument(PdfWriter writer, Document document) {
			try {
				totalPagesTemplate = writer.getDirectContent().createTemplate(30, 16);
				baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
			} catch (Exception e) {
				logger.warn("Could not initialize footer font/template: {}", e.getMessage());
			}
		}

		@Override
		public void onEndPage(PdfWriter writer, Document document) {
			PdfContentByte cb = writer.getDirectContent();

			float left = document.left();
			float right = document.right();
			float bottom = document.bottom() - 20;

			// Draw subtle footer divider line
			cb.setColorStroke(COLOR_BORDER);
			cb.setLineWidth(0.5f);
			cb.moveTo(left, bottom + 12);
			cb.lineTo(right, bottom + 12);
			cb.stroke();

			// Left footer: "Chronivaro | Created: YYYY-MM-DD HH:mm"
			String createdStr = i18n.generatedAt + ": " + LocalDateTime.now().format(DATE_TIME_FORMATTER);
			String leftText = (companyName != null && !companyName.isBlank() ? companyName + " | " : "") + createdStr;

			cb.beginText();
			cb.setFontAndSize(baseFont, 7.5f);
			cb.setColorFill(COLOR_SECONDARY);
			cb.showTextAligned(Element.ALIGN_LEFT, leftText, left, bottom, 0);

			// Right footer: "Page X of Y" / "Seite X von Y"
			String pageText = i18n.page + " " + writer.getPageNumber() + " " + i18n.of + " ";
			float pageTextWidth = baseFont.getWidthPoint(pageText, 7.5f);
			cb.showTextAligned(Element.ALIGN_RIGHT, pageText, right - 12, bottom, 0);
			cb.endText();

			// Add the template containing total page count
			if (totalPagesTemplate != null) {
				cb.addTemplate(totalPagesTemplate, right - 12, bottom);
			}
		}

		@Override
		public void onCloseDocument(PdfWriter writer, Document document) {
			if (totalPagesTemplate != null && baseFont != null) {
				totalPagesTemplate.beginText();
				totalPagesTemplate.setFontAndSize(baseFont, 7.5f);
				totalPagesTemplate.setColorFill(COLOR_SECONDARY);
				totalPagesTemplate.showTextAligned(Element.ALIGN_LEFT, String.valueOf(writer.getPageNumber() - 1), 0, 0, 0);
				totalPagesTemplate.endText();
			}
		}
	}

	// -------------------------------------------------------------------------
	// Localization Bundle for PDF Reports (Swiss German and English)
	// -------------------------------------------------------------------------

	public static class I18nTexts {
		public String monthReportTitle;
		public String vacationReportTitle;
		public String absenceReportTitle;
		public String period;
		public String year;
		public String employee;
		public String personalNumber;
		public String team;
		public String location;
		public String status;
		public String approvedBy;
		public String approvalDate;
		public String summary;
		public String targetTime;
		public String actualTime;
		public String holidayCredit;
		public String totalAbsences;
		public String paidAbsence;
		public String unpaidAbsence;
		public String vacationUsage;
		public String initialBalance;
		public String periodVariance;
		public String manualCorrections;
		public String closingBalance;
		public String periodState;
		public String dailyBreakdown;
		public String date;
		public String day;
		public String target;
		public String actual;
		public String holiday;
		public String absence;
		public String balance;
		public String workingLocation;
		public String total;
		public String annualEntitlement;
		public String carryOver;
		public String corrections;
		public String totalEntitlement;
		public String vacationTaken;
		public String remainingBalance;
		public String vacationJournal;
		public String type;
		public String amount;
		public String source;
		public String comment;
		public String createdBy;
		public String absenceType;
		public String from;
		public String to;
		public String durationType;
		public String duration;
		public String paid;
		public String all;
		public String totalEntries;
		public String yes;
		public String no;
		public String page;
		public String of;
		public String generatedAt;
	}

	public static I18nTexts getI18n(String language) {
		I18nTexts texts = new I18nTexts();
		boolean isEn = "en".equalsIgnoreCase(language);

		if (isEn) {
			texts.monthReportTitle = "Monthly Time Report";
			texts.vacationReportTitle = "Vacation Summary";
			texts.absenceReportTitle = "Absence Report";
			texts.period = "Period";
			texts.year = "Year";
			texts.employee = "Employee";
			texts.personalNumber = "Pers. Nr.";
			texts.team = "Team";
			texts.location = "Location";
			texts.status = "Status";
			texts.approvedBy = "Approved by";
			texts.approvalDate = "Approval Date";
			texts.summary = "Monthly Summary";
			texts.targetTime = "Target Time";
			texts.actualTime = "Actual Time";
			texts.holidayCredit = "Holiday Credit";
			texts.totalAbsences = "Total Absences";
			texts.paidAbsence = "Paid Absence";
			texts.unpaidAbsence = "Unpaid Absence";
			texts.vacationUsage = "Vacation Usage";
			texts.initialBalance = "Starting Balance";
			texts.periodVariance = "Period Variance";
			texts.manualCorrections = "Corrections";
			texts.closingBalance = "Closing Balance";
			texts.periodState = "Period State";
			texts.dailyBreakdown = "Daily Breakdown";
			texts.date = "Date";
			texts.day = "Day";
			texts.target = "Target";
			texts.actual = "Actual";
			texts.holiday = "Holiday";
			texts.absence = "Absence";
			texts.balance = "Balance";
			texts.workingLocation = "Location";
			texts.total = "Total";
			texts.annualEntitlement = "Annual Entitlement";
			texts.carryOver = "Carry-Over";
			texts.corrections = "Adjustments";
			texts.totalEntitlement = "Total Entitlement";
			texts.vacationTaken = "Vacation Taken";
			texts.remainingBalance = "Remaining Balance";
			texts.vacationJournal = "Vacation Journal";
			texts.type = "Type";
			texts.amount = "Amount";
			texts.source = "Source";
			texts.comment = "Comment";
			texts.createdBy = "Created by";
			texts.absenceType = "Absence Type";
			texts.from = "From";
			texts.to = "to";
			texts.durationType = "Duration Type";
			texts.duration = "Duration";
			texts.paid = "Paid";
			texts.all = "All";
			texts.totalEntries = "Total Entries";
			texts.yes = "Yes";
			texts.no = "No";
			texts.page = "Page";
			texts.of = "of";
			texts.generatedAt = "Generated on";
		} else {
			// German (Swiss German standard: no 'ß')
			texts.monthReportTitle = "Monatsreport";
			texts.vacationReportTitle = "Ferienübersicht";
			texts.absenceReportTitle = "Abwesenheitsreport";
			texts.period = "Periode";
			texts.year = "Jahr";
			texts.employee = "Mitarbeiter";
			texts.personalNumber = "Pers. Nr.";
			texts.team = "Team";
			texts.location = "Standort";
			texts.status = "Status";
			texts.approvedBy = "Genehmigt von";
			texts.approvalDate = "Genehmigungsdatum";
			texts.summary = "Zusammenfassung";
			texts.targetTime = "Sollzeit";
			texts.actualTime = "Istzeit";
			texts.holidayCredit = "Feiertage";
			texts.totalAbsences = "Abwesenheiten Total";
			texts.paidAbsence = "Bezahlte Abwesenheit";
			texts.unpaidAbsence = "Unbezahlte Abwesenheit";
			texts.vacationUsage = "Ferienbezug";
			texts.initialBalance = "Anfangssaldo";
			texts.periodVariance = "Periodensaldo";
			texts.manualCorrections = "Korrekturen";
			texts.closingBalance = "Endsaldo";
			texts.periodState = "Periodenstatus";
			texts.dailyBreakdown = "Tagesübersicht";
			texts.date = "Datum";
			texts.day = "Tag";
			texts.target = "Soll";
			texts.actual = "Ist";
			texts.holiday = "Feiertag";
			texts.absence = "Abwesend";
			texts.balance = "Saldo";
			texts.workingLocation = "Arbeitsort";
			texts.total = "Total";
			texts.annualEntitlement = "Jahresanspruch";
			texts.carryOver = "Übertrag Vorjahr";
			texts.corrections = "Korrekturen";
			texts.totalEntitlement = "Gesamtanspruch";
			texts.vacationTaken = "Bezogen";
			texts.remainingBalance = "Restguthaben";
			texts.vacationJournal = "Ferienbuchungen";
			texts.type = "Typ";
			texts.amount = "Betrag";
			texts.source = "Quelle";
			texts.comment = "Kommentar";
			texts.createdBy = "Erstellt von";
			texts.absenceType = "Abwesenheitsart";
			texts.from = "Von";
			texts.to = "bis";
			texts.durationType = "Dauertyp";
			texts.duration = "Dauer";
			texts.paid = "Bezahlt";
			texts.all = "Alle";
			texts.totalEntries = "Einträge Total";
			texts.yes = "Ja";
			texts.no = "Nein";
			texts.page = "Seite";
			texts.of = "von";
			texts.generatedAt = "Erstellt am";
		}
		return texts;
	}
}
