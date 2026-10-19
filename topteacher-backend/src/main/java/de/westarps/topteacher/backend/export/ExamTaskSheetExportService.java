package de.westarps.topteacher.backend.export;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.font.FontRenderContext;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.RenderRequest;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.Section;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.Variant;
import de.westarps.topteacher.backend.render.MarkdownFragmentHtmlRenderer;
import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeTask;

/** Pupil task sheets contain authored tasks and materials, never assessment criteria or results. */
@Service
public class ExamTaskSheetExportService {

	private static final Font NAME_FONT = loadNameFont();

	private static Font loadNameFont() {
		try (final var stream = ExamTaskSheetExportService.class.getResourceAsStream("/templates/frugal-sans/FrugalSansLight.ttf")) {
			return Font.createFont(Font.TRUETYPE_FONT, Objects.requireNonNull(stream)).deriveFont(8f);
		} catch (final IOException | FontFormatException exception) {
			throw new IllegalStateException("Task-sheet font could not be loaded.", exception);
		}
	}

	private final ExamRepository exams;
	private final CourseRepository courses;
	private final LevelOfExpectationsRepository design;
	private final HtmlRenderer html;
	private final PdfRenderer pdf;
	private final MarkdownFragmentHtmlRenderer markdown = new MarkdownFragmentHtmlRenderer();

	public ExamTaskSheetExportService(final ExamRepository exams, final CourseRepository courses,
			final LevelOfExpectationsRepository design, final HtmlRenderer html, final PdfRenderer pdf) {
		this.exams = exams;
		this.courses = courses;
		this.design = design;
		this.html = html;
		this.pdf = pdf;
	}

	public TaskSheetModel createModel(final int examId, final Integer pupilId) {
		return design.withDesignRead(examId, () -> {
			final Exam exam = exams.findById(examId)
					.orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + examId));
			final var course = courses.findById(exam.courseId())
					.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + exam.courseId()));
			final String pupilName = pupilId == null ? "" : exams.findPupils(examId).stream()
					.filter(pupil -> pupil.id().equals(pupilId)).findFirst()
					.map(pupil -> pupil.name() + " " + pupil.surname())
					.orElseThrow(() -> new IllegalArgumentException("Pupil is not assigned to exam: " + pupilId));
			final var categories = design.findCategoriesByExamId(examId);
			final var tasks = design.findTasksByExamId(examId);
			final var materials = design.findMaterialsByExamId(examId);
			final var images = design.findMaterialImagesByIds(materials.stream()
					.flatMap(material -> LoeMaterialImageReferences.ids(material.descriptionMarkdown()).stream()).toList());
			final List<PartModel> parts = new ArrayList<>();
			for (final var part : design.findPartsByExamId(examId).stream()
					.sorted(Comparator.comparingInt(LoePart::sortOrder)
							.thenComparing(LoePart::id)).toList()) {
				final List<MaterialModel> partMaterials = new ArrayList<>();
				boolean previousDedicated = false;
				for (final LoeMaterial material : materials.stream().filter(item -> item.partId().equals(part.id()))
						.sorted(Comparator.comparingInt(LoeMaterial::sortOrder).thenComparing(LoeMaterial::id)).toList()) {
					final String content = renderMarkdown(material.descriptionMarkdown(), images);
					final var headings = Jsoup.parseBodyFragment(content).select("h1, h2, h3");
					headings.select("sup").remove();
					final boolean hasTitle = headings.stream().anyMatch(heading -> heading.text().equals(material.title()));
					partMaterials.add(new MaterialModel(material.title(), content,
							!partMaterials.isEmpty() && (material.dedicatedPage() || previousDedicated), !hasTitle));
					previousDedicated = material.dedicatedPage();
				}
				final List<CategoryModel> partCategories = categories.stream().filter(item -> item.partId().equals(part.id()))
						.sorted(Comparator.comparingInt(LoeCategory::sortOrder)
								.thenComparing(LoeCategory::id))
						.map(category -> {
							final var categoryTasks = tasks.stream().filter(item -> item.categoryId().equals(category.id()))
									.sorted(Comparator.comparingInt(LoeTask::sortOrder)
											.thenComparing(LoeTask::id)).toList();
							return new CategoryModel(category.title(), categoryTasks.stream().mapToInt(item -> item.maxPoints()).sum(),
									category.printTasksOnExamSheet(),
									category.printTasksOnExamSheet() ? renderMarkdown(category.descriptionMarkdown(), List.of()) : "",
									category.printTasksOnExamSheet() ? categoryTasks.stream().map(task -> new TaskModel(task.title(),
											renderMarkdown(task.descriptionMarkdown(), List.of()), task.maxPoints())).toList() : List.of());
						}).toList();
				parts.add(new PartModel(part.title(), partCategories.stream().mapToInt(CategoryModel::points).sum(),
						partMaterials, partCategories, previousDedicated));
			}
			return new TaskSheetModel(exam.title(), course.getDisplayName(), exam.date().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
					pupilName, parts.stream().mapToInt(PartModel::points).sum(), List.copyOf(parts),
					renderMarkdown(design.findExamFooterMarkdown(examId), List.of()));
		});
	}

	public String renderHtml(final TaskSheetModel model) {
		return html.renderModel("export/exam-task-sheet.thymeleaf.html", model);
	}

	public byte[] renderA5Pdf(final TaskSheetModel model) {
		return pdf.renderA5Pdf(renderHtml(model));
	}

	public byte[] renderA4LandscapePdf(final TaskSheetModel model) {
		return pdf.imposeA5OnA4Landscape(renderA5Pdf(model));
	}

	public byte[] renderAllPersonalized(final int examId) {
		final List<TaskSheetModel> models = design.withDesignRead(examId, () -> {
			final TaskSheetModel anonymous = createModel(examId, null);
			final List<Pupil> pupils = exams.findPupils(examId);
			if (pupils.isEmpty()) {
				throw new IllegalArgumentException("Exam has no assigned pupils: " + examId);
			}
			return pupils.stream().map(pupil -> new TaskSheetModel(anonymous.title(), anonymous.course(),
					anonymous.date(), pupil.name() + " " + pupil.surname(), anonymous.points(), anonymous.parts(),
					anonymous.footerHtml())).toList();
		});
		/* Impose each copy separately so pupils never share an A4 sheet. */
		return pdf.merge(models.stream().map(this::renderA4LandscapePdf).toList());
	}

	private String renderMarkdown(final String source, final List<LoeMaterialImage> images) {
		if (source == null || source.isBlank()) {
			return "";
		}
		final var fragment = markdown.render(new RenderRequest(
				List.of(new Section("", Objects.requireNonNullElse(source, ""), Variant.MATERIAL)), images));
		final var document = Jsoup.parseBodyFragment(fragment.html());
		for (final Map.Entry<String, LoeMaterialImage> image : fragment.imageResources().entrySet()) {
			document.select("img").stream().filter(element -> image.getKey().equals(element.attr("src"))).forEach(element -> {
				element.attr("src", "data:" + image.getValue().mediaType() + ";base64,"
						+ Base64.getEncoder().encodeToString(image.getValue().data()));
				element.removeAttr("width").removeAttr("height");
			});
		}
		return document.body().html();
	}

	public record TaskSheetModel(String title, String course, String date, String pupilName, int points,
			List<PartModel> parts, String footerHtml) {
		public String nameStyle() {
			final double width = NAME_FONT.getStringBounds(pupilName, new FontRenderContext(null, true, true)).getWidth();
			final double points = Math.min(8, 8 * (42 * 72 / 25.4) / Math.max(1, width));
			return String.format(Locale.ROOT, "font-size: %.3fpt", points);
		}
	}
	public record PartModel(String title, int points, List<MaterialModel> materials, List<CategoryModel> categories,
			boolean tasksOnNewPage) { }
	public record MaterialModel(String title, String html, boolean newPage, boolean showTitle) { }
	public record CategoryModel(String title, int points, boolean printTasks, String html, List<TaskModel> tasks) { }
	public record TaskModel(String title, String html, int points) { }
}
