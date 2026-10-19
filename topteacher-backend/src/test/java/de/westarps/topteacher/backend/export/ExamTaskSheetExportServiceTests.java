package de.westarps.topteacher.backend.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.loe.LoeMaterial;

@SpringBootTest
@Sql("/db/demo-data.sql")
@Transactional
class ExamTaskSheetExportServiceTests {
	@Autowired private ExamTaskSheetExportService service;
	@Autowired private ExamTaskSheetExportController controller;
	@Autowired private CourseRepository courses;
	@Autowired private de.westarps.topteacher.backend.repo.PupilRepository pupilRepository;
	@Autowired private ExamRepository exams;
	@Autowired private LevelOfExpectationsRepository design;

	@Test
	void exportsSpanishPupilContentWithoutAssessmentRubric() throws Exception {
		final var exam = spanishExam();
		final var model = service.createModel(exam.id(), null);
		assertThat(model.points()).isEqualTo(160);
		assertThat(model.pupilName()).isEmpty();
		assertThat(model.parts()).extracting(ExamTaskSheetExportService.PartModel::points).containsExactly(110, 50);
		final String html = service.renderHtml(model);
		assertThat(html).contains("Zugelassene Hilfsmittel", "ws-markdown-numbered", "Fußnoten", "110 Punkte", "66 Punkte", "30 Punkte");
		assertThat(html).doesNotContain("crit:", "erfüllt ein weiteres aufgabenbezogenes", "Textgestaltung", "GESAMTPUNKTZAHL", "5. Zugelassene");
		final byte[] a5 = service.renderA5Pdf(model);
		final byte[] a4 = controller.anonymous(exam.id()).getBody();
		final List<String> pages = pages(a5);
		assertThat(pages).allSatisfy(page -> assertThat(page.replaceAll("Seite \\d+ / \\d+", "").trim()).isNotEmpty());
		assertThat(pages.getFirst()).contains("Name:", "Gesamt: 160 Punkte", "Zugelassene Hilfsmittel");
		assertThat(pages).anySatisfy(page -> assertThat(page).contains("27.", "Betreuung, Begleitung"));
		assertThat(pages).anySatisfy(page -> assertThat(page).contains("Sprachliche Leistung / Darstellungsleistung", "66 Punkte"));
		assertThat(pages).anySatisfy(page -> assertThat(page).contains("Klausurteil B", "50 Punkte"));
		try (final var imposed = PDDocument.load(a4)) {
			assertThat(imposed.getNumberOfPages()).isEqualTo((pages.size() + 1) / 2);
			assertThat(imposed.getPage(0).getMediaBox().getWidth()).isCloseTo(PDRectangle.A4.getHeight(), org.assertj.core.data.Offset.offset(0.1f));
			assertThat(imposed.getPage(0).getMediaBox().getHeight()).isCloseTo(PDRectangle.A4.getWidth(), org.assertj.core.data.Offset.offset(0.1f));
		}
		final String review = System.getProperty("tt.pdfReviewDirectory");
		if (review != null) {
			Files.createDirectories(Path.of(review));
			Files.writeString(Path.of(review, "aufgabenblatt.html"), html);
			Files.write(Path.of(review, "aufgabenblatt-a5.pdf"), a5);
			Files.write(Path.of(review, "aufgabenblatt-anonym.pdf"), a4);
		}
	}

	@Test
	void personalizedCopiesKeepPaginationAndAreImposedSeparately() throws Exception {
		final var exam = spanishExam();
		final var pupils = exams.findPupils(exam.id());
		final var anonymous = pages(service.renderA5Pdf(service.createModel(exam.id(), null)));
		final var model = service.createModel(exam.id(), pupils.getFirst().id());
		final var personal = pages(service.renderA5Pdf(model));
		assertThat(personal).hasSize(anonymous.size());
		assertThat(personal.getFirst()).contains(model.pupilName());
		assertThat(personal.subList(1, personal.size())).isEqualTo(anonymous.subList(1, anonymous.size()));
		assertThatThrownBy(() -> service.createModel(exam.id(), Integer.MAX_VALUE)).isInstanceOf(IllegalArgumentException.class);
		try (final var combined = PDDocument.load(controller.allPersonalized(exam.id()).getBody())) {
			assertThat(combined.getNumberOfPages()).isEqualTo(pupils.size() * ((anonymous.size() + 1) / 2));
		}
		final String review = System.getProperty("tt.pdfReviewDirectory");
		if (review != null) {
			Files.write(Path.of(review, "aufgabenblatt-personalisiert.pdf"), service.renderA4LandscapePdf(model));
		}
	}

	@Test
	void inlineMaterialSharesSpaceButDedicatedMaterialSeparatesFollowingContent() throws Exception {
		final var exam = spanishExam();
		final var material = design.findMaterialsByExamId(exam.id()).stream().filter(item -> item.title().equals("Zugelassene Hilfsmittel")).findFirst().orElseThrow();
		final var source = design.findMaterialsByExamId(exam.id()).get(1);
		design.saveMaterial(new LoeMaterial(source.id(), source.partId(), "SHORT MATERIAL", "Short material body", source.sortOrder(), false));
		var pages = pages(service.renderA5Pdf(service.createModel(exam.id(), null)));
		assertThat(pages.getFirst()).contains(material.title(), "SHORT MATERIAL", "Aufgabe 1");
		design.saveMaterial(new LoeMaterial(source.id(), source.partId(), "SHORT MATERIAL", "Short material body", source.sortOrder(), true));
		pages = pages(service.renderA5Pdf(service.createModel(exam.id(), null)));
		assertThat(pages.getFirst()).contains(material.title()).doesNotContain("SHORT MATERIAL", "Aufgabe 1");
		assertThat(pages.get(1)).contains("SHORT MATERIAL").doesNotContain("Aufgabe 1");
		assertThat(pages.get(2)).contains("Aufgabe 1");
	}

	@Test
	void longPupilNamesFitTheReservedLineWithoutChangingPagination() throws Exception {
		final var exam = spanishExam();
		final var pupil = exams.findPupils(exam.id()).getFirst();
		final var anonymous = pages(service.renderA5Pdf(service.createModel(exam.id(), null)));
		final var name = "Alexandra".repeat(8);
		final var surname = "Mustermann".repeat(8);
		pupilRepository.save(new de.westarps.topteacher.model.Pupil(pupil.id(), name, surname, pupil.lifecycle()));
		final var model = service.createModel(exam.id(), pupil.id());
		assertThat(model.nameStyle()).doesNotContain("9.000pt");
		final var personalized = pages(service.renderA5Pdf(model));
		assertThat(personalized).hasSize(anonymous.size());
		assertThat(personalized.getFirst()).contains(name, surname);
		assertThat(personalized.subList(1, personalized.size())).isEqualTo(anonymous.subList(1, anonymous.size()));
	}

	@Test
	void taskSheetAlertsRenderAllFiveVectorIcons() throws Exception {
		final var exam = spanishExam();
		final String alerts = java.util.stream.Stream.of("NOTE", "TIP", "IMPORTANT", "WARNING", "CAUTION")
				.map(type -> "> [!" + type + "]\n> PDF alert " + type).collect(java.util.stream.Collectors.joining("\n\n"));
		design.saveExamFooterMarkdown(exam.id(), alerts);
		final var model = service.createModel(exam.id(), null);
		int icons = 0;
		try (final var document = PDDocument.load(service.renderA5Pdf(model))) {
			assertThat(new PDFTextStripper().getText(document)).contains("PDF alert NOTE", "PDF alert TIP", "PDF alert IMPORTANT", "PDF alert WARNING", "PDF alert CAUTION");
			for (final var page : document.getPages()) {
				for (final var name : page.getResources().getXObjectNames()) {
					assertThat(page.getResources().getXObject(name)).isInstanceOf(org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject.class);
					icons++;
				}
			}
		}
		/* Five test alerts plus the demo source's existing NOTE. */
		assertThat(icons).isEqualTo(6);
		final String review = System.getProperty("tt.pdfReviewDirectory");
		if (review != null) {
			Files.createDirectories(Path.of(review));
			Files.write(Path.of(review, "alerts.pdf"), service.renderA4LandscapePdf(model));
		}
	}

	private Exam spanishExam() {
		return courses.findAll().stream().filter(item -> item.subject().name().equals("Spanisch") && item.schoolYear().getCalendarYear() == 2026)
				.flatMap(course -> exams.findByCourseId(course.id()).stream())
				.filter(item -> item.title().equals("Abiturprüfung 2026 Spanisch (Demo)")).findFirst().orElseThrow();
	}

	private List<String> pages(final byte[] bytes) throws Exception {
		try (final var document = PDDocument.load(bytes)) {
			final List<String> pages = new ArrayList<>();
			for (int page = 1; page <= document.getNumberOfPages(); page++) {
				final var stripper = new PDFTextStripper();
				stripper.setStartPage(page);
				stripper.setEndPage(page);
				pages.add(stripper.getText(document));
			}
			return pages;
		}
	}
}
