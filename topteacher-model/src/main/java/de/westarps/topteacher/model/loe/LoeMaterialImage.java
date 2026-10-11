package de.westarps.topteacher.model.loe;

import java.util.Arrays;
import java.util.Objects;

public record LoeMaterialImage(String id, String contentHash, String mediaType, int pixelWidth, int pixelHeight,
		long byteSize, byte[] data) {

	public LoeMaterialImage {
		id = Objects.requireNonNull(id, "id must not be null");
		contentHash = Objects.requireNonNull(contentHash, "contentHash must not be null");
		mediaType = Objects.requireNonNull(mediaType, "mediaType must not be null");
		data = Arrays.copyOf(Objects.requireNonNull(data, "data must not be null"), data.length);
		if (!id.matches("[a-z0-9]{8}")) {
			throw new IllegalArgumentException("image id must contain eight lowercase letters or digits");
		}
		if (!contentHash.matches("[a-f0-9]{64}")) {
			throw new IllegalArgumentException("content hash must be a lowercase SHA-256 value");
		}
		if (!mediaType.equals("image/png") && !mediaType.equals("image/jpeg")) {
			throw new IllegalArgumentException("unsupported image media type");
		}
		if (pixelWidth <= 0 || pixelHeight <= 0 || byteSize <= 0 || byteSize != data.length) {
			throw new IllegalArgumentException("invalid image dimensions or byte size");
		}
	}

	@Override
	public byte[] data() {
		return Arrays.copyOf(data, data.length);
	}
}
