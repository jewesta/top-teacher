package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import de.westarps.topteacher.backend.export.Sanitizer;
import de.westarps.topteacher.backend.export.Sanitizer.MarkdownView;
import de.westarps.topteacher.backend.file.FileAccessSettingsService;
import de.westarps.topteacher.mcp.McpContextTools.MarkdownContext;
import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.InstallationLocation;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.loe.LoeCriterionParser;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences;

class McpContextToolsTests extends FullMcpAccessTest {

	private final FileAccessSettingsService fileAccess = mock(FileAccessSettingsService.class);
	private final McpContextTools tools = new McpContextTools(fileAccess);

	@Test
	void discoversEffectiveCapabilitiesAndImageLimitsWithoutPrivatePaths() {
		when(fileAccess.installationLocation()).thenReturn(InstallationLocation.REMOTE);
		final var full = tools.getMcpContext();
		assertThat(full.contractVersion()).isEqualTo("2.0");
		assertThat(full.identityCapabilities().realNameSearch()).isTrue();
		assertThat(full.imageImport().maximumBytes()).isEqualTo(10 * 1024 * 1024);
		try (McpAccessContext.Scope ignored = McpAccessContext.open(McpAccessLevel.PSEUDONYMIZED)) {
			final var pseudonymized = tools.getMcpContext();
			assertThat(pseudonymized.accessLevel()).isEqualTo("PSEUDONYMIZED");
			assertThat(pseudonymized.identityCapabilities().createOrImportPupils()).isFalse();
			assertThat(pseudonymized.availableOperations()).contains("apply_exam_content_changes", "apply_level_of_expectations_changes",
					"create_database_backup", "import_material_image").doesNotContain("create_pupils", "update_pupil", "create_level_of_expectations");
			assertThat(pseudonymized.imageImport()).isEqualTo(full.imageImport());
		}
	}

	@Test
	void guideIsIdenticalInBothModesAndFilteredByAuthoringContext() {
		final var all = tools.getMarkdownGuide(null);
		try (McpAccessContext.Scope ignored = McpAccessContext.open(McpAccessLevel.PSEUDONYMIZED)) {
			assertThat(tools.getMarkdownGuide(MarkdownContext.ALL)).isEqualTo(all);
		}
		final var task = tools.getMarkdownGuide(MarkdownContext.TASK);
		assertThat(task.markdown()).contains("[!NUMBERED]", "[^1]", "closing text", "do not support managed images")
				.doesNotContain("![Diagramm 1]", "crit:1?points");
		assertThat(task.capabilities().managedImages()).isFalse();
		assertThat(task.capabilities().assessmentCriteria()).isFalse();
		assertThat(all.capabilities().pupilExamSheetExport()).isFalse();
	}

	@Test
	void copyableCriterionImageAndAlertExamplesConformToActualParsersAndRenderer() {
		final List<String> examples = examples(tools.getMarkdownGuide(null).markdown());
		final String criteria = examples.stream().filter(source -> source.contains("crit:")).findFirst().orElseThrow();
		final var parsed = LoeCriterionParser.analyze(1, criteria);
		assertThat(parsed.issues()).isEmpty();
		assertThat(parsed.criteria()).extracting(criterion -> criterion.pointUnits()).containsExactly(3, 1);
		final String image = examples.stream().filter(source -> source.contains("img:")).findFirst().orElseThrow();
		assertThat(LoeMaterialImageReferences.ids(image)).containsExactly("4n8k2p7c");
		assertThat(LoeMaterialImageReferences.findAt(image, 4, 4)).isPresent();
		final String alerts = examples.stream().filter(source -> source.contains("[!TIP]")).findFirst().orElseThrow();
		final String html = new Sanitizer().markdownToHtml(alerts, MarkdownView.PUPIL).value();
		assertThat(html).contains("ws-markdown-alert-tip", "ws-markdown-alert-important", "Schlüsselbegriffe")
				.doesNotContain("[!TIP]");
	}

	private static List<String> examples(final String markdown) {
		final List<String> examples = new ArrayList<>();
		final Matcher matcher = Pattern.compile("```markdown\\n([\\s\\S]*?)```").matcher(markdown);
		while (matcher.find()) {
			examples.add(matcher.group(1));
		}
		return examples;
	}
}
