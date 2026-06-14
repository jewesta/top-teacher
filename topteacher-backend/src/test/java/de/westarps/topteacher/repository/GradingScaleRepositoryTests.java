package de.westarps.topteacher.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradeLevel;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.GradingScaleRange;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

@SpringBootTest
class GradingScaleRepositoryTests {

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private ExamRepository examRepository;

	@Autowired
	private GradingScaleRepository gradingScaleRepository;

	@Autowired
	private SubjectRepository subjectRepository;

	@Test
	void savesAndFindsGradingScalesWithRanges() {
		final GradingScale saved = gradingScaleRepository
				.save(new GradingScale(null, "Repository Scale 100", 100, Lifecycle.ACTIVE));
		final GradingScaleRange topRange = gradingScaleRepository
				.saveRange(new GradingScaleRange(null, saved.id(), GradeLevel.SEHR_GUT_PLUS, 95, 100));
		final GradingScaleRange bottomRange = gradingScaleRepository
				.saveRange(new GradingScaleRange(null, saved.id(), GradeLevel.UNGENUEGEND, 0, 19));

		assertThat(saved.id()).isNotNull();
		assertThat(saved.getDisplayName()).isEqualTo("Repository Scale 100 (100 Punkte)");
		assertThat(gradingScaleRepository.findById(saved.id())).contains(saved);
		assertThat(gradingScaleRepository.findActive()).contains(saved);
		assertThat(gradingScaleRepository.findRangesByGradingScaleId(saved.id())).containsExactly(topRange,
				bottomRange);
	}

	@Test
	void savesCompleteGradingScaleWithRanges() {
		final GradingScale saved = gradingScaleRepository.saveWithRanges(
				new GradingScale(null, "Repository Complete Scale 100", 100, Lifecycle.ACTIVE), fullRanges(0));

		assertThat(saved.id()).isNotNull();
		assertThat(gradingScaleRepository.findRangesByGradingScaleId(saved.id())).hasSize(16).first()
				.extracting(GradingScaleRange::gradeLevel).isEqualTo(GradeLevel.SEHR_GUT_PLUS);
	}

	@Test
	void savesSameNameWithDifferentMaximumPoints() {
		final GradingScale first = gradingScaleRepository
				.save(new GradingScale(null, "Repository Duplicate Name", 150, Lifecycle.ACTIVE));
		final GradingScale second = gradingScaleRepository
				.save(new GradingScale(null, "Repository Duplicate Name", 160, Lifecycle.ACTIVE));

		assertThat(first.id()).isNotEqualTo(second.id());
		assertThat(gradingScaleRepository.findAll()).contains(first, second);
		assertThat(first.getDisplayName()).isEqualTo("Repository Duplicate Name (150 Punkte)");
		assertThat(second.getDisplayName()).isEqualTo("Repository Duplicate Name (160 Punkte)");
	}

	@Test
	void rejectsIncompleteOrGappedGradingScaleRanges() {
		assertThatThrownBy(() -> gradingScaleRepository.saveWithRanges(
				new GradingScale(null, "Repository Incomplete Scale 100", 100, Lifecycle.ACTIVE),
				fullRanges(0).subList(0, 15))).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Ein Notenschlüssel muss genau 16 Notenpunkte enthalten.");

		final List<GradingScaleRange> gappedRanges = fullRanges(0).stream()
				.map(range -> range.gradeLevel() == GradeLevel.UNGENUEGEND
						? new GradingScaleRange(range.id(), range.gradingScaleId(), range.gradeLevel(), 0, 18)
						: range)
				.toList();

		assertThatThrownBy(() -> gradingScaleRepository.saveWithRanges(
				new GradingScale(null, "Repository Gapped Scale 100", 100, Lifecycle.ACTIVE), gappedRanges))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Die Punktebereiche müssen lückenlos von 0 bis zur Maximalpunktzahl reichen.");
	}

	@Test
	void rejectsChangingScaleOnceItIsUsedByAnExam() {
		final GradingScale saved = gradingScaleRepository.saveWithRanges(
				new GradingScale(null, "Repository Locked Scale 100", 100, Lifecycle.ACTIVE), fullRanges(0));
		final Course course = courseRepository.save(new Course(null, SchoolClass.CLS_5C, subject("Englisch"),
				new SchoolYear(2038), CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, saved.id()));
		examRepository.save(new Exam(null, course.id(), "1. Klausur", LocalDate.of(2038, 9, 17)));

		assertThat(gradingScaleRepository.isUsedByExam(saved.id())).isTrue();
		assertThatThrownBy(() -> gradingScaleRepository
				.save(new GradingScale(saved.id(), "Repository Locked Scale Updated 100", 100, Lifecycle.ACTIVE)))
				.isInstanceOf(IllegalArgumentException.class).hasMessage(
						"Dieser Notenschlüssel wird bereits von Klausuren verwendet und kann nicht mehr geändert werden.");
		assertThatThrownBy(() -> gradingScaleRepository
				.saveRange(new GradingScaleRange(null, saved.id(), GradeLevel.SEHR_GUT_PLUS, 94, 100)))
				.isInstanceOf(IllegalArgumentException.class).hasMessage(
						"Dieser Notenschlüssel wird bereits von Klausuren verwendet und kann nicht mehr geändert werden.");
	}

	private List<GradingScaleRange> fullRanges(final int gradingScaleId) {
		return List.of(new GradingScaleRange(null, gradingScaleId, GradeLevel.SEHR_GUT_PLUS, 95, 100),
				new GradingScaleRange(null, gradingScaleId, GradeLevel.SEHR_GUT, 90, 94),
				new GradingScaleRange(null, gradingScaleId, GradeLevel.SEHR_GUT_MINUS, 85, 89),
				new GradingScaleRange(null, gradingScaleId, GradeLevel.GUT_PLUS, 80, 84),
				new GradingScaleRange(null, gradingScaleId, GradeLevel.GUT, 75, 79),
				new GradingScaleRange(null, gradingScaleId, GradeLevel.GUT_MINUS, 70, 74),
				new GradingScaleRange(null, gradingScaleId, GradeLevel.BEFRIEDIGEND_PLUS, 65, 69),
				new GradingScaleRange(null, gradingScaleId, GradeLevel.BEFRIEDIGEND, 60, 64),
				new GradingScaleRange(null, gradingScaleId, GradeLevel.BEFRIEDIGEND_MINUS, 55, 59),
				new GradingScaleRange(null, gradingScaleId, GradeLevel.AUSREICHEND_PLUS, 50, 54),
				new GradingScaleRange(null, gradingScaleId, GradeLevel.AUSREICHEND, 45, 49),
				new GradingScaleRange(null, gradingScaleId, GradeLevel.AUSREICHEND_MINUS, 40, 44),
				new GradingScaleRange(null, gradingScaleId, GradeLevel.MANGELHAFT_PLUS, 34, 39),
				new GradingScaleRange(null, gradingScaleId, GradeLevel.MANGELHAFT, 27, 33),
				new GradingScaleRange(null, gradingScaleId, GradeLevel.MANGELHAFT_MINUS, 20, 26),
				new GradingScaleRange(null, gradingScaleId, GradeLevel.UNGENUEGEND, 0, 19));
	}

	private Subject subject(final String name) {
		return subjectRepository.findAll().stream().filter(candidate -> candidate.name().equals(name)).findFirst()
				.orElseThrow();
	}
}
