package de.westarps.topteacher.backend.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.backend.repo.SettingsRepository;
import de.westarps.topteacher.backend.settings.AppSettings;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;

@SpringBootTest
@Sql("/db/demo-data.sql")
class LevelOfExpectationsExportServiceTests {

	@Autowired
	private LevelOfExpectationsExportService exportService;

	@Autowired
	private LevelOfExpectationsExportController exportController;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private ExamRepository examRepository;

	@Autowired
	private PupilRepository pupilRepository;

	@Autowired
	private SettingsRepository settingsRepository;

	@Test
	void rendersReviewedDemoLevelOfExpectationsAsPupilHtml() {
		final DemoSelection demo = findReviewedDemoSelection();

		final String html = exportService.renderPupilHtml(demo.exam().id(), demo.pupil().id());

		assertThat(html).contains("10a_Erdkunde");
		assertThat(html).contains("Klausur Nr. 1");
		assertThat(html).contains("Mia Weber");
		assertThat(html).contains("Klausur: Windenergie, Klimawandel und Energiewende");
		assertThat(html).contains("Aufgabe 3: Transformation des Energiesektors beurteilen");
		assertThat(html).contains("GESAMTPUNKTZAHL KLAUSUR");
		assertThat(html).contains("gut plus");
		assertThat(html).doesNotContain("eh:", "tt-criterion", "tt-criterion-badge");
		assertThat(html).doesNotContain("Hinweise / Tipps", "Klares Fazit");
	}

	@Test
	void rendersReviewedDemoLevelOfExpectationsAsTeacherHtml() {
		final DemoSelection demo = findReviewedDemoSelection();

		final String html = exportService.renderTeacherHtml(demo.exam().id(), demo.pupil().id());

		assertThat(html).contains("Lehrer:innen-Version");
		assertThat(html).contains("tt-teacher-watermark");
		assertThat(html).contains("tt-criterion-highlight");
		assertThat(html).contains("tt-criterion-marker");
		assertThat(html).contains("Hinweise / Tipps");
		assertThat(html).contains("Beim Überarbeiten besonders auf Materialbelege");
		assertThat(html).contains("Klares Fazit");
		assertThat(html).doesNotContain("Notiz: ");
	}

	@Test
	void hidesTeacherWatermarkWhenSettingIsDisabled() {
		final DemoSelection demo = findReviewedDemoSelection();

		settingsRepository.save(AppSettings.TT_LOE_EXPORT_SHOW_WATERMARK_KEY, "false");
		try {
			final String html = exportService.renderTeacherHtml(demo.exam().id(), demo.pupil().id());

			assertThat(html).doesNotContain("tt-teacher-watermark");
		} finally {
			settingsRepository.save(AppSettings.TT_LOE_EXPORT_SHOW_WATERMARK_KEY, "true");
		}
	}

	@Test
	void rendersReviewedDemoLevelOfExpectationsAsA4LandscapePdf() throws IOException {
		final DemoSelection demo = findReviewedDemoSelection();

		final byte[] pdf = exportService.renderPupilA4LandscapePdf(demo.exam().id(), demo.pupil().id());

		assertThat(pdf).startsWith("%PDF".getBytes());
		try (PDDocument document = PDDocument.load(pdf)) {
			assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
			assertThat(document.getPage(0).getMediaBox().getWidth()).isCloseTo(PDRectangle.A4.getHeight(),
					org.assertj.core.data.Offset.offset(1f));
			assertThat(document.getPage(0).getMediaBox().getHeight()).isCloseTo(PDRectangle.A4.getWidth(),
					org.assertj.core.data.Offset.offset(1f));
		}
	}

	@Test
	void rendersTeacherNotesInReviewedDemoTeacherLevelOfExpectationsPdf() throws IOException {
		final DemoSelection demo = findReviewedDemoSelection();

		final byte[] pdf = exportService.renderTeacherA4LandscapePdf(demo.exam().id(), demo.pupil().id());

		try (PDDocument document = PDDocument.load(pdf)) {
			final String text = new PDFTextStripper().getText(document);
			assertThat(text).contains("Beim Überarbeiten besonders auf Materialbelege");
			assertThat(text).contains("Klares Fazit");
			assertThat(text).doesNotContain("Notiz:");
		}
	}

	@Test
	void exportsReviewedDemoLevelOfExpectationsThroughDownloadController() {
		final DemoSelection demo = findReviewedDemoSelection();

		final ResponseEntity<byte[]> response = exportController.exportPupilLevelOfExpectations(demo.exam().id(),
				demo.pupil().id());

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
		assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
				.isEqualTo("attachment; filename=erwartungshorizont-klausur-windenergie-und-klimawandel-weber-mia.pdf");
		assertThat(response.getBody()).startsWith("%PDF".getBytes());
	}

	@Test
	void exportsReviewedDemoTeacherLevelOfExpectationsThroughDownloadController() {
		final DemoSelection demo = findReviewedDemoSelection();

		final ResponseEntity<byte[]> response = exportController.exportTeacherLevelOfExpectations(demo.exam().id(),
				demo.pupil().id());

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
		assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo(
				"attachment; filename=lehrerversion-erwartungshorizont-klausur-windenergie-und-klimawandel-weber-mia.pdf");
		assertThat(response.getBody()).startsWith("%PDF".getBytes());
	}

	private DemoSelection findReviewedDemoSelection() {
		final Course course = courseRepository.findAll().stream()
				.filter(candidate -> candidate.schoolClass() == SchoolClass.CLS_10A)
				.filter(candidate -> candidate.subject().name().equals("Erdkunde"))
				.filter(candidate -> candidate.schoolYear().getCalendarYear() == 2026).findFirst().orElseThrow();
		final Exam exam = examRepository.findByCourseId(course.id()).stream()
				.filter(candidate -> candidate.title().equals("Klausur Windenergie und Klimawandel")).findFirst()
				.orElseThrow();
		final Pupil pupil = pupilRepository.findAll().stream().filter(candidate -> candidate.name().equals("Mia"))
				.filter(candidate -> candidate.surname().equals("Weber")).findFirst().orElseThrow();
		return new DemoSelection(exam, pupil);
	}

	private record DemoSelection(Exam exam, Pupil pupil) {
	}
}
