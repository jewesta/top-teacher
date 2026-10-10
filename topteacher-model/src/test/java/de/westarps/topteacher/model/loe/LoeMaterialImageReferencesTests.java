package de.westarps.topteacher.model.loe;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoeMaterialImageReferencesTests {

	@Test
	void findsManagedImageReferencesAndIgnoresOtherImages() {
		assertThat(LoeMaterialImageReferences.ids("""
			![Diagramm](img:4n8k2p7c?width=75)
			![Foto](img:abcdefgh)
			![Extern](https://example.com/image.png)
			![Ungültig](img:TOO-LONG)
			""")).containsExactlyInAnyOrder("4n8k2p7c", "abcdefgh");
	}

	@Test
	void replacesOnlyTheManagedImageIdentifier() {
		assertThat(LoeMaterialImageReferences.replaceId(
				"![Diagramm](img:4n8k2p7c?width=75) img:4n8k2p7c", "4n8k2p7c", "abcdefgh"))
				.isEqualTo("![Diagramm](img:abcdefgh?width=75) img:4n8k2p7c");
	}
}
