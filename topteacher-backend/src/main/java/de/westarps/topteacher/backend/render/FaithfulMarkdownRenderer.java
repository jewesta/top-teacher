package de.westarps.topteacher.backend.render;

import java.util.List;
import java.util.Objects;

import de.westarps.topteacher.model.loe.LoeMaterialImage;

/** Renders focused Markdown fragments with print-oriented styling. */
public interface FaithfulMarkdownRenderer {
	int MAX_SOURCE_IMAGES = 100;
	long MAX_SOURCE_IMAGE_BYTES = 32L * 1024 * 1024;
	long MAX_SOURCE_IMAGE_PIXELS = 12_000_000;

	RenderedPng render(RenderRequest request);

	enum Variant {
		STANDARD, MATERIAL, REQUIREMENT
	}

	record Section(String title, String markdown, Variant variant) {
		public Section {
			title = Objects.requireNonNullElse(title, "");
			markdown = Objects.requireNonNullElse(markdown, "");
			Objects.requireNonNull(variant, "variant");
		}
	}

	record RenderRequest(List<Section> sections, List<LoeMaterialImage> images) {
		public RenderRequest {
			sections = List.copyOf(sections);
			images = List.copyOf(images);
		}
	}

	record RenderedPng(byte[] data, int pixelWidth, int pixelHeight) {
		public RenderedPng {
			Objects.requireNonNull(data, "data");
			data = data.clone();
			if (pixelWidth < 1 || pixelHeight < 1) {
				throw new IllegalArgumentException("PNG dimensions must be positive.");
			}
		}

		@Override
		public byte[] data() {
			return data.clone();
		}
	}
}
