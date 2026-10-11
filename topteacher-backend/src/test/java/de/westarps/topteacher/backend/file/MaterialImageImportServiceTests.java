package de.westarps.topteacher.backend.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import de.westarps.topteacher.backend.authoring.ExamDesignGuard;
import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.backend.repo.SharedFolderMappingRepository;
import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.backend.settings.AppSettings;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.CoursePeriod;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.InstallationLocation;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.SchoolYear;
import de.westarps.topteacher.model.SharedFolderMapping;
import de.westarps.topteacher.model.Subject;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences.Alignment;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeRequirement;
import de.westarps.topteacher.model.loe.LoeRequirementResult;
import de.westarps.topteacher.model.loe.LoeTask;

@SpringBootTest
class MaterialImageImportServiceTests {

	@Autowired private MaterialImageImportService images;
	@Autowired private LevelOfExpectationsRepository levels;
	@Autowired private ExamDesignGuard guard;
	@Autowired private ExamRepository exams;
	@Autowired private CourseRepository courses;
	@Autowired private SubjectRepository subjects;
	@Autowired private GradingScaleRepository scales;
	@Autowired private PupilRepository pupils;
	@Autowired private AppSettings settings;
	@Autowired private SharedFolderMappingRepository mappings;
	@Autowired private JdbcTemplate jdbc;
	@TempDir private Path tempDir;
	private Exam exam;
	private LoePart part;
	private LoeMaterial material;
	private Path source;

	@BeforeEach
	void setUp() throws Exception {
		settings.saveTtFileAccessInstallationLocation(InstallationLocation.LOCAL);
		exam = createExam();
		part = levels.savePart(new LoePart(null, exam.id(), "Part", 0));
		material = levels.saveMaterial(new LoeMaterial(null, part.id(), "Material", "Source text", 0));
		source = tempDir.resolve("chart.png");
		final BufferedImage image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, UUID.randomUUID().hashCode());
		ImageIO.write(image, "png", source.toFile());
	}

	@AfterEach
	void restoreLocalAccess() {
		settings.saveTtFileAccessInstallationLocation(InstallationLocation.LOCAL);
		mappings.replaceAll(List.of());
	}

	@Test
	void serializesBracketsAndImageSyntaxInCaptionsAsOneRealRenderedImage() {
		final var imported = images.importImage(exam.id(), material.id(), source.toString(),
				"Chart [A] (img:deadbeef) \\ ]", null, guard.revision(exam.id()), UUID.randomUUID().toString());
		final String markdown = imported.material().descriptionMarkdown();
		final String rendered = HtmlRenderer.builder().build().render(Parser.builder().build().parse(markdown));

		assertThat(LoeMaterialImageReferences.ids(markdown)).containsExactly(imported.metadata().imageId());
		assertThat(rendered).contains("<img src=\"img:" + imported.metadata().imageId() + "\"")
				.contains("alt=\"Chart [A] (img:deadbeef) \\ ]\"");
	}

	@Test
	void importsAndReferencesTheCanonicalImageThenReplaysWithoutReadingTheSourceAgain() throws Exception {
		final long revision = guard.revision(exam.id());
		final String requestId = UUID.randomUUID().toString();
		final var imported = images.importImage(exam.id(), material.id(), source.toString(), "Chart ] \\ caption", 75,
				revision, requestId);
		final var saved = levels.findMaterialsByExamId(exam.id()).getFirst();

		assertThat(imported.replayed()).isFalse();
		assertThat(imported.revision()).isGreaterThan(revision);
		assertThat(imported.mutationRevision()).isEqualTo(imported.revision());
		assertThat(imported.material()).isEqualTo(saved);
		assertThat(saved.descriptionMarkdown()).startsWith("Source text\n\n![Chart \\] \\\\ caption]")
				.contains("(img:" + imported.metadata().imageId() + "?width=75)");
		assertThat(images.getImage(exam.id(), material.id(), imported.metadata().imageId()).mediaType())
				.isEqualTo("image/png");
		Files.delete(source);

		final var replay = images.importImage(exam.id(), material.id(), source.toString(), "Chart ] \\ caption", 75,
				revision, requestId);

		assertThat(replay.replayed()).isTrue();
		assertThat(replay.metadata()).isEqualTo(imported.metadata());
		assertThat(replay.revision()).isEqualTo(imported.revision());
		assertThat(levels.findMaterialsByExamId(exam.id()).getFirst().descriptionMarkdown())
				.isEqualTo(saved.descriptionMarkdown());
		assertThatThrownBy(() -> images.importImage(exam.id(), material.id(), source.toString(), "Changed", 75,
				revision, requestId)).isInstanceOf(IllegalArgumentException.class);
		levels.saveExamFooterMarkdown(exam.id(), "Later edit");
		final var laterReplay = images.importImage(exam.id(), material.id(), source.toString(), "Chart ] \\ caption", 75,
				revision, requestId);
		assertThat(laterReplay.revision()).isEqualTo(guard.revision(exam.id())).isGreaterThan(imported.revision());
		assertThat(laterReplay.mutationRevision()).isEqualTo(imported.mutationRevision());
	}

	@Test
	void importsAnAlignedImageUsingTheCanonicalDeclaration() {
		final var imported = images.importImage(exam.id(), material.id(), source.toString(), "Centered chart", 50,
				Alignment.CENTER, guard.revision(exam.id()), UUID.randomUUID().toString());

		assertThat(imported.material().descriptionMarkdown()).contains(
				"![Centered chart](img:" + imported.metadata().imageId() + "?width=50)");
	}

	@Test
	void deduplicatesImportsAndRejectsImagesOutsideTheirMaterialAndExamScope() {
		final var first = images.importImage(exam.id(), material.id(), source.toString(), "First", null,
				guard.revision(exam.id()), UUID.randomUUID().toString());
		assertThat(first.material().descriptionMarkdown()).contains("![First](img:" + first.metadata().imageId() + ")")
				.doesNotContain("?width=");
		final LoeMaterial other = levels.saveMaterial(new LoeMaterial(null, part.id(), "Other", "", 1));
		assertThatThrownBy(() -> images.getImage(exam.id(), other.id(), first.metadata().imageId()))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not referenced");
		final var second = images.importImage(exam.id(), other.id(), source.toString(), "Second", null,
				guard.revision(exam.id()), UUID.randomUUID().toString());

		assertThat(second.metadata().imageId()).isEqualTo(first.metadata().imageId());
		assertThat(LoeMaterialImageReferences.ids(levels.findMaterialsByExamId(exam.id()).get(1).descriptionMarkdown()))
				.containsExactly(first.metadata().imageId());
		final Exam different = createExam();
		assertThatThrownBy(() -> images.getImage(different.id(), material.id(), first.metadata().imageId()))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not belong");
	}

	@Test
	void rejectsStaleRevisionsAndForgedMaterialIdsWithoutInsertingBlobs() {
		final long revision = guard.revision(exam.id());
		levels.saveExamFooterMarkdown(exam.id(), "Changed by the user");
		final long imageCount = imageCount();

		assertThatThrownBy(() -> images.importImage(exam.id(), material.id(), source.toString(), "Chart", 100,
				revision, UUID.randomUUID().toString())).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> images.importImage(exam.id(), Integer.MAX_VALUE, source.toString(), "Chart", 100,
				guard.revision(exam.id()), UUID.randomUUID().toString())).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("does not belong");
		assertThat(imageCount()).isEqualTo(imageCount);
	}

	@Test
	void boundsSourcePathsBeforeResolvingThem() {
		final long revision = guard.revision(exam.id());
		assertThatThrownBy(() -> images.importImage(exam.id(), material.id(), "/" + "a".repeat(4_096), "Chart", null,
				revision, UUID.randomUUID().toString())).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("4096");
		assertThat(guard.revision(exam.id())).isEqualTo(revision);
	}

	@Test
	void replaysNeutralMetadataAfterTheReferencedMaterialAndBlobHaveBeenDeleted() throws Exception {
		final long revision = guard.revision(exam.id());
		final String requestId = UUID.randomUUID().toString();
		final var imported = images.importImage(exam.id(), material.id(), source.toString(), "Chart", null,
				revision, requestId);
		levels.deleteMaterial(material.id());
		Files.delete(source);

		final var replay = images.importImage(exam.id(), material.id(), source.toString(), "Chart", null,
				revision, requestId);

		assertThat(replay.replayed()).isTrue();
		assertThat(replay.metadata()).isEqualTo(imported.metadata());
		assertThat(replay.material()).isNull();
		assertThat(replay.revision()).isEqualTo(guard.revision(exam.id())).isGreaterThan(imported.revision());
		assertThat(replay.mutationRevision()).isEqualTo(imported.mutationRevision());
	}

	@Test
	void resolvesRemoteSharesAndDoesNotExposeMappedServerPathsOnFailure() throws Exception {
		settings.saveTtFileAccessInstallationLocation(InstallationLocation.REMOTE);
		mappings.replaceAll(List.of(new SharedFolderMapping("/Volumes/School", tempDir.toString(), 0)));
		final var imported = images.importImage(exam.id(), material.id(), "/Volumes/School/chart.png", "Chart", 100,
				guard.revision(exam.id()), UUID.randomUUID().toString());
		assertThat(imported.metadata().imageId()).hasSize(8);
		Files.createSymbolicLink(tempDir.resolve("outside.png"), Path.of("/etc/hosts"));

		assertThatThrownBy(() -> images.importImage(exam.id(), material.id(), "/Volumes/School/outside.png", "Chart", 100,
				guard.revision(exam.id()), UUID.randomUUID().toString())).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("file-access scope").hasMessageNotContaining(tempDir.toString())
				.hasMessageNotContaining("/etc/hosts");
	}

	@Test
	void rollsBackTheBlobAndReferenceWhenTheMaterialAlreadyContainsABrokenImageReference() {
		jdbc.update("update loe_material set description_markdown = ? where id = ?",
				"![Missing](img:missing0)", material.id());
		final long revision = guard.revision(exam.id());
		final long imageCount = imageCount();

		assertThatThrownBy(() -> images.importImage(exam.id(), material.id(), source.toString(), "Chart", null,
				revision, UUID.randomUUID().toString())).isInstanceOf(IllegalArgumentException.class);
		assertThat(imageCount()).isEqualTo(imageCount);
		assertThat(guard.revision(exam.id())).isEqualTo(revision);
		assertThat(levels.findMaterialsByExamId(exam.id()).getFirst().descriptionMarkdown())
				.isEqualTo("![Missing](img:missing0)");
	}

	@Test
	void rollsBackAnImportThatWouldExceedTheSharedMarkdownLimit() {
		levels.saveMaterial(new LoeMaterial(material.id(), part.id(), material.title(), "x".repeat(49_950),
				material.sortOrder()));
		final long revision = guard.revision(exam.id());
		final long imageCount = imageCount();

		assertThatThrownBy(() -> images.importImage(exam.id(), material.id(), source.toString(), "caption".repeat(20),
				100, revision, UUID.randomUUID().toString())).isInstanceOf(IllegalArgumentException.class);
		assertThat(imageCount()).isEqualTo(imageCount);
		assertThat(guard.revision(exam.id())).isEqualTo(revision);
		assertThat(levels.findMaterialsByExamId(exam.id()).getFirst().descriptionMarkdown()).hasSize(49_950);
	}

	@Test
	void blocksImageImportsAfterTheFirstResultAndForArchivedCourses() {
		final LoeCategory category = levels.saveCategory(new LoeCategory(null, part.id(), "Content", "", 0));
		final LoeTask task = levels.saveTask(new LoeTask(null, category.id(), "Task", "", 100, 0));
		final LoeRequirement requirement = levels.saveRequirement(new LoeRequirement(null, task.id(), "Answer", 100, false, 0));
		final Pupil pupil = pupils.save(new Pupil(null, "Image", UUID.randomUUID().toString(), Lifecycle.ACTIVE));
		levels.saveRequirementResult(new LoeRequirementResult(requirement.id(), pupil.id(), 0, ""));
		final long imageCount = imageCount();

		assertThatThrownBy(() -> images.importImage(exam.id(), material.id(), source.toString(), "Chart", 100,
				guard.revision(exam.id()), UUID.randomUUID().toString())).isInstanceOf(IllegalStateException.class);
		final Exam archivedExam = createExam();
		final LoePart archivedPart = levels.savePart(new LoePart(null, archivedExam.id(), "Part", 0));
		final LoeMaterial archivedMaterial = levels.saveMaterial(new LoeMaterial(null, archivedPart.id(), "Material", "", 0));
		final Course course = courses.findById(archivedExam.courseId()).orElseThrow();
		courses.save(new Course(course.id(), course.schoolClass(), course.subject(), course.schoolYear(),
				course.coursePeriod(), Lifecycle.INACTIVE, course.gradingScaleId()));
		assertThatThrownBy(() -> images.importImage(archivedExam.id(), archivedMaterial.id(), source.toString(), "Chart", 100,
				guard.revision(archivedExam.id()), UUID.randomUUID().toString())).isInstanceOf(IllegalStateException.class);
		assertThat(imageCount()).isEqualTo(imageCount);
	}

	private long imageCount() {
		return jdbc.queryForObject("select count(*) from loe_material_image", Long.class);
	}

	private Exam createExam() {
		final String unique = UUID.randomUUID().toString();
		final Subject subject = subjects.save(new Subject(null, "Image " + unique, Lifecycle.ACTIVE));
		final GradingScale scale = scales.save(new GradingScale(null, "Image " + unique, 100, Lifecycle.ACTIVE));
		final Course course = courses.save(new Course(null, SchoolClass.CLS_10A, subject, new SchoolYear(2060),
				CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, scale.id()));
		return exams.save(new Exam(null, course.id(), "Images", LocalDate.of(2060, 9, 1)));
	}
}
