package de.westarps.topteacher.backend.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.model.Exam;

class ExamEvaluationExportControllerTests {

	@Test
	void exportsEvaluationWorkbookThroughDownloadController() {
		final Exam exam = new Exam(1, 10, "Klausur Nr. 1", LocalDate.of(2026, 5, 21));
		final ExamRepository examRepository = mock(ExamRepository.class);
		when(examRepository.findById(exam.id())).thenReturn(Optional.of(exam));

		final ExamEvaluationExcelExportService exportService = mock(ExamEvaluationExcelExportService.class);
		when(exportService.renderWorkbook(exam.id())).thenReturn(new byte[] { 1, 2, 3 });

		final ExamEvaluationExportController controller = new ExamEvaluationExportController(examRepository,
				exportService);

		final var response = controller.exportEvaluation(exam.id());

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getHeaders().getContentType().toString())
				.isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
				.isEqualTo("attachment; filename=auswertung-klausur-nr-1.xlsx");
		assertThat(response.getBody()).containsExactly(1, 2, 3);
	}
}
