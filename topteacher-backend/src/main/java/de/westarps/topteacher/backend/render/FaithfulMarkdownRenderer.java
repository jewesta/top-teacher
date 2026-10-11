package de.westarps.topteacher.backend.render;

import java.util.List;
import java.util.Objects;

import de.westarps.topteacher.model.loe.LoeMaterialImage;

/** Browser implementations render the same components as the application preview. */
public interface FaithfulMarkdownRenderer {

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
