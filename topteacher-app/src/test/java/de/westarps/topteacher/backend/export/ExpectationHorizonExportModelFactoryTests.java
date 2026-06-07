package de.westarps.topteacher.backend.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.westarps.topteacher.backend.export.ExpectationHorizonExportModelFactory.ExpectationHorizonExportData;
import de.westarps.topteacher.backend.export.ExpectationHorizonExportModelFactory.ExpectationHorizonExportModel;
import de.westarps.topteacher.backend.export.Sanitizer.MarkdownView;
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
import de.westarps.topteacher.model.eh.EhCategory;
import de.westarps.topteacher.model.eh.EhPart;
import de.westarps.topteacher.model.eh.EhRequirement;
import de.westarps.topteacher.model.eh.EhRequirementResult;
import de.westarps.topteacher.model.eh.EhTask;
import de.westarps.topteacher.model.eh.ExamNoteSection;

class ExpectationHorizonExportModelFactoryTests {

	private final ExpectationHorizonExportModelFactory factory =
			new ExpectationHorizonExportModelFactory(new Sanitizer());

	@Test
	void createsPupilFacingExportModelWithSanitizedMarkdownAndAggregatedPoints() {
		final ExpectationHorizonExportModel model = factory.createPupilModel(data());

		assertThat(model.course().getDisplayName()).contains("Englisch");
		assertThat(model.parts()).hasSize(1);
		assertThat(model.points().maxDisplayName()).isEqualTo("6 (+ 2)");
		assertThat(model.points().achievedDisplayName()).isEqualTo("4 (+ 1)");

		final var firstRequirement = model.parts().getFirst().categories().getFirst().tasks().getFirst()
				.requirements().getFirst();
		assertThat(firstRequirement.description().value()).contains("<strong>Zeitform</strong>");
		assertThat(firstRequirement.description().value()).doesNotContain("eh:1", "tt-criterion", "mark");
		assertThat(firstRequirement.comment()).isEqualTo("Sauber");

		final var bonusRequirement = model.parts().getFirst().categories().getFirst().tasks().getFirst()
				.requirements().get(1);
		assertThat(bonusRequirement.maxPointsDisplayName()).isEqualTo("(2)");
		assertThat(bonusRequirement.achievedPointsDisplayName()).isEqualTo("(1)");

		assertThat(model.noteSections().getFirst().description().value()).contains("<em>Notiz</em>");
	}

	@Test
	void displaysZeroBonusPointsInBraces() {
		final var requirement = new ExpectationHorizonExportModelFactory.Requirement(1, new SafeHtml("Bonuspunkt"),
				0, true, 0, "");

		assertThat(requirement.maxPointsDisplayName()).isEqualTo("(0)");
		assertThat(requirement.achievedPointsDisplayName()).isEqualTo("(0)");
	}

	@Test
	void canCreateTeacherFacingExportModelWithCriterionRendering() {
		final ExpectationHorizonExportModel model = factory.createPupilModel(data(), MarkdownView.TEACHER);

		final var firstRequirement = model.parts().getFirst().categories().getFirst().tasks().getFirst()
				.requirements().getFirst();
		assertThat(firstRequirement.description().value()).contains("tt-criterion");
		assertThat(firstRequirement.description().value()).contains("tt-criterion-badge");
	}

	private static ExpectationHorizonExportData data() {
		return new ExpectationHorizonExportData(
				new Course(1, SchoolClass.CLS_5A, Subject.ENGLISH, new SchoolYear(2026), CoursePeriod.FULL_YEAR,
						Lifecycle.ACTIVE, 1),
				new Exam(1, 1, "Klausur Nr. 1", LocalDate.of(2026, 5, 21)),
				new Pupil(1, "Anna", "Muster", Lifecycle.ACTIVE),
				new GradingScale(1, "Standard", 100, Lifecycle.ACTIVE),
				List.of(new GradingScaleRange(1, 1, GradeLevel.SEHR_GUT_PLUS, 95, 100)),
				List.of(new EhPart(1, 1, "Klausurteil A", 0)),
				List.of(new EhCategory(1, 1, "Inhaltliche Leistung", "", 0)),
				List.of(new EhTask(1, 1, "Teilaufgabe 1", 0)),
				List.of(
						new EhRequirement(1, 1, "**[Zeitform](eh:1)** nutzen", 6, false, 0),
						new EhRequirement(2, 1, "Bonuspunkt", 2, true, 1)),
				List.of(
						new EhRequirementResult(1, 1, 4, "Sauber"),
						new EhRequirementResult(2, 1, 1, "")),
				List.of(new ExamNoteSection(1, 1, "Hinweis", "*Notiz*", 0)));
	}
}
