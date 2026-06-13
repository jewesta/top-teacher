package de.westarps.topteacher.backend.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.GradingScaleRange;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.loe.LoeAggregationColumns;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoePointRules;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;

@Service
public class ExamEvaluationExcelExportService {

	private static final String SHEET_NAME = "Auswertung";
	private static final int FROZEN_COLUMNS = 3;
	private static final int HEADER_ROW_INDEX = 0;

	private final CourseRepository courseRepository;
	private final ExamRepository examRepository;
	private final GradingScaleRepository gradingScaleRepository;
	private final LevelOfExpectationsRepository levelOfExpectationsRepository;

	public ExamEvaluationExcelExportService(final CourseRepository courseRepository,
			final ExamRepository examRepository, final GradingScaleRepository gradingScaleRepository,
			final LevelOfExpectationsRepository levelOfExpectationsRepository) {
		this.courseRepository = courseRepository;
		this.examRepository = examRepository;
		this.gradingScaleRepository = gradingScaleRepository;
		this.levelOfExpectationsRepository = levelOfExpectationsRepository;
	}

	public byte[] renderWorkbook(final int examId) {
		final EvaluationExportData data = createExportData(examId);

		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			renderSheet(workbook, data);
			workbook.write(output);
			return output.toByteArray();
		} catch (final IOException exception) {
			throw new IllegalStateException("Excel-Datei konnte nicht erzeugt werden.", exception);
		}
	}

	private void renderSheet(final Workbook workbook, final EvaluationExportData data) {
		final Sheet sheet = workbook.createSheet(SHEET_NAME);
		final WorkbookStyles styles = WorkbookStyles.create(workbook);
		final List<LoeAggregationColumns.Column> aggregationColumns = LoeAggregationColumns.from(data.parts(),
				data.categories(), data.tasks(), data.requirements());

		renderHeader(sheet, styles, aggregationColumns);
		renderRows(sheet, styles, data, aggregationColumns);
		configureSheet(sheet, aggregationColumns.size(), data.pupils().size());
	}

	private void renderHeader(final Sheet sheet, final WorkbookStyles styles,
			final List<LoeAggregationColumns.Column> aggregationColumns) {
		final Row header = sheet.createRow(HEADER_ROW_INDEX);
		header.setHeightInPoints(90);

		headerCell(header, 0, "Schüler", styles.header());
		headerCell(header, 1, "Gesamt", styles.header());
		headerCell(header, 2, "Note", styles.header());
		for (int index = 0; index < aggregationColumns.size(); index++) {
			headerCell(header, FROZEN_COLUMNS + index, aggregationColumns.get(index).title(), styles.header());
		}
	}

	private void renderRows(final Sheet sheet, final WorkbookStyles styles, final EvaluationExportData data,
			final List<LoeAggregationColumns.Column> aggregationColumns) {
		for (int index = 0; index < data.pupils().size(); index++) {
			final EvaluationRow evaluationRow = data.evaluationRow(data.pupils().get(index));
			final Row row = sheet.createRow(index + 1);
			textCell(row, 0, evaluationRow.pupilName(), styles.text());
			textCell(row, 1,
					pointsDisplayName(evaluationRow.pointsFor(data.requirements()), hasBonus(data.requirements())),
					styles.centered());
			textCell(row, 2, data.gradeDisplayName(evaluationRow), styles.centered());
			for (int columnIndex = 0; columnIndex < aggregationColumns.size(); columnIndex++) {
				final LoeAggregationColumns.Column aggregationColumn = aggregationColumns.get(columnIndex);
				textCell(row, FROZEN_COLUMNS + columnIndex,
						pointsDisplayName(evaluationRow.pointsFor(aggregationColumn.requirements()),
								hasBonus(aggregationColumn.requirements())),
						styles.centered());
			}
		}
	}

	private void configureSheet(final Sheet sheet, final int aggregationColumnCount, final int pupilCount) {
		sheet.createFreezePane(FROZEN_COLUMNS, 1);
		sheet.setAutoFilter(
				new CellRangeAddress(HEADER_ROW_INDEX, pupilCount, 0, FROZEN_COLUMNS + aggregationColumnCount - 1));
		sheet.setColumnWidth(0, 26 * 256);
		sheet.setColumnWidth(1, 10 * 256);
		sheet.setColumnWidth(2, 18 * 256);
		for (int index = 0; index < aggregationColumnCount; index++) {
			sheet.setColumnWidth(FROZEN_COLUMNS + index, 10 * 256);
		}
	}

	private EvaluationExportData createExportData(final int examId) {
		final Exam exam = examRepository.findById(examId)
				.orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + examId));
		final Course course = courseRepository.findById(exam.courseId())
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + exam.courseId()));
		final List<LoePart> parts = levelOfExpectationsRepository.findPartsByExamId(examId);
		final List<LoeCategory> categories = levelOfExpectationsRepository.findCategoriesByExamId(examId);
		final List<LoeTask> tasks = levelOfExpectationsRepository.findTasksByExamId(examId);
		final List<LoeRequirement> requirements = levelOfExpectationsRepository.findRequirementsByExamId(examId);
		final List<Pupil> pupils = courseRepository.findPupils(exam.courseId());
		final Map<Integer, Map<Integer, Integer>> achievedPointsByPupilId = pupils.stream()
				.collect(Collectors.toMap(Pupil::id, pupil -> achievedPointsByRequirementId(exam, pupil)));

		if (course.gradingScaleId() == null) {
			return new EvaluationExportData(pupils, parts, categories, tasks, requirements, null, List.of(),
					achievedPointsByPupilId);
		}

		final GradingScale gradingScale = gradingScaleRepository.findById(course.gradingScaleId()).orElse(null);
		if (gradingScale == null) {
			return new EvaluationExportData(pupils, parts, categories, tasks, requirements, null, List.of(),
					achievedPointsByPupilId);
		}
		return new EvaluationExportData(pupils, parts, categories, tasks, requirements, new LoePointRules(gradingScale),
				gradingScaleRepository.findRangesByGradingScaleId(gradingScale.id()), achievedPointsByPupilId);
	}

	private Map<Integer, Integer> achievedPointsByRequirementId(final Exam exam, final Pupil pupil) {
		return levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(exam.id(), pupil.id()).stream()
				.collect(Collectors.toMap(LoeRequirementResult::requirementId, LoeRequirementResult::points,
						(left, right) -> right));
	}

	private static void headerCell(final Row row, final int columnIndex, final String value, final CellStyle style) {
		textCell(row, columnIndex, value, style);
	}

	private static void textCell(final Row row, final int columnIndex, final String value, final CellStyle style) {
		final Cell cell = row.createCell(columnIndex);
		cell.setCellValue(value == null ? "" : value);
		cell.setCellStyle(style);
	}

	private static boolean hasBonus(final List<LoeRequirement> requirements) {
		return requirements.stream().anyMatch(LoeRequirement::bonus);
	}

	private static String pointsDisplayName(final LoePoints points, final boolean showBonus) {
		if (!showBonus && points.bonus() == 0) {
			return String.valueOf(points.regular());
		}
		return points.regular() + " (+" + points.bonus() + ")";
	}

	private record EvaluationExportData(List<Pupil> pupils, List<LoePart> parts, List<LoeCategory> categories,
			List<LoeTask> tasks, List<LoeRequirement> requirements, LoePointRules pointRules,
			List<GradingScaleRange> gradingScaleRanges, Map<Integer, Map<Integer, Integer>> achievedPointsByPupilId) {

		EvaluationRow evaluationRow(final Pupil pupil) {
			final Map<Integer, Integer> achievedPointsByRequirementId = achievedPointsByPupilId.getOrDefault(pupil.id(),
					Map.of());
			return new EvaluationRow(pupil, achievedPointsByRequirementId);
		}

		String gradeDisplayName(final EvaluationRow row) {
			if (pointRules == null || !pointRules.regularMaxPointsMatch(requirements)) {
				return "";
			}
			final int effectivePoints;
			try {
				effectivePoints = pointRules.cappedAchievedTotal(requirements, row::achievedPointsFor);
			} catch (final IllegalArgumentException exception) {
				return "";
			}
			return gradingScaleRanges.stream()
					.filter(range -> range.minPoints() <= effectivePoints && effectivePoints <= range.maxPoints())
					.findFirst().map(range -> range.gradeLevel().getDisplayName()).orElse("");
		}

	}

	private record EvaluationRow(Pupil pupil, Map<Integer, Integer> achievedPointsByRequirementId) {

		String pupilName() {
			return pupil.surname() + ", " + pupil.name();
		}

		LoePoints pointsFor(final List<LoeRequirement> requirements) {
			return requirements.stream().map(this::pointsFor).reduce(new LoePoints(0, 0), LoePoints::plus);
		}

		int achievedPointsFor(final LoeRequirement requirement) {
			return achievedPointsByRequirementId.getOrDefault(requirement.id(), 0);
		}

		private LoePoints pointsFor(final LoeRequirement requirement) {
			final int points = achievedPointsFor(requirement);
			return requirement.bonus() ? new LoePoints(0, points) : new LoePoints(points, 0);
		}
	}

	private record LoePoints(int regular, int bonus) {

		LoePoints plus(final LoePoints other) {
			return new LoePoints(regular + other.regular, bonus + other.bonus);
		}
	}

	private record WorkbookStyles(CellStyle header, CellStyle text, CellStyle centered) {

		private static WorkbookStyles create(final Workbook workbook) {
			final Font headerFont = workbook.createFont();
			headerFont.setBold(true);

			final CellStyle header = bordered(workbook);
			header.setFont(headerFont);
			header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
			header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			header.setAlignment(HorizontalAlignment.CENTER);
			header.setVerticalAlignment(VerticalAlignment.BOTTOM);
			header.setWrapText(true);
			header.setRotation((short) 45);

			final CellStyle text = bordered(workbook);
			text.setVerticalAlignment(VerticalAlignment.CENTER);

			final CellStyle centered = bordered(workbook);
			centered.setAlignment(HorizontalAlignment.CENTER);
			centered.setVerticalAlignment(VerticalAlignment.CENTER);

			return new WorkbookStyles(header, text, centered);
		}

		private static CellStyle bordered(final Workbook workbook) {
			final CellStyle style = workbook.createCellStyle();
			style.setBorderBottom(BorderStyle.THIN);
			style.setBorderLeft(BorderStyle.THIN);
			style.setBorderRight(BorderStyle.THIN);
			style.setBorderTop(BorderStyle.THIN);
			return style;
		}
	}
}
