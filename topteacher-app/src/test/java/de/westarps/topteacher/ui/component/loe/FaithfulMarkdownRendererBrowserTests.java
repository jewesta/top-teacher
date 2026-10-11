package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.RenderRequest;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.Section;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.Variant;
import de.westarps.topteacher.model.loe.LoeMaterialImage;

/** Explicit browser verification for changes to the faithful preview path. */
@EnabledIfSystemProperty(named = "tt.mcp.render.integration", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"tt.mcp.enabled=true", "tt.mcp.token-file=", "server.servlet.context-path=/top-teacher",
		"spring.datasource.url=jdbc:h2:mem:mcp-png-browser;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class FaithfulMarkdownRendererBrowserTests {

	@Autowired private FaithfulMarkdownRenderer renderer;

	@Test
	void capturesSharedMarkdownComponentsAndIsolatesTheNextRequest() throws Exception {
		final BufferedImage source = new BufferedImage(120, 60, BufferedImage.TYPE_INT_RGB);
		final var graphics = source.createGraphics();
		graphics.setColor(new Color(30, 140, 190));
		graphics.fillRect(0, 0, 120, 60);
		graphics.dispose();
		final ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
		ImageIO.write(source, "png", imageBytes);
		final LoeMaterialImage image = new LoeMaterialImage("a1b2c3d4", "0".repeat(64), "image/png", 120, 60,
				imageBytes.size(), imageBytes.toByteArray());
		final var result = renderer.render(new RenderRequest(List.of(
				new Section("Aufgabe 1", "**Erkläre** die Quelle.\n\n> [!NOTE]\n> Das ist ein Hinweis.", Variant.STANDARD),
				new Section("Quelle 1", "Ein Text mit Fußnote[^1].\n\n> [!NUMBERED]\n> Erste Zeile\n> Zweite Zeile\n"
						+ "\n![Blaues Testbild](img:a1b2c3d4)\n\n[^1]: Eine Quellenangabe.", Variant.MATERIAL),
				new Section("Anforderung 1", "Verwendet [Belege](crit:evidence?points=1.5).", Variant.REQUIREMENT)),
				List.of(image)));
		final BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.data()));
		assertThat(decoded).isNotNull();
		assertThat(decoded.getWidth()).isEqualTo(960).isEqualTo(result.pixelWidth());
		assertThat(decoded.getHeight()).isGreaterThan(200).isEqualTo(result.pixelHeight());
		assertThat(result.data().length).isLessThanOrEqualTo(8 * 1024 * 1024);
		final int blueRgb = new Color(30, 140, 190).getRGB() & 0xFFFFFF;
		int bluePixels = 0;
		for (int y = 0; y < decoded.getHeight(); y++) {
			for (int x = 0; x < decoded.getWidth(); x++) {
				if ((decoded.getRGB(x, y) & 0xFFFFFF) == blueRgb) {
					bluePixels++;
				}
			}
		}
		assertThat(bluePixels).as("The managed material image must appear in the captured preview").isGreaterThan(500);
		final String output = System.getProperty("tt.mcp.render.test-output");
		if (output != null && !output.isBlank()) {
			Files.write(Path.of(output), result.data());
		}
		final var next = renderer.render(new RenderRequest(List.of(new Section("", "Nur dieser Text.", Variant.STANDARD)),
				List.of()));
		assertThat(next.pixelHeight()).isLessThan(result.pixelHeight());
	}
}
