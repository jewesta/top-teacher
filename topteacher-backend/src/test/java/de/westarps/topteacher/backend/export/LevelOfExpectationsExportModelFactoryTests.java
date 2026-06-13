package de.westarps.topteacher.backend.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.westarps.topteacher.backend.export.LevelOfExpectationsExportModelFactory.LevelOfExpectationsExportData;
import de.westarps.topteacher.backend.export.LevelOfExpectationsExportModelFactory.LevelOfExpectationsExportModel;
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
import de.westarps.topteacher.model.loe.ExamNoteSection;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeCriterion;
import de.westarps.topteacher.model.loe.LoeCriterionResult;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;

class LevelOfExpectationsExportModelFactoryTests {

	private final LevelOfExpectationsExportModelFactory factory = new LevelOfExpectationsExportModelFactory(
			new Sanitizer());

	@Test
	void createsPupilFacingExportModelWithSanitizedMarkdownAndAggregatedPoints() {
		final LevelOfExpectationsExportModel model = factory.createPupilModel(data());

		assertThat(model.course().getDisplayName()).contains("Englisch");
		assertThat(model.examDateDisplayName()).isEqualTo("21.05.2026");
		assertThat(model.parts()).hasSize(1);
		assertThat(model.points().maxDisplayName()).isEqualTo("6 (+ 2)");
		assertThat(model.points().achievedDisplayName()).isEqualTo("4 (+ 1)");
		assertThat(model.totalGradeDisplayName()).isEqualTo("ungenügend");
		assertThat(model.gradingScaleTableRows().getFirst().cells().stream()
				.map(LevelOfExpectationsExportModelFactory.GradingScaleTableCell::gradeDisplayName).toList())
				.containsExactly("sehr gut plus", "ungenügend", "");
		assertThat(model.gradingScaleTableRows().getFirst().cells().getFirst().minPointsDisplayName()).isEqualTo("95");
		assertThat(model.gradingScaleTableRows().getFirst().cells().getFirst().maxPointsDisplayName()).isEqualTo("100");

		final var firstRequirement = model.parts().getFirst().categories().getFirst().tasks().getFirst().requirements()
				.getFirst();
		assertThat(firstRequirement.description().value()).contains("<strong>Zeitform</strong>");
		assertThat(firstRequirement.description().value()).doesNotContain("eh:1", "tt-criterion", "mark");
		assertThat(firstRequirement.comment()).isEmpty();

		final var bonusRequirement = model.parts().getFirst().categories().getFirst().tasks().getFirst().requirements()
				.get(1);
		assertThat(bonusRequirement.maxPointsDisplayName()).isEqualTo("(2)");
		assertThat(bonusRequirement.achievedPointsDisplayName()).isEqualTo("(1)");

		assertThat(model.noteSections()).isEmpty();
	}

	@Test
	void displaysZeroBonusPointsInBraces() {
		final var requirement = new LevelOfExpectationsExportModelFactory.Requirement(1, new SafeHtml("Bonuspunkt"), 0,
				true, 0, "");

		assertThat(requirement.maxPointsDisplayName()).isEqualTo("(0)");
		assertThat(requirement.achievedPointsDisplayName()).isEqualTo("(0)");
	}

	@Test
	void canCreateTeacherFacingExportModelWithCriterionRendering() {
		final LevelOfExpectationsExportModel model = factory.createTeacherModel(data());

		final var firstRequirement = model.parts().getFirst().categories().getFirst().tasks().getFirst().requirements()
				.getFirst();
		assertThat(firstRequirement.comment()).isEqualTo("Sauber");
		assertThat(firstRequirement.description().value()).contains("tt-criterion");
		assertThat(firstRequirement.description().value()).contains("tt-criterion-badge");
		assertThat(firstRequirement.description().value()).contains("tt-criterion-marker");
		assertThat(model.noteSections().getFirst().description().value()).contains("<em>Notiz</em>");
	}

	private static LevelOfExpectationsExportData data() {
		return new LevelOfExpectationsExportData(
				new Course(1, SchoolClass.CLS_5A, Subject.ENGLISH, new SchoolYear(2026), CoursePeriod.FULL_YEAR,
						Lifecycle.ACTIVE, 1),
				new Exam(1, 1, "Klausur Nr. 1", LocalDate.of(2026, 5, 21)),
				new Pupil(1, "Anna", "Muster", Lifecycle.ACTIVE),
				new GradingScale(1, "Standard", 100, Lifecycle.ACTIVE),
				List.of(new GradingScaleRange(1, 1, GradeLevel.SEHR_GUT_PLUS, 95, 100),
						new GradingScaleRange(2, 1, GradeLevel.UNGENUEGEND, 0, 19)),
				List.of(new LoePart(1, 1, "Klausurteil A", 0)),
				List.of(new LoeCategory(1, 1, "Inhaltliche Leistung", "", 0)),
				List.of(new LoeTask(1, 1, "Teilaufgabe 1", 0)),
				List.of(new LoeRequirement(1, 1, "**[Zeitform](eh:1)** und [Wortwahl](eh:2) nutzen", 6, false, 0),
						new LoeRequirement(2, 1, "Bonuspunkt", 2, true, 1)),
				List.of(new LoeRequirementResult(1, 1, 4, "Sauber"), new LoeRequirementResult(2, 1, 1, "")),
				List.of(new LoeCriterion(10, 1, "1", "Zeitform", 0, true),
						new LoeCriterion(11, 1, "2", "Wortwahl", 1, true)),
				List.of(new LoeCriterionResult(10, 1, true), new LoeCriterionResult(11, 1, false)),
				List.of(new ExamNoteSection(1, 1, "Hinweis", "*Notiz*", 0)));
	}
}
