package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.mcp.LevelOfExpectationsMcpTools.CategoryDraft;
import de.westarps.topteacher.mcp.LevelOfExpectationsMcpTools.LevelOfExpectationsView;
import de.westarps.topteacher.mcp.LevelOfExpectationsMcpTools.NoteSectionDraft;
import de.westarps.topteacher.mcp.LevelOfExpectationsMcpTools.PartDraft;
import de.westarps.topteacher.mcp.LevelOfExpectationsMcpTools.RequirementDraft;
import de.westarps.topteacher.mcp.LevelOfExpectationsMcpTools.TaskDraft;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;
import de.westarps.topteacher.model.loe.ExamNoteSection;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeTask;

class LevelOfExpectationsMcpToolsTests {

	private final CourseRepository courses = mock(CourseRepository.class);
	private final ExamRepository exams = mock(ExamRepository.class);
	private final GradingScaleRepository gradingScales = mock(GradingScaleRepository.class);
	private final LevelOfExpectationsRepository levelOfExpectations = mock(LevelOfExpectationsRepository.class);
	private final LevelOfExpectationsMcpTools tools = new LevelOfExpectationsMcpTools(courses, exams, gradingScales,
			levelOfExpectations);

	private final Exam exam = new Exam(7, 3, "Klausur", LocalDate.of(2026, 9, 18), null, null);
	private final Course course = new Course(3, SchoolClass.CLS_Q1, new Subject(2, "Englisch", Lifecycle.ACTIVE),
			new SchoolYear(2026), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, 5);

	@BeforeEach
	void setUpContext() {
		when(exams.findById(exam.id())).thenReturn(Optional.of(exam));
		when(courses.findById(course.id())).thenReturn(Optional.of(course));
	}

	@Test
	void createsANestedDesignAndDerivesSortOrderFromTheRequest() {
		when(levelOfExpectations.savePart(any())).thenReturn(new LoePart(11, exam.id(), "Teil A", 0));
		when(levelOfExpectations.saveCategory(any())).thenReturn(new LoeCategory(12, 11, "Inhalt", "Beschreibung", 0));
		when(levelOfExpectations.saveTask(any())).thenReturn(new LoeTask(13, 12, "Aufgabe 1", 0));
		when(levelOfExpectations.saveRequirement(any()))
				.thenReturn(new LoeRequirement(14, 13, "Erreicht [das Ziel](eh:1).", 5, false, 0));
		when(levelOfExpectations.saveNoteSection(any()))
				.thenReturn(new ExamNoteSection(15, exam.id(), "Hinweis", "Notiz", 0));

		final RequirementDraft requirement = new RequirementDraft("Erreicht [das Ziel](eh:1).", 5, false);
		final List<PartDraft> parts = List.of(new PartDraft("Teil A", List.of(new CategoryDraft("Inhalt",
				"Beschreibung", List.of(new TaskDraft("Aufgabe 1", List.of(requirement)))))));

		final LevelOfExpectationsView result = tools.createLevelOfExpectations(exam.id(), parts,
				List.of(new NoteSectionDraft("Hinweis", "Notiz")));

		assertThat(result.exam().id()).isEqualTo(exam.id());
		verify(levelOfExpectations).savePart(new LoePart(null, exam.id(), "Teil A", 0));
		verify(levelOfExpectations).saveCategory(new LoeCategory(null, 11, "Inhalt", "Beschreibung", 0));
		verify(levelOfExpectations).saveTask(new LoeTask(null, 12, "Aufgabe 1", 0));
		verify(levelOfExpectations)
				.saveRequirement(new LoeRequirement(null, 13, "Erreicht [das Ziel](eh:1).", 5, false, 0));
		verify(levelOfExpectations).saveNoteSection(new ExamNoteSection(null, exam.id(), "Hinweis", "Notiz", 0));
	}

	@Test
	void refusesToOverwriteAnExistingDesign() {
		when(levelOfExpectations.findPartsByExamId(exam.id()))
				.thenReturn(List.of(new LoePart(11, exam.id(), "Vorhanden", 0)));

		assertThatThrownBy(
				() -> tools.createLevelOfExpectations(exam.id(), List.of(new PartDraft("Neu", List.of())), List.of()))
						.isInstanceOf(IllegalStateException.class)
						.hasMessageContaining("already has a level of expectations");

		verify(levelOfExpectations, never()).savePart(any());
	}

	@Test
	void rejectsAnUnboundedDraftBeforeWritingAnything() {
		final String oversizedTitle = "x".repeat(501);

		assertThatThrownBy(() -> tools.createLevelOfExpectations(exam.id(),
				List.of(new PartDraft(oversizedTitle, List.of())), List.of()))
						.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("part title");

		verify(levelOfExpectations, never()).savePart(any());
	}
}
