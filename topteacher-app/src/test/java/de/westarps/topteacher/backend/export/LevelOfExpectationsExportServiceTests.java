package de.westarps.topteacher.backend.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
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
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import de.westarps.topteacher.model.Subject;

@SpringBootTest
@Sql("/demo-data.sql")
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

	@Test
	void rendersReviewedDemoLevelOfExpectationsAsPupilHtml() {
		final DemoSelection demo = findReviewedDemoSelection();

		final String html = exportService.renderPupilHtml(demo.exam().id(), demo.pupil().id());

		assertThat(html).contains("EF_Englisch");
		assertThat(html).contains("Klausur Nr. 4");
		assertThat(html).contains("Finn Becker");
		assertThat(html).contains("Klausurteil A: Schreiben mit Leseverstehen");
		assertThat(html).contains("GESAMTPUNKTZAHL KLAUSUR");
		assertThat(html).contains("sehr gut plus");
		assertThat(html).doesNotContain("eh:", "tt-criterion", "tt-criterion-badge");
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
	void exportsReviewedDemoLevelOfExpectationsThroughDownloadController() {
		final DemoSelection demo = findReviewedDemoSelection();

		final ResponseEntity<byte[]> response =
				exportController.exportPupilLevelOfExpectations(demo.exam().id(), demo.pupil().id());

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
		assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
				.isEqualTo("attachment; filename=erwartungshorizont-klausur-nr-4-becker-finn.pdf");
		assertThat(response.getBody()).startsWith("%PDF".getBytes());
	}

	private DemoSelection findReviewedDemoSelection() {
		final Course course = courseRepository.findAll().stream()
				.filter(candidate -> candidate.schoolClass() == SchoolClass.CLS_EF)
				.filter(candidate -> candidate.subject() == Subject.ENGLISH)
				.filter(candidate -> candidate.schoolYear().getCalendarYear() == 2026)
				.findFirst()
				.orElseThrow();
		final Exam exam = examRepository.findByCourseId(course.id()).stream()
				.filter(candidate -> candidate.title().equals("Klausur Nr. 4"))
				.findFirst()
				.orElseThrow();
		final Pupil pupil = pupilRepository.findAll().stream()
				.filter(candidate -> candidate.name().equals("Finn"))
				.filter(candidate -> candidate.surname().equals("Becker"))
				.findFirst()
				.orElseThrow();
		return new DemoSelection(exam, pupil);
	}

	private record DemoSelection(Exam exam, Pupil pupil) {
	}
}
