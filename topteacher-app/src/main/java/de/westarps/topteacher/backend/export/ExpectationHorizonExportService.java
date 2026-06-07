package de.westarps.topteacher.backend.export;

import org.springframework.stereotype.Service;

import de.westarps.topteacher.backend.export.ExpectationHorizonExportModelFactory.ExpectationHorizonExportData;
import de.westarps.topteacher.backend.export.ExpectationHorizonExportModelFactory.ExpectationHorizonExportModel;
import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.ExpectationHorizonRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Pupil;

@Service
public class ExpectationHorizonExportService {

	private static final String PUPIL_TEMPLATE = "export/expectation-horizon-pupil.thymeleaf.html";

	private final CourseRepository courseRepository;
	private final ExamRepository examRepository;
	private final PupilRepository pupilRepository;
	private final GradingScaleRepository gradingScaleRepository;
	private final ExpectationHorizonRepository expectationHorizonRepository;
	private final ExpectationHorizonExportModelFactory modelFactory;
	private final HtmlRenderer htmlRenderer;
	private final PdfRenderer pdfRenderer;

	public ExpectationHorizonExportService(final CourseRepository courseRepository,
			final ExamRepository examRepository, final PupilRepository pupilRepository,
			final GradingScaleRepository gradingScaleRepository,
			final ExpectationHorizonRepository expectationHorizonRepository,
			final ExpectationHorizonExportModelFactory modelFactory, final HtmlRenderer htmlRenderer,
			final PdfRenderer pdfRenderer) {
		this.courseRepository = courseRepository;
		this.examRepository = examRepository;
		this.pupilRepository = pupilRepository;
		this.gradingScaleRepository = gradingScaleRepository;
		this.expectationHorizonRepository = expectationHorizonRepository;
		this.modelFactory = modelFactory;
		this.htmlRenderer = htmlRenderer;
		this.pdfRenderer = pdfRenderer;
	}

	public String renderPupilHtml(final int examId, final int pupilId) {
		return htmlRenderer.renderModel(PUPIL_TEMPLATE, createPupilModel(examId, pupilId));
	}

	public String renderPupilHtml(final ExpectationHorizonExportModel model) {
		return htmlRenderer.renderModel(PUPIL_TEMPLATE, model);
	}

	public byte[] renderPupilA5Pdf(final int examId, final int pupilId) {
		return renderPupilA5Pdf(createPupilModel(examId, pupilId));
	}

	public byte[] renderPupilA5Pdf(final ExpectationHorizonExportModel model) {
		return pdfRenderer.renderA5Pdf(renderPupilHtml(model));
	}

	public byte[] renderPupilA4LandscapePdf(final int examId, final int pupilId) {
		return renderPupilA4LandscapePdf(createPupilModel(examId, pupilId));
	}

	public byte[] renderPupilA4LandscapePdf(final ExpectationHorizonExportModel model) {
		return pdfRenderer.imposeA5OnA4Landscape(renderPupilA5Pdf(model));
	}

	public ExpectationHorizonExportModel createPupilModel(final int examId, final int pupilId) {
		final Exam exam = examRepository.findById(examId)
				.orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + examId));
		final Course course = courseRepository.findById(exam.courseId())
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + exam.courseId()));
		final Pupil pupil = pupilRepository.findById(pupilId)
				.orElseThrow(() -> new IllegalArgumentException("Pupil does not exist: " + pupilId));
		final GradingScale gradingScale = gradingScaleRepository.findById(course.gradingScaleId())
				.orElseThrow(() -> new IllegalArgumentException("Grading scale does not exist: "
						+ course.gradingScaleId()));

		return modelFactory.createPupilModel(new ExpectationHorizonExportData(course, exam, pupil, gradingScale,
				gradingScaleRepository.findRangesByGradingScaleId(gradingScale.id()),
				expectationHorizonRepository.findPartsByExamId(examId),
				expectationHorizonRepository.findCategoriesByExamId(examId),
				expectationHorizonRepository.findTasksByExamId(examId),
				expectationHorizonRepository.findRequirementsByExamId(examId),
				expectationHorizonRepository.findRequirementResultsByExamAndPupil(examId, pupilId),
				expectationHorizonRepository.findNoteSectionsByExamId(examId)));
	}
}
