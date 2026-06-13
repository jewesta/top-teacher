package de.westarps.topteacher.backend.export;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import de.westarps.topteacher.backend.export.LevelOfExpectationsExportModelFactory.LevelOfExpectationsExportModel;

@Controller
public class LevelOfExpectationsExportController {

	private final LevelOfExpectationsExportService exportService;

	public LevelOfExpectationsExportController(final LevelOfExpectationsExportService exportService) {
		this.exportService = exportService;
	}

	@GetMapping(value = "/export/exams/{examId}/pupils/{pupilId}/level-of-expectations.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> exportPupilLevelOfExpectations(@PathVariable final int examId,
			@PathVariable final int pupilId) {
		final LevelOfExpectationsExportModel model = exportService.createPupilModel(examId, pupilId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + pdfFileName(model, false))
				.contentType(MediaType.APPLICATION_PDF).body(exportService.renderPupilA4LandscapePdf(model));
	}

	@GetMapping(value = "/export/exams/{examId}/pupils/{pupilId}/level-of-expectations-teacher.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> exportTeacherLevelOfExpectations(@PathVariable final int examId,
			@PathVariable final int pupilId) {
		final LevelOfExpectationsExportModel model = exportService.createTeacherModel(examId, pupilId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + pdfFileName(model, true))
				.contentType(MediaType.APPLICATION_PDF).body(exportService.renderTeacherA4LandscapePdf(model));
	}

	private static String pdfFileName(final LevelOfExpectationsExportModel model, final boolean teacherVersion) {
		final String prefix = teacherVersion ? "lehrerversion-erwartungshorizont" : "erwartungshorizont";
		return prefix + "-" + fileNamePart(model.exam().title()) + "-" + fileNamePart(model.pupil().surname()) + "-"
				+ fileNamePart(model.pupil().name()) + ".pdf";
	}

	private static String fileNamePart(final String value) {
		return (value == null ? "" : value).replaceAll("[^a-zA-Z0-9_-]+", "-").replaceAll("(^-+|-+$)", "")
				.toLowerCase();
	}
}
