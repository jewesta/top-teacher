package de.westarps.topteacher.backend.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import org.springframework.stereotype.Service;

import de.westarps.topteacher.backend.authoring.ExamDesignGuard;
import de.westarps.topteacher.backend.authoring.ExamAuthoringService;
import de.westarps.topteacher.backend.authoring.ExamMutationExecutor;
import de.westarps.topteacher.backend.authoring.ExamMutationExecutor.MutationExecution;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences.Alignment;

/** Imports an image and its material reference in one revision-checked transaction. */
@Service
public class MaterialImageImportService {

	private static final String IMAGE_ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
	private static final int MAX_ALT_TEXT_LENGTH = 1_000;
	private static final int MAX_SOURCE_PATH_LENGTH = 4_096;
	private final LevelOfExpectationsRepository repository;
	private final SharedFilePathResolver paths;
	private final MaterialImageProcessor processor;
	private final ExamDesignGuard guard;
	private final ExamMutationExecutor mutations;
	private final ExamAuthoringService authoring;
	private final SecureRandom random = new SecureRandom();

	public MaterialImageImportService(final LevelOfExpectationsRepository repository,
			final SharedFilePathResolver paths, final MaterialImageProcessor processor, final ExamDesignGuard guard,
			final ExamMutationExecutor mutations, final ExamAuthoringService authoring) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.paths = Objects.requireNonNull(paths, "paths");
		this.processor = Objects.requireNonNull(processor, "processor");
		this.guard = Objects.requireNonNull(guard, "guard");
		this.mutations = Objects.requireNonNull(mutations, "mutations");
		this.authoring = Objects.requireNonNull(authoring, "authoring");
	}

	public ImportImageSnapshot importImage(final int examId, final int materialId,
			final String sourcePath, final String altText, final Integer widthPercent, final long expectedRevision,
			final String requestId) {
		return importImage(examId, materialId, sourcePath, altText, widthPercent, Alignment.LEFT, expectedRevision,
				requestId);
	}

	public ImportImageSnapshot importImage(final int examId, final int materialId,
			final String sourcePath, final String altText, final Integer widthPercent, final Alignment alignment,
			final long expectedRevision, final String requestId) {
		final String description = alternativeText(altText);
		final Alignment resolvedAlignment = alignment == null ? Alignment.LEFT : alignment;
		if (widthPercent != null && (widthPercent < 1 || widthPercent > 100)) {
			throw new IllegalArgumentException("widthPercent must be between 1 and 100.");
		}
		if (sourcePath == null || sourcePath.isBlank()) {
			throw new IllegalArgumentException("sourcePath must be an absolute image file path.");
		}
		if (sourcePath.length() > MAX_SOURCE_PATH_LENGTH) {
			throw new IllegalArgumentException("sourcePath must not exceed " + MAX_SOURCE_PATH_LENGTH + " characters.");
		}
		final String fingerprint = ExamMutationExecutor.fingerprint(examId, expectedRevision, materialId, sourcePath,
				description, widthPercent, resolvedAlignment);
		final MutationExecution<ImportedImage> mutation = mutations.execute(examId, expectedRevision, requestId,
				"import_material_image", fingerprint,
				() -> importAndReference(examId, materialId, sourcePath, description, widthPercent, resolvedAlignment),
				MaterialImageImportService::encodeMetadata, MaterialImageImportService::decodeMetadata);
		return guard.read(examId, () -> new ImportImageSnapshot(guard.revision(examId), mutation.revision(),
				mutation.replayed(), mutation.metadata(), repository.findMaterialsByExamId(examId).stream()
						.filter(candidate -> candidate.id() == materialId).findFirst().orElse(null)));
	}

	public LoeMaterialImage getImage(final int examId, final int materialId, final String imageId) {
		if (imageId == null || !imageId.matches("[a-z0-9]{8}")) {
			throw new IllegalArgumentException("imageId must be a stored eight-character material image ID.");
		}
		return guard.read(examId, () -> {
			final LoeMaterial material = material(examId, materialId);
			if (!LoeMaterialImageReferences.ids(material.descriptionMarkdown()).contains(imageId)) {
				throw new IllegalArgumentException("The image is not referenced by the specified exam material.");
			}
			return repository.findMaterialImagesByIds(List.of(imageId)).stream().findFirst()
					.orElseThrow(() -> new IllegalArgumentException("The referenced material image is unavailable."));
		});
	}

	private ImportedImage importAndReference(final int examId, final int materialId, final String sourcePath,
			final String description, final Integer width, final Alignment alignment) {
		final LoeMaterial material = material(examId, materialId);
		if (repository.hasResultsForExam(examId)) {
			throw new IllegalStateException("Exam material images cannot be changed after the first results.");
		}
		final LoeMaterialImage pending = processor.process(randomImageId(
				LoeMaterialImageReferences.ids(material.descriptionMarkdown()), random::nextInt), readImage(sourcePath));
		final String reference = "![" + description.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")
				.replace('\n', ' ').replace('\r', ' ') + "](" + LoeMaterialImageReferences.destination(pending.id(),
						width, alignment) + ")";
		final String markdown = material.descriptionMarkdown()
				+ (material.descriptionMarkdown().isEmpty() ? "" : "\n\n") + reference;
		final LoeMaterial saved = repository.saveMaterialWithImages(new LoeMaterial(material.id(), material.partId(),
				material.title(), markdown, material.sortOrder()), List.of(pending));
		authoring.validateDesignLimits(examId);
		final LoeMaterialImage stored = repository.findMaterialImageByContentHash(pending.contentHash())
				.orElseThrow(() -> new IllegalStateException("Imported image was not persisted."));
		if (!LoeMaterialImageReferences.ids(saved.descriptionMarkdown()).contains(stored.id())) {
			throw new IllegalStateException("Imported image has no material reference.");
		}
		return new ImportedImage(examId, materialId, stored.id(), stored.mediaType(), stored.pixelWidth(),
				stored.pixelHeight(), stored.byteSize());
	}

	private LoeMaterial material(final int examId, final int materialId) {
		return repository.findMaterialsByExamId(examId).stream().filter(candidate -> candidate.id() == materialId)
				.findFirst().orElseThrow(() -> new IllegalArgumentException("Material does not belong to the specified exam."));
	}

	private Path readablePath(final String sourcePath) {
		try {
			return paths.resolveReadableFile(sourcePath);
		} catch (final IllegalArgumentException | SecurityException exception) {
			throw new IllegalArgumentException("The image file is unavailable or outside the configured file-access scope.");
		}
	}

	@SuppressWarnings("unchecked")
	private byte[] readImage(final String sourcePath) {
		final Path path = readablePath(sourcePath);
		try (DirectoryStream<Path> root = Files.newDirectoryStream(path.getRoot())) {
			if (root instanceof SecureDirectoryStream<?>) {
				return readSecurely((SecureDirectoryStream<Path>) root, path);
			}
			return readWithCanonicalRechecks(path, sourcePath);
		} catch (final IOException | SecurityException exception) {
			throw new IllegalArgumentException("The image file could not be read.");
		}
	}

	private static byte[] readSecurely(final SecureDirectoryStream<Path> root, final Path path) throws IOException {
		SecureDirectoryStream<Path> directory = root;
		try {
			for (int index = 0; index < path.getNameCount() - 1; index++) {
				final SecureDirectoryStream<Path> parent = directory;
				directory = parent.newDirectoryStream(path.getName(index), LinkOption.NOFOLLOW_LINKS);
				if (parent != root) {
					parent.close();
				}
			}
			final Path filename = path.getFileName();
			final BasicFileAttributes attributes = directory
					.getFileAttributeView(filename, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
			if (!attributes.isRegularFile()) {
				throw new IllegalArgumentException("The image must be a readable regular file.");
			}
			try (SeekableByteChannel channel = directory.newByteChannel(filename,
					Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
				return readBounded(channel);
			}
		} finally {
			if (directory != root) {
				directory.close();
			}
		}
	}

	private byte[] readWithCanonicalRechecks(final Path path, final String sourcePath) throws IOException {
		/*
		 * Providers without secure directory handles cannot guarantee adversarial race
		 * containment. Recheck canonical path and configured scope after opening and
		 * after the bounded read; server mount paths never leave this service.
		 */
		try (SeekableByteChannel channel = Files.newByteChannel(path,
				Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
			if (!Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile()
					|| !path.equals(readablePath(sourcePath))) {
				throw new IllegalArgumentException("The image file changed or is outside the configured file-access scope.");
			}
			final byte[] bytes = readBounded(channel);
			if (!path.equals(readablePath(sourcePath))) {
				throw new IllegalArgumentException("The image file changed or is outside the configured file-access scope.");
			}
			return bytes;
		}
	}

	private static byte[] readBounded(final SeekableByteChannel channel) throws IOException {
		if (channel.size() > MaterialImageProcessor.MAX_UPLOAD_BYTES) {
			throw new IllegalArgumentException("The image must not exceed 10 MiB.");
		}
		try (InputStream input = Channels.newInputStream(channel)) {
			final byte[] bytes = input.readNBytes(MaterialImageProcessor.MAX_UPLOAD_BYTES + 1);
			if (bytes.length > MaterialImageProcessor.MAX_UPLOAD_BYTES) {
				throw new IllegalArgumentException("The image must not exceed 10 MiB.");
			}
			return bytes;
		}
	}

	static String randomImageId(final Set<String> excludedIds, final IntUnaryOperator randomIndex) {
		for (int attempt = 0; attempt < 100; attempt++) {
			final StringBuilder id = new StringBuilder(8);
			for (int index = 0; index < 8; index++) {
				id.append(IMAGE_ID_ALPHABET.charAt(randomIndex.applyAsInt(IMAGE_ID_ALPHABET.length())));
			}
			if (!excludedIds.contains(id.toString())) {
				return id.toString();
			}
		}
		throw new IllegalStateException("Could not generate an unused material image ID.");
	}

	private static String alternativeText(final String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("altText must describe the image.");
		}
		final String trimmed = value.trim();
		if (trimmed.length() > MAX_ALT_TEXT_LENGTH) {
			throw new IllegalArgumentException("altText must not exceed " + MAX_ALT_TEXT_LENGTH + " characters.");
		}
		return trimmed;
	}

	private static String encodeMetadata(final ImportedImage image) {
		return image.examId() + "|" + image.materialId() + "|" + image.imageId() + "|" + image.mediaType() + "|"
				+ image.pixelWidth() + "|" + image.pixelHeight() + "|" + image.byteSize();
	}

	private static ImportedImage decodeMetadata(final String value) {
		final String[] values = value.split("\\|", -1);
		if (values.length != 7) {
			throw new IllegalStateException("Invalid imported image receipt.");
		}
		return new ImportedImage(Integer.parseInt(values[0]), Integer.parseInt(values[1]), values[2], values[3],
				Integer.parseInt(values[4]), Integer.parseInt(values[5]), Long.parseLong(values[6]));
	}

	public record ImportedImage(int examId, int materialId, String imageId, String mediaType, int pixelWidth,
			int pixelHeight, long byteSize) {
	}

	public record ImportImageSnapshot(long revision, long mutationRevision, boolean replayed, ImportedImage metadata,
			LoeMaterial material) {
	}
}
