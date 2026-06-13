package de.westarps.topteacher.backend.export;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.model.Exam;

@Controller
public class ExamEvaluationExportController {

	private static final MediaType XLSX_MEDIA_TYPE = MediaType
			.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final ExamRepository examRepository;
	private final ExamEvaluationExcelExportService excelExportService;

	public ExamEvaluationExportController(final ExamRepository examRepository,
			final ExamEvaluationExcelExportService excelExportService) {
		this.examRepository = examRepository;
		this.excelExportService = excelExportService;
	}

	@GetMapping(value = "/export/exams/{examId}/evaluation.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	public ResponseEntity<byte[]> exportEvaluation(@PathVariable final int examId) {
		final Exam exam = examRepository.findById(examId)
				.orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + examId));
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + excelFileName(exam))
				.contentType(XLSX_MEDIA_TYPE).body(excelExportService.renderWorkbook(examId));
	}

	private static String excelFileName(final Exam exam) {
		return "auswertung-" + fileNamePart(exam.title()) + ".xlsx";
	}

	private static String fileNamePart(final String value) {
		return (value == null ? "" : value).replaceAll("[^a-zA-Z0-9_-]+", "-").replaceAll("(^-+|-+$)", "")
				.toLowerCase();
	}
}
