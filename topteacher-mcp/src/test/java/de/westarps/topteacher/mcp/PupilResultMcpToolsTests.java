package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.mcp.PupilResultMcpTools.PupilResultView;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradeLevel;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.GradingScaleRange;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;

class PupilResultMcpToolsTests {

	private final ExamRepository exams = mock(ExamRepository.class);
	private final GradingScaleRepository gradingScales = mock(GradingScaleRepository.class);
	private final LevelOfExpectationsRepository levelOfExpectations = mock(LevelOfExpectationsRepository.class);
	private final PupilRepository pupils = mock(PupilRepository.class);
	private final PupilResultMcpTools tools = new PupilResultMcpTools(exams, gradingScales, levelOfExpectations,
			pupils);

	@Test
	void returnsCappedPointsAndTheMatchingGradeForAPupil() {
		final Exam exam = new Exam(7, 3, "Klausur", LocalDate.of(2026, 9, 18), null, 9);
		final Pupil pupil = new Pupil(21, "Ada", "Lovelace", Lifecycle.ACTIVE);
		final LoeRequirement regular = new LoeRequirement(31, 1, "Regulär", 5, false, 0);
		final LoeRequirement bonus = new LoeRequirement(32, 1, "Bonus", 2, true, 1);
		when(exams.findById(exam.id())).thenReturn(Optional.of(exam));
		when(exams.hasPupil(exam.id(), pupil.id())).thenReturn(true);
		when(pupils.findById(pupil.id())).thenReturn(Optional.of(pupil));
		when(levelOfExpectations.findRequirementsByExamId(exam.id())).thenReturn(List.of(regular, bonus));
		when(levelOfExpectations.findRequirementResultsByExamAndPupil(exam.id(), pupil.id()))
				.thenReturn(List.of(new LoeRequirementResult(regular.id(), pupil.id(), 4),
						new LoeRequirementResult(bonus.id(), pupil.id(), 2)));
		when(gradingScales.findById(9))
				.thenReturn(Optional.of(new GradingScale(9, "Fünf Punkte", 5, Lifecycle.ACTIVE)));
		when(gradingScales.findRangesByGradingScaleId(9))
				.thenReturn(List.of(new GradingScaleRange(1, 9, GradeLevel.GUT, 5, 5)));

		final PupilResultView result = tools.getPupilResult(exam.id(), pupil.id());

		assertThat(result.summary().regularPoints()).isEqualTo(4);
		assertThat(result.summary().bonusPoints()).isEqualTo(2);
		assertThat(result.summary().effectivePoints()).isEqualTo(5);
		assertThat(result.summary().grade()).isEqualTo("2");
		assertThat(result.summary().gradingAvailable()).isTrue();
	}
}
