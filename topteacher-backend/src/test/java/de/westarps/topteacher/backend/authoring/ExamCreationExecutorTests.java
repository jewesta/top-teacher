package de.westarps.topteacher.backend.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.Subject;

@SpringBootTest
class ExamCreationExecutorTests {

	@Autowired private ExamCreationExecutor creations;
	@Autowired private ExamRepository exams;
	@Autowired private CourseRepository courses;
	@Autowired private SubjectRepository subjects;
	@Autowired private GradingScaleRepository scales;
	@Autowired private LevelOfExpectationsRepository design;
	@Autowired private JdbcTemplate jdbc;

	@Test
	void retriesReturnTheSameExamWithoutRunningCreationAgain() {
		final Course course = course();
		final AtomicInteger invoked = new AtomicInteger();
		final String hash = ExamMutationExecutor.fingerprint("shell", course.id());
		final var created = creations.execute(course.id(), "retry", "CREATE_EXAM", hash, () -> {
			invoked.incrementAndGet();
			return shell(course, "New exam").id();
		});
		design.saveExamFooterMarkdown(created.examId(), "Later academic edit");
		final var replay = creations.execute(course.id(), "retry", "CREATE_EXAM", hash, () -> {
			throw new AssertionError("A retry must not create another exam");
		});
		assertThat(replay.examId()).isEqualTo(created.examId());
		assertThat(replay.mutationRevision()).isEqualTo(created.mutationRevision());
		assertThat(replay.replayed()).isTrue();
		assertThat(invoked).hasValue(1);
		assertThat(exams.findByCourseId(course.id())).hasSize(1);
		assertThatThrownBy(() -> creations.execute(course.id(), "retry", "CREATE_EXAM",
				ExamMutationExecutor.fingerprint("different"), () -> shell(course, "Other").id()))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("different exam creation");
	}

	@Test
	void failedCreationRollsBackShellAssignmentsAndReceipt() {
		final Course course = course();
		assertThatThrownBy(() -> creations.execute(course.id(), "rollback", "CREATE_EXAM",
				ExamMutationExecutor.fingerprint("rollback"), () -> {
					shell(course, "Rollback");
					throw new IllegalArgumentException("Invalid requested content");
				})).isInstanceOf(IllegalArgumentException.class);
		assertThat(exams.findByCourseId(course.id())).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from exam_creation_mutation where course_id = ?",
				Integer.class, course.id())).isZero();
	}

	@Test
	void rejectsInvalidKeysAndInactiveCoursesBeforeInvokingCreation() {
		final Course course = course();
		assertThatThrownBy(() -> creations.execute(course.id(), "bad key", "CREATE_EXAM",
				ExamMutationExecutor.fingerprint("valid"), () -> { throw new AssertionError(); }))
				.isInstanceOf(IllegalArgumentException.class);
		courses.save(new Course(course.id(), course.schoolClass(), course.subject(), course.schoolYear(),
				course.coursePeriod(), Lifecycle.INACTIVE, course.gradingScaleId()));
		assertThatThrownBy(() -> creations.execute(course.id(), "valid", "CREATE_EXAM",
				ExamMutationExecutor.fingerprint("valid"), () -> { throw new AssertionError(); }))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("active course");
	}

	@Test
	void concurrentIdenticalCreatesAreSerializedAndProduceOneShell() throws Exception {
		final Course course = course();
		final String hash = ExamMutationExecutor.fingerprint("concurrent");
		final CountDownLatch started = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		final var first = CompletableFuture.supplyAsync(() -> creations.execute(course.id(), "same", "CREATE_EXAM", hash, () -> {
			started.countDown();
			await(release);
			return shell(course, "Concurrent").id();
		}));
		assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
		final var second = CompletableFuture.supplyAsync(() -> creations.execute(course.id(), "same", "CREATE_EXAM", hash,
				() -> { throw new AssertionError("Concurrent retry must not create"); }));
		try {
			assertThat(second.isDone()).isFalse();
		} finally {
			release.countDown();
		}
		final var created = first.get(5, TimeUnit.SECONDS);
		final var replay = second.get(5, TimeUnit.SECONDS);
		assertThat(replay.examId()).isEqualTo(created.examId());
		assertThat(replay.replayed()).isTrue();
		assertThat(exams.findByCourseId(course.id())).hasSize(1);
	}

	private static void await(final CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new AssertionError("Creation test timed out");
			}
		} catch (final InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new AssertionError(interrupted);
		}
	}

	private Exam shell(final Course course, final String title) {
		return exams.save(new Exam(null, course.id(), title, LocalDate.of(2078, 5, 1)), List.of());
	}

	private Course course() {
		final Subject subject = subjects.save(new Subject(null, "Creation " + UUID.randomUUID(), Lifecycle.ACTIVE));
		return courses.save(new Course(null, SchoolClass.CLS_Q1, subject, new SchoolYear(2078), CoursePeriod.FULL_YEAR,
				Lifecycle.ACTIVE, scales.findActive().getFirst().id()));
	}
}
