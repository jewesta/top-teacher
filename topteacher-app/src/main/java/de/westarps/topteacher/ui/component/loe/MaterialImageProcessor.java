package de.westarps.topteacher.ui.component.loe;

import de.westarps.topteacher.model.loe.LoeMaterialImage;

/** Keeps the material editor on the shared backend image-normalization pipeline. */
final class MaterialImageProcessor {

	static final int MAX_UPLOAD_BYTES = de.westarps.topteacher.backend.file.MaterialImageProcessor.MAX_UPLOAD_BYTES;
	static final long MAX_DECODED_PIXELS = de.westarps.topteacher.backend.file.MaterialImageProcessor.MAX_DECODED_PIXELS;
	static final int MAX_LONGEST_SIDE = de.westarps.topteacher.backend.file.MaterialImageProcessor.MAX_LONGEST_SIDE;
	private final de.westarps.topteacher.backend.file.MaterialImageProcessor delegate =
			new de.westarps.topteacher.backend.file.MaterialImageProcessor();

	LoeMaterialImage process(final String id, final byte[] uploadedData) {
		return delegate.process(id, uploadedData);
	}
}
