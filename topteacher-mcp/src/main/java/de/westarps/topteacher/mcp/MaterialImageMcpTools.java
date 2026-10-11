package de.westarps.topteacher.mcp;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.westarps.topteacher.backend.file.MaterialImageImportService;
import de.westarps.topteacher.backend.file.MaterialImageImportService.ImportedImage;
import de.westarps.topteacher.backend.file.MaterialImageImportService.ImportImageSnapshot;
import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences.Alignment;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class MaterialImageMcpTools {

	private final MaterialImageImportService images;

	public MaterialImageMcpTools(final MaterialImageImportService images) {
		this.images = Objects.requireNonNull(images, "images");
	}

	@McpTool(name = "import_material_image", title = "Import an exam material image",
			description = "Import a PNG or JPEG from an absolute path using the configured local or mapped file access, normalize it, and append its image reference to the specified exam material atomically. Requires the current exam revision and a unique request ID. Retry the exact request with the same ID without another insertion; revision is current, mutationRevision identifies the original mutation. The material snapshot contains the canonical image declaration, so it can be repositioned using an UPDATE_MATERIAL command; it is null if the material has since been deleted. Maximum input size 10 MiB, maximum 40 megapixels; normalized longest side 3000 pixels. Available for both access levels; remove personal information from images before importing.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public ImportImageResult importMaterialImage(
			@McpToolParam(description = "Exam ID from get_exam.") final int examId,
			@McpToolParam(description = "Material ID belonging to that exam from get_exam.") final int materialId,
			@McpToolParam(description = "Absolute path supplied by the user, up to 4096 characters; remote installations apply configured share mappings.") final String sourcePath,
			@McpToolParam(description = "Nonblank alternative text describing the image, up to 1000 characters.") final String altText,
			@McpToolParam(required = false, description = "Image width percentage from 1 to 100; omit for automatic sizing.") final Integer widthPercent,
			@McpToolParam(required = false, description = "Image alignment: LEFT, CENTER, or RIGHT; omit for CENTER.") final Alignment alignment,
			@McpToolParam(description = "Current exam revision from get_exam.") final long expectedRevision,
			@McpToolParam(description = "Unique request ID; reuse it only when retrying this exact request.") final String requestId) {
		McpAccessContext.accessLevel();
		final ImportImageSnapshot result = images.importImage(examId, materialId, sourcePath, altText,
				widthPercent, alignment, expectedRevision, requestId);
		return new ImportImageResult(result.revision(), result.mutationRevision(), result.replayed(), result.metadata(),
				result.material());
	}

	@McpTool(name = "get_material_image", title = "Get an exam material image",
			description = "Return the stored image as native MCP image content (mimeType plus Base64 data). The image must be referenced by the specified material within the specified exam. Full and pseudonymized access both receive the original image pixels; image content is not anonymized.",
			generateOutputSchema = false,
			annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
					idempotentHint = true, openWorldHint = false))
	public CallToolResult getMaterialImage(
			@McpToolParam(description = "Exam ID from get_exam.") final int examId,
			@McpToolParam(description = "Material ID belonging to that exam from get_exam.") final int materialId,
			@McpToolParam(description = "Image ID listed for this material by get_exam or import_material_image.") final String imageId) {
		McpAccessContext.accessLevel();
		return CallToolResult.builder(List.of(McpImageContentFactory.from(images.getImage(examId, materialId, imageId))))
				.build();
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ImportImageResult(long revision, long mutationRevision, boolean replayed, ImportedImage image,
			@Nullable LoeMaterial material) {
	}
}
