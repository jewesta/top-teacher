package de.westarps.topteacher.backend.render;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.java2d.Java2DRenderer;
import com.openhtmltopdf.java2d.api.FSPage;
import com.openhtmltopdf.java2d.api.FSPageProcessor;
import com.openhtmltopdf.java2d.api.Java2DRendererBuilder;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceType;
import com.openhtmltopdf.resource.ImageResource;
import com.openhtmltopdf.swing.AWTFSImage;
import com.openhtmltopdf.swing.NaiveUserAgent;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import com.openhtmltopdf.util.ThreadCtx;

import de.westarps.topteacher.model.loe.LoeMaterialImage;
import jakarta.annotation.PreDestroy;

/** Independent fragment renderer; the existing PDF export pipeline is not involved. */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class Java2DFaithfulMarkdownRenderer implements FaithfulMarkdownRenderer {

	static final int MAX_WIDTH = 960;
	static final int MAX_HEIGHT = 12_000;
	static final int MAX_PNG_BYTES = 8 * 1024 * 1024;
	static final int MAX_CHARACTERS = 200_000;
	static final int MAX_SECTIONS = 201;
	static final int MAX_IMAGES = MAX_SOURCE_IMAGES;
	static final long MAX_IMAGE_BYTES = MAX_SOURCE_IMAGE_BYTES;
	static final long MAX_IMAGE_PIXELS = MAX_SOURCE_IMAGE_PIXELS;
	private static final String CSS = resourceText("render/markdown-fragment.css");
	private static final Map<String, String> ALERT_ICONS = Map.of(
			"note", resourceText("templates/export/markdown-alerts/note.svg"),
			"tip", resourceText("templates/export/markdown-alerts/tip.svg"),
			"important", resourceText("templates/export/markdown-alerts/important.svg"),
			"warning", resourceText("templates/export/markdown-alerts/warning.svg"),
			"caution", resourceText("templates/export/markdown-alerts/caution.svg"));
	private final MarkdownFragmentHtmlRenderer htmlRenderer = new MarkdownFragmentHtmlRenderer();
	private final Duration timeout;
	private final ThreadPoolExecutor worker = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
			new SynchronousQueue<>(), runnable -> {
				final Thread thread = new Thread(runnable, "tt-mcp-png-render");
				thread.setDaemon(true);
				return thread;
			});

	public Java2DFaithfulMarkdownRenderer(
			@Value("${tt.mcp.render.timeout-seconds:30}") final int timeoutSeconds) {
		if (timeoutSeconds < 5 || timeoutSeconds > 60) {
			throw new IllegalArgumentException("PNG render timeout must be between 5 and 60 seconds.");
		}
		timeout = Duration.ofSeconds(timeoutSeconds);
	}

	@Override
	public RenderedPng render(final RenderRequest request) {
		validateInput(request);
		final Future<RenderedPng> result;
		try {
			result = worker.submit(() -> renderFragment(request));
		} catch (final RejectedExecutionException exception) {
			throw new IllegalStateException("PNG renderer is busy or stopped.", exception);
		}
		try {
			return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (final InterruptedException exception) {
			result.cancel(true);
			Thread.currentThread().interrupt();
			throw new IllegalStateException("PNG rendering was interrupted.", exception);
		} catch (final TimeoutException exception) {
			/* A cancelled job retains the sole worker until it actually exits. */
			result.cancel(true);
			throw new IllegalStateException("PNG rendering timed out.", exception);
		} catch (final ExecutionException exception) {
			throw new IllegalStateException("PNG rendering failed.", exception.getCause());
		}
	}

	@PreDestroy
	public void close() {
		worker.shutdownNow();
	}

	private RenderedPng renderFragment(final RenderRequest request) throws IOException {
		final var fragment = htmlRenderer.render(request);
		final Map<String, byte[]> images = validatedImages(fragment.imageResources());
		checkInterrupted();
		final Document document = Jsoup.parse("<html><head><style>" + CSS + "</style></head><body>"
				+ fragment.html() + "</body></html>");
		embedAlertIcons(document);
		boundImageGeometry(document, fragment.imageResources());
		final java.util.List<String> numberedBoxIds = new java.util.ArrayList<>();
		for (final Element numbered : document.select("table.ws-markdown-numbered")) {
			final String id = "tt-render-numbered-" + numberedBoxIds.size();
			numbered.attr("id", id);
			numberedBoxIds.add(id);
		}
		document.outputSettings().syntax(Document.OutputSettings.Syntax.xml)
				.escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml).charset(StandardCharsets.UTF_8).prettyPrint(false);
		final BoundedPageProcessor pages = new BoundedPageProcessor();
		final BufferedImage measurement = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		final Graphics2D layoutGraphics = measurement.createGraphics();
		configureGraphics(layoutGraphics);
		try (BatikSVGDrawer svgDrawer = new BatikSVGDrawer();
				SnapshotImageUserAgent imageAgent = new SnapshotImageUserAgent(images)) {
			final Java2DRendererBuilder builder = new Java2DRendererBuilder();
			builder.useFastMode();
			builder.useEnvironmentFonts(false);
			builder.useLayoutGraphics(layoutGraphics);
			builder.useSVGDrawer(svgDrawer);
			addFont(builder, "FrugalSansLight.ttf", 400, FontStyle.NORMAL);
			addFont(builder, "FrugalSansLightItalic.ttf", 400, FontStyle.ITALIC);
			addFont(builder, "FrugalSansBold.ttf", 700, FontStyle.NORMAL);
			addFont(builder, "FrugalSansBoldItalic.ttf", 700, FontStyle.ITALIC);
			builder.useUriResolver((baseUri, uri) -> images.containsKey(uri) ? uri : null);
			builder.useExternalResourceAccessControl((uri, type) -> images.containsKey(uri),
					ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
			builder.useExternalResourceAccessControl((uri, type) -> images.containsKey(uri),
					ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
			builder.useProtocolsStreamImplementation(uri -> imageStream(images.get(uri)), "tt-image");
			builder.withHtmlContent(document.html(), "tt-fragment:");
			builder.toSinglePage(pages);
			try (Java2DRenderer renderer = builder.buildJava2DRenderer()) {
				/* Replace only this render's image decoder; do not change ImageIO's global cache setting. */
				ThreadCtx.get().sharedContext().setUserAgentCallback(imageAgent);
				renderer.layout();
				for (final String id : numberedBoxIds) {
					final var box = ThreadCtx.get().sharedContext().getBoxById(id);
					if (box == null || box.getWidth() > MAX_WIDTH - 48) {
						throw new IllegalArgumentException("Numbered source line overflows the fragment width.");
					}
				}
				checkInterrupted();
				renderer.writeSinglePage();
			}
			checkInterrupted();
			final BufferedImage image = pages.image();
			final LimitedOutputStream output = new LimitedOutputStream(MAX_PNG_BYTES);
			try (var pngOutput = new BoundedImageOutputStream(output, MAX_PNG_BYTES)) {
				if (!ImageIO.write(image, "png", pngOutput)) {
					throw new IOException("PNG encoder is unavailable.");
				}
				pngOutput.flush();
			}
			checkInterrupted();
			return new RenderedPng(output.toByteArray(), image.getWidth(), image.getHeight());
		} finally {
			layoutGraphics.dispose();
			measurement.flush();
			pages.close();
		}
	}

	private static void validateInput(final RenderRequest request) {
		if (request.sections().size() > MAX_SECTIONS || request.images().size() > MAX_IMAGES) {
			throw new IllegalArgumentException("Too many sections or images for a PNG fragment.");
		}
		long characters = 0;
		for (final Section section : request.sections()) {
			characters += (long) section.title().length() + section.markdown().length();
		}
		long bytes = 0;
		long pixels = 0;
		for (final LoeMaterialImage image : request.images()) {
			bytes += image.byteSize();
			pixels += (long) image.pixelWidth() * image.pixelHeight();
			if (bytes > MAX_IMAGE_BYTES || pixels > MAX_IMAGE_PIXELS) {
				throw new IllegalArgumentException("Material images exceed the PNG rendering budget.");
			}
		}
		if (characters > MAX_CHARACTERS) {
			throw new IllegalArgumentException("Markdown exceeds the PNG rendering budget.");
		}
	}

	private static Map<String, byte[]> validatedImages(final Map<String, LoeMaterialImage> resources)
			throws IOException {
		final Map<String, byte[]> result = new java.util.HashMap<>();
		long pixels = 0;
		for (final var resource : resources.entrySet()) {
			checkInterrupted();
			final LoeMaterialImage image = resource.getValue();
			final byte[] bytes = image.data();
			/* Inspect encoded dimensions without allocating the decoded raster. */
			try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
				final var readers = ImageIO.getImageReaders(input);
				if (!readers.hasNext()) {
					throw new IOException("Material image cannot be decoded.");
				}
				final ImageReader reader = readers.next();
				try {
					reader.setInput(input, true, true);
					final int width = reader.getWidth(0);
					final int height = reader.getHeight(0);
					pixels += (long) width * height;
					if (width != image.pixelWidth() || height != image.pixelHeight() || pixels > MAX_IMAGE_PIXELS) {
						throw new IOException("Encoded material image dimensions exceed the rendering budget.");
					}
				} finally {
					reader.dispose();
				}
			}
			result.put(resource.getKey(), bytes);
		}
		return result;
	}

	private static FSStream imageStream(final byte[] bytes) {
		checkInterrupted();
		if (bytes == null) {
			return null;
		}
		return new FSStream() {
			@Override
			public InputStream getStream() {
				return new ByteArrayInputStream(bytes);
			}

			@Override
			public Reader getReader() {
				return null;
			}
		};
	}

	private static void addFont(final Java2DRendererBuilder builder, final String fileName, final int weight,
			final FontStyle style) {
		builder.useFont(() -> resourceStream("templates/frugal-sans/" + fileName), "Frugal Sans", weight, style, true);
	}

	private static void embedAlertIcons(final Document document) {
		for (final var icon : ALERT_ICONS.entrySet()) {
			for (final Element target : document.select(".ws-markdown-alert-" + icon.getKey()
					+ " > .ws-markdown-alert-title > .ws-markdown-alert-icon")) {
				final Element svg = Jsoup.parseBodyFragment(icon.getValue()).selectFirst("svg");
				if (svg != null) {
					svg.attr("width", "18").attr("height", "18");
					target.empty().appendChild(svg);
				}
			}
		}
	}

	private static void boundImageGeometry(final Document document, final Map<String, LoeMaterialImage> resources) {
		long scaledPixels = 0;
		for (final Element element : document.select("img.tt-material-image")) {
			final String uri = element.attr("src");
			final LoeMaterialImage image = resources.get(uri);
			if (image == null) {
				throw new IllegalArgumentException("Material image is outside the rendering snapshot.");
			}
			final var percent = java.util.regex.Pattern.compile("width:(100|[1-9][0-9]?)%;height:auto")
					.matcher(element.attr("style"));
			final int width = percent.matches() ? Math.max(1, 912 * Integer.parseInt(percent.group(1)) / 100)
					: Math.min(912, image.pixelWidth());
			final long height = Math.max(1, Math.round((double) image.pixelHeight() * width / image.pixelWidth()));
			if (height > MAX_HEIGHT) {
				throw new IllegalArgumentException("Scaled material image is too tall for a PNG fragment.");
			}
			/* Count every occurrence: narrower containers can create additional cached sizes. */
			scaledPixels += width * height;
			if (scaledPixels > MAX_IMAGE_PIXELS) {
				throw new IllegalArgumentException("Scaled material images exceed the rendering budget.");
			}
		}
	}

	private static void configureGraphics(final Graphics2D graphics) {
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
	}

	private static void checkInterrupted() {
		if (Thread.currentThread().isInterrupted()) {
			throw new IllegalStateException("PNG rendering was cancelled.");
		}
	}

	private static InputStream resourceStream(final String path) {
		final InputStream input = Java2DFaithfulMarkdownRenderer.class.getClassLoader().getResourceAsStream(path);
		if (input == null) {
			throw new IllegalStateException("Missing PNG renderer resource: " + path);
		}
		return input;
	}

	private static String resourceText(final String path) {
		try (InputStream input = resourceStream(path)) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		} catch (final IOException exception) {
			throw new IllegalStateException("Could not read PNG renderer resource: " + path, exception);
		}
	}

	private static final class SnapshotImageUserAgent extends NaiveUserAgent implements AutoCloseable {
		private final Map<String, byte[]> images;
		private final Map<String, BufferedImage> decoded = new java.util.HashMap<>();

		SnapshotImageUserAgent(final Map<String, byte[]> images) {
			this.images = images;
			setUriResolver((baseUri, uri) -> images.containsKey(uri) ? uri : null);
			setAccessController(ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI, (uri, type) -> false);
			setAccessController(ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI, (uri, type) -> false);
		}

		@Override
		public ImageResource getImageResource(final String uri, final ExternalResourceType type) {
			checkInterrupted();
			if (!images.containsKey(uri)) {
				return new ImageResource(uri, null);
			}
			BufferedImage image = decoded.get(uri);
			if (image == null) {
				try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(images.get(uri)))) {
					final var readers = ImageIO.getImageReaders(input);
					if (!readers.hasNext()) {
						throw new IOException("Material image cannot be decoded.");
					}
					final ImageReader reader = readers.next();
					try {
						reader.setInput(input, true, true);
						image = reader.read(0);
					} finally {
						reader.dispose();
					}
					decoded.put(uri, image);
				} catch (final IOException exception) {
					throw new IllegalStateException("Material image decoding failed.", exception);
				}
			}
			return new ImageResource(uri, AWTFSImage.createImage(image));
		}

		@Override
		public void close() {
			decoded.values().forEach(BufferedImage::flush);
			decoded.clear();
		}
	}

	static final class BoundedPageProcessor implements FSPageProcessor, AutoCloseable {
		private BufferedImage image;
		private Graphics2D graphics;

		@Override
		public FSPage createPage(final int pageNumber, final int width, final int height) {
			checkInterrupted();
			if (image != null || pageNumber != 0 || width < 1 || width > MAX_WIDTH || height < 1 || height > MAX_HEIGHT) {
				throw new IllegalArgumentException("PNG fragment dimensions exceed the rendering budget.");
			}
			image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			graphics = image.createGraphics();
			configureGraphics(graphics);
			graphics.setColor(java.awt.Color.WHITE);
			graphics.fillRect(0, 0, width, height);
			return () -> graphics;
		}

		@Override
		public void finishPage(final FSPage page) {
			checkInterrupted();
		}

		BufferedImage image() {
			if (image == null) {
				throw new IllegalStateException("PNG fragment did not produce a canvas.");
			}
			return image;
		}

		@Override
		public void close() {
			if (graphics != null) {
				graphics.dispose();
			}
			if (image != null) {
				image.flush();
			}
		}
	}

	static final class LimitedOutputStream extends OutputStream {
		private final ByteArrayOutputStream output = new ByteArrayOutputStream();
		private final int limit;

		LimitedOutputStream(final int limit) {
			this.limit = limit;
		}

		@Override
		public void write(final int value) throws IOException {
			checkLength(1);
			output.write(value);
		}

		@Override
		public void write(final byte[] bytes, final int offset, final int length) throws IOException {
			checkLength(length);
			output.write(bytes, offset, length);
		}

		byte[] toByteArray() {
			return output.toByteArray();
		}

		private void checkLength(final int additionalBytes) throws IOException {
			checkInterrupted();
			if ((long) output.size() + additionalBytes > limit) {
				throw new IOException("PNG exceeds the response byte budget.");
			}
		}
	}

	/* ImageIO may seek while encoding; bound its memory cache as well as the output. */
	private static final class BoundedImageOutputStream extends MemoryCacheImageOutputStream {
		private final int limit;

		BoundedImageOutputStream(final OutputStream output, final int limit) {
			super(output);
			this.limit = limit;
		}

		@Override
		public void write(final int value) throws IOException {
			checkLength(1);
			super.write(value);
		}

		@Override
		public void write(final byte[] bytes, final int offset, final int length) throws IOException {
			checkLength(length);
			super.write(bytes, offset, length);
		}

		private void checkLength(final int additionalBytes) throws IOException {
			checkInterrupted();
			if (getStreamPosition() + additionalBytes > limit) {
				throw new IOException("PNG exceeds the response byte budget.");
			}
		}
	}
}
