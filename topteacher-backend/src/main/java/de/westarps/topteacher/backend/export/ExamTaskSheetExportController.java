package de.westarps.topteacher.backend.export;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ExamTaskSheetExportController {
	private final ExamTaskSheetExportService service;

	public ExamTaskSheetExportController(final ExamTaskSheetExportService service) {
		this.service = service;
	}

	@GetMapping(value = "/export/exams/{examId}/task-sheet.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> anonymous(@PathVariable final int examId) {
		return response(service.renderA4LandscapePdf(service.createModel(examId, null)), "aufgabenblatt-" + examId);
	}

	@GetMapping(value = "/export/exams/{examId}/pupils/{pupilId}/task-sheet.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> personalized(@PathVariable final int examId, @PathVariable final int pupilId) {
		return response(service.renderA4LandscapePdf(service.createModel(examId, pupilId)), "aufgabenblatt-" + examId + "-" + pupilId);
	}

	@GetMapping(value = "/export/exams/{examId}/task-sheets.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> allPersonalized(@PathVariable final int examId) {
		return response(service.renderAllPersonalized(examId), "aufgabenblaetter-" + examId);
	}

	private static ResponseEntity<byte[]> response(final byte[] data, final String name) {
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name + ".pdf")
				.contentType(MediaType.APPLICATION_PDF).body(data);
	}
}
