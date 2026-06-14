package de.westarps.topteacher.backend.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradeLevel;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.GradingScaleRange;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;

class ExamEvaluationExcelExportServiceTests {

	private static final Exam EXAM = new Exam(1, 10, "Klausur Nr. 1", LocalDate.of(2026, 5, 21));
	private static final Subject SUBJECT = new Subject(1, "Englisch", Lifecycle.ACTIVE);
	private static final Course COURSE = new Course(EXAM.courseId(), SchoolClass.CLS_5A, SUBJECT, new SchoolYear(2026),
			CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, 30);
	private static final GradingScale GRADING_SCALE = new GradingScale(COURSE.gradingScaleId(), "Standard", 7,
			Lifecycle.ACTIVE);
	private static final Pupil PUPIL = new Pupil(20, "Anna", "Ergebnis", Lifecycle.ACTIVE);
	private static final LoePart PART = new LoePart(1, EXAM.id(), "Klausurteil A", 0);
	private static final LoeCategory CATEGORY = new LoeCategory(2, PART.id(), "Inhalt", "", 0);
	private static final LoeTask TASK = new LoeTask(3, CATEGORY.id(), "Teilaufgabe 1", 0);
	private static final LoeRequirement REQUIREMENT = new LoeRequirement(4, TASK.id(), "Anforderung", 5, false, 0);
	private static final LoePart SECOND_PART = new LoePart(5, EXAM.id(), "Klausurteil B", 1);
	private static final LoeCategory SECOND_CATEGORY = new LoeCategory(6, SECOND_PART.id(), "Sprache", "", 0);
	private static final LoeTask SECOND_TASK = new LoeTask(7, SECOND_CATEGORY.id(), "Teilaufgabe 2", 0);
	private static final LoeRequirement SECOND_REQUIREMENT = new LoeRequirement(8, SECOND_TASK.id(),
			"Zweite Anforderung", 2, false, 0);

	@Test
	void rendersEvaluationWorkbookWithFrozenSummaryColumns() throws IOException {
		final CourseRepository courseRepository = mock(CourseRepository.class);
		when(courseRepository.findById(EXAM.courseId())).thenReturn(Optional.of(COURSE));

		final ExamRepository examRepository = mock(ExamRepository.class);
		when(examRepository.findById(EXAM.id())).thenReturn(Optional.of(EXAM));
		when(examRepository.findPupils(EXAM.id())).thenReturn(List.of(PUPIL));

		final GradingScaleRepository gradingScaleRepository = mock(GradingScaleRepository.class);
		when(gradingScaleRepository.findById(COURSE.gradingScaleId())).thenReturn(Optional.of(GRADING_SCALE));
		when(gradingScaleRepository.findRangesByGradingScaleId(GRADING_SCALE.id()))
				.thenReturn(List.of(new GradingScaleRange(1, GRADING_SCALE.id(), GradeLevel.SEHR_GUT_PLUS, 7, 7),
						new GradingScaleRange(2, GRADING_SCALE.id(), GradeLevel.UNGENUEGEND, 0, 6)));

		final LevelOfExpectationsRepository levelOfExpectationsRepository = mock(LevelOfExpectationsRepository.class);
		when(levelOfExpectationsRepository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART, SECOND_PART));
		when(levelOfExpectationsRepository.findCategoriesByExamId(EXAM.id()))
				.thenReturn(List.of(CATEGORY, SECOND_CATEGORY));
		when(levelOfExpectationsRepository.findTasksByExamId(EXAM.id())).thenReturn(List.of(TASK, SECOND_TASK));
		when(levelOfExpectationsRepository.findRequirementsByExamId(EXAM.id()))
				.thenReturn(List.of(REQUIREMENT, SECOND_REQUIREMENT));
		when(levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(EXAM.id(), PUPIL.id()))
				.thenReturn(List.of(new LoeRequirementResult(REQUIREMENT.id(), PUPIL.id(), 5),
						new LoeRequirementResult(SECOND_REQUIREMENT.id(), PUPIL.id(), 2)));

		final ExamEvaluationExcelExportService service = new ExamEvaluationExcelExportService(courseRepository,
				examRepository, gradingScaleRepository, levelOfExpectationsRepository);

		final byte[] workbookBytes = service.renderWorkbook(EXAM.id());

		try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(workbookBytes))) {
			final var sheet = workbook.getSheet("Auswertung");
			assertThat(sheet).isNotNull();
			assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();

			final var header = sheet.getRow(0);
			assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Schüler:in");
			assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Gesamt");
			assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Note");
			assertThat(header.getCell(3).getStringCellValue()).isEqualTo("Klausurteil A");
			assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Klausurteil B");
			assertThat(header.getCell(4).getCellStyle().getRotation()).isEqualTo((short) 45);

			final var row = sheet.getRow(1);
			assertThat(row.getCell(0).getStringCellValue()).isEqualTo("Ergebnis, Anna");
			assertThat(row.getCell(1).getStringCellValue()).isEqualTo("7");
			assertThat(row.getCell(2).getStringCellValue()).isEqualTo("sehr gut plus");
			assertThat(row.getCell(3).getStringCellValue()).isEqualTo("5");
			assertThat(row.getCell(4).getStringCellValue()).isEqualTo("2");
		}
	}
}
