package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.westarps.topteacher.backend.file.MaterialImageImportService;
import de.westarps.topteacher.backend.file.MaterialImageImportService.ImportedImage;
import de.westarps.topteacher.backend.file.MaterialImageImportService.ImportImageSnapshot;
import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoeMaterialImage;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;

class MaterialImageMcpToolsTests {

	private final MaterialImageImportService images = mock(MaterialImageImportService.class);
	private final MaterialImageMcpTools tools = new MaterialImageMcpTools(images);

	@ParameterizedTest
	@EnumSource(McpAccessLevel.class)
	void returnsNativeImageContentForBothAccessLevels(final McpAccessLevel level) {
		final byte[] bytes = new byte[] { 1, 2, 3 };
		when(images.getImage(7, 9, "image001"))
				.thenReturn(new LoeMaterialImage("image001", "a".repeat(64), "image/png", 2, 3, bytes.length, bytes));
		try (McpAccessContext.Scope ignored = McpAccessContext.open(level)) {
			final var result = tools.getMaterialImage(7, 9, "image001");

			assertThat(result.isError()).isFalse();
			assertThat(result.structuredContent()).isNull();
			assertThat(result.content()).singleElement().isInstanceOfSatisfying(ImageContent.class, image -> {
				assertThat(image.mimeType()).isEqualTo("image/png");
				assertThat(Base64.getDecoder().decode(image.data())).containsExactly(bytes);
			});
		}
	}

	@ParameterizedTest
	@EnumSource(McpAccessLevel.class)
	void exposesStableImportMetadataAndReplayStatusForBothAccessLevels(final McpAccessLevel level) {
		final ImportedImage metadata = new ImportedImage(7, 9, "image001", "image/jpeg", 2, 3, 100);
		final LoeMaterial material = new LoeMaterial(9, 2, "Chart", "![Chart](img:image001)", 0);
		when(images.importImage(7, 9, "/Volumes/Share/chart.jpg", "Chart", null, 8, "request-1"))
				.thenReturn(new ImportImageSnapshot(12, 9, true, metadata, material));
		try (McpAccessContext.Scope ignored = McpAccessContext.open(level)) {
			final var result = tools.importMaterialImage(7, 9, "/Volumes/Share/chart.jpg", "Chart", null, 8, "request-1");

			assertThat(result.revision()).isEqualTo(12);
			assertThat(result.mutationRevision()).isEqualTo(9);
			assertThat(result.replayed()).isTrue();
			assertThat(result.image()).isEqualTo(metadata);
			assertThat(result.material()).isEqualTo(material);
		}
	}

	@Test
	void requiresAnAuthenticatedContextBeforeReadingFilesOrImages() {
		assertThatThrownBy(() -> tools.getMaterialImage(7, 9, "image001"))
				.isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> tools.importMaterialImage(7, 9, "/Volumes/Share/chart.jpg", "Chart", 100, 8, "request-1"))
				.isInstanceOf(SecurityException.class);
		verifyNoInteractions(images);
	}
}
