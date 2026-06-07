package de.westarps.topteacher.backend.export;

import org.springframework.stereotype.Service;

import de.westarps.topteacher.backend.export.LevelOfExpectationsExportModelFactory.LevelOfExpectationsExportData;
import de.westarps.topteacher.backend.export.LevelOfExpectationsExportModelFactory.LevelOfExpectationsExportModel;
import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Pupil;

@Service
public class LevelOfExpectationsExportService {

	private static final String PUPIL_TEMPLATE = "export/level-of-expectations-pupil.thymeleaf.html";

	private final CourseRepository courseRepository;
	private final ExamRepository examRepository;
	private final PupilRepository pupilRepository;
	private final GradingScaleRepository gradingScaleRepository;
	private final LevelOfExpectationsRepository levelOfExpectationsRepository;
	private final LevelOfExpectationsExportModelFactory modelFactory;
	private final HtmlRenderer htmlRenderer;
	private final PdfRenderer pdfRenderer;

	public LevelOfExpectationsExportService(final CourseRepository courseRepository,
			final ExamRepository examRepository, final PupilRepository pupilRepository,
			final GradingScaleRepository gradingScaleRepository,
			final LevelOfExpectationsRepository levelOfExpectationsRepository,
			final LevelOfExpectationsExportModelFactory modelFactory, final HtmlRenderer htmlRenderer,
			final PdfRenderer pdfRenderer) {
		this.courseRepository = courseRepository;
		this.examRepository = examRepository;
		this.pupilRepository = pupilRepository;
		this.gradingScaleRepository = gradingScaleRepository;
		this.levelOfExpectationsRepository = levelOfExpectationsRepository;
		this.modelFactory = modelFactory;
		this.htmlRenderer = htmlRenderer;
		this.pdfRenderer = pdfRenderer;
	}

	public String renderPupilHtml(final int examId, final int pupilId) {
		return htmlRenderer.renderModel(PUPIL_TEMPLATE, createPupilModel(examId, pupilId));
	}

	public String renderPupilHtml(final LevelOfExpectationsExportModel model) {
		return htmlRenderer.renderModel(PUPIL_TEMPLATE, model);
	}

	public byte[] renderPupilA5Pdf(final int examId, final int pupilId) {
		return renderPupilA5Pdf(createPupilModel(examId, pupilId));
	}

	public byte[] renderPupilA5Pdf(final LevelOfExpectationsExportModel model) {
		return pdfRenderer.renderA5Pdf(renderPupilHtml(model));
	}

	public byte[] renderPupilA4LandscapePdf(final int examId, final int pupilId) {
		return renderPupilA4LandscapePdf(createPupilModel(examId, pupilId));
	}

	public byte[] renderPupilA4LandscapePdf(final LevelOfExpectationsExportModel model) {
		return pdfRenderer.imposeA5OnA4Landscape(renderPupilA5Pdf(model));
	}

	public LevelOfExpectationsExportModel createPupilModel(final int examId, final int pupilId) {
		final Exam exam = examRepository.findById(examId)
				.orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + examId));
		final Course course = courseRepository.findById(exam.courseId())
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + exam.courseId()));
		final Pupil pupil = pupilRepository.findById(pupilId)
				.orElseThrow(() -> new IllegalArgumentException("Pupil does not exist: " + pupilId));
		final GradingScale gradingScale = gradingScaleRepository.findById(course.gradingScaleId())
				.orElseThrow(() -> new IllegalArgumentException("Grading scale does not exist: "
						+ course.gradingScaleId()));

		return modelFactory.createPupilModel(new LevelOfExpectationsExportData(course, exam, pupil, gradingScale,
				gradingScaleRepository.findRangesByGradingScaleId(gradingScale.id()),
				levelOfExpectationsRepository.findPartsByExamId(examId),
				levelOfExpectationsRepository.findCategoriesByExamId(examId),
				levelOfExpectationsRepository.findTasksByExamId(examId),
				levelOfExpectationsRepository.findRequirementsByExamId(examId),
				levelOfExpectationsRepository.findRequirementResultsByExamAndPupil(examId, pupilId),
				levelOfExpectationsRepository.findNoteSectionsByExamId(examId)));
	}
}
