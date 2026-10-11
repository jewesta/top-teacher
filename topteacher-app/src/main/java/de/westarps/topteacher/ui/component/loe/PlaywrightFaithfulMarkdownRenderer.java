package de.westarps.topteacher.ui.component.loe;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.ColorScheme;
import com.microsoft.playwright.options.ScreenshotAnimations;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.WaitUntilState;

import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer;
import de.westarps.topteacher.ui.component.loe.McpMarkdownRenderTickets.Ticket;
import jakarta.annotation.PreDestroy;

/** Lazily captures the application's own viewers without persistent export files. */
@Service
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
@EnableConfigurationProperties(McpMarkdownRenderProperties.class)
public final class PlaywrightFaithfulMarkdownRenderer implements FaithfulMarkdownRenderer {

	private static final Logger LOGGER = LoggerFactory.getLogger(PlaywrightFaithfulMarkdownRenderer.class);
	static final int MAX_HEIGHT = 12_000;
	static final int MAX_OUTPUT_BYTES = 8 * 1024 * 1024;
	static final int MAX_SECTIONS = 201;
	static final int MAX_CHARACTERS = 200_000;
	static final long MAX_IMAGE_BYTES = 32L * 1024 * 1024;
	private static final String UNAVAILABLE = "Die PNG-Vorschau ist nicht verfügbar. Prüfe die Browser-Konfiguration.";
	private static final String TIMED_OUT = "Die PNG-Vorschau hat zu lange gedauert. Versuche es erneut.";
	private static final String TOO_LARGE = "Der Inhalt ist für eine einzelne PNG-Vorschau zu groß.";
	private static final String READY = """
		() => {
		   const root = document.getElementById('tt-mcp-markdown-render');
		   if (root?.dataset.renderStatus !== 'ready') return false;
		   const viewers = [...root.querySelectorAll('ws-markdown-viewer')];
		   return viewers.every(viewer => viewer.hasRendered === true)
		      && document.fonts.status === 'loaded'
		      && [...root.querySelectorAll('img')].every(image => image.complete);
		}
		""";
	private static final String SETTLE = """
		async () => {
		   const root = document.getElementById('tt-mcp-markdown-render');
		   await document.fonts.ready;
		   await Promise.all([...root.querySelectorAll('img')].map(image => image.decode().catch(() => {})));
		   await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
		   return [...root.querySelectorAll('img')].every(image => image.naturalWidth > 0);
		}
		""";
	private static final String READY_DIAGNOSTICS = """
		() => {
		   const root = document.getElementById('tt-mcp-markdown-render');
		   return {
		      documentReady: document.readyState,
		      rootPresent: !!root,
		      rootStatus: root?.dataset.renderStatus ?? null,
		      viewerRegistered: !!customElements.get('ws-markdown-viewer'),
		      viewerRendered: [...(root?.querySelectorAll('ws-markdown-viewer') ?? [])]
		         .map(viewer => viewer.hasRendered === true),
		      fontStatus: document.fonts.status,
		      imageState: [...(root?.querySelectorAll('img') ?? [])]
		         .map(image => ({complete: image.complete, loaded: image.naturalWidth > 0})),
		      bodyChildTags: [...document.body.children].map(child => child.tagName)
		   };
		}
		""";

	private final McpMarkdownRenderProperties properties;
	private final McpMarkdownRenderTickets tickets;
	private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
			new ArrayBlockingQueue<>(2), runnable -> {
				final Thread thread = new Thread(runnable, "tt-mcp-markdown-render");
				thread.setDaemon(true);
				return thread;
			}, new ThreadPoolExecutor.AbortPolicy()) {
		@Override
		protected void terminated() {
			try {
				closeBrowser();
			} catch (final RuntimeException ignored) {
				/* An interrupted or crashed browser may have closed its driver already. */
			} finally {
				super.terminated();
			}
		}
	};
	private volatile URI applicationUri;
	private Playwright playwright;
	private Browser browser;

	PlaywrightFaithfulMarkdownRenderer(final McpMarkdownRenderProperties properties,
			final McpMarkdownRenderTickets tickets) {
		this.properties = properties;
		this.tickets = tickets;
	}

	@EventListener
	void serverInitialized(final WebServerInitializedEvent event) {
		final String namespace = event.getApplicationContext().getServerNamespace();
		if (namespace != null && !namespace.isBlank()) {
			return;
		}
		final var environment = event.getApplicationContext().getEnvironment();
		final String path = environment.getProperty("server.servlet.context-path", "");
		final String scheme = environment.getProperty("server.ssl.enabled", Boolean.class, false) ? "https" : "http";
		applicationUri = URI.create(scheme + "://127.0.0.1:" + event.getWebServer().getPort()
				+ (path.equals("/") ? "" : path));
	}

	@Override
	public RenderedPng render(final RenderRequest request) {
		Objects.requireNonNull(request, "request");
		validateRequest(request);
		if (applicationUri == null) {
			throw new IllegalStateException(UNAVAILABLE);
		}
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(properties.timeoutSeconds());
		final Future<RenderedPng> future;
		try {
			future = worker.submit(() -> capture(request, deadline));
		} catch (final RejectedExecutionException exception) {
			throw new IllegalStateException("Die PNG-Vorschau ist gerade ausgelastet. Versuche es erneut.");
		}
		try {
			return future.get(properties.timeoutSeconds() + 2L, TimeUnit.SECONDS);
		} catch (final InterruptedException exception) {
			future.cancel(true);
			worker.purge();
			Thread.currentThread().interrupt();
			throw new IllegalStateException(TIMED_OUT);
		} catch (final TimeoutException exception) {
			future.cancel(true);
			worker.purge();
			throw new IllegalStateException(TIMED_OUT);
		} catch (final ExecutionException exception) {
			if (exception.getCause() instanceof RenderFailure failure) {
				throw new IllegalStateException(failure.getMessage());
			}
			throw new IllegalStateException(UNAVAILABLE);
		}
	}

	private RenderedPng capture(final RenderRequest request, final long deadline) {
		remainingMillis(deadline);
		final URI base = applicationUri;
		final Ticket ticket = tickets.issue(request, Duration.ofSeconds(properties.timeoutSeconds()));
		try {
			final Browser currentBrowser = browser(deadline);
			try (BrowserContext context = currentBrowser.newContext(new Browser.NewContextOptions()
					.setViewportSize(McpMarkdownRenderView.WIDTH + 48, 768).setDeviceScaleFactor(1)
					.setColorScheme(ColorScheme.LIGHT).setLocale("de-DE").setAcceptDownloads(false)
					.setIgnoreHTTPSErrors(true)
					.setServiceWorkers(ServiceWorkerPolicy.BLOCK))) {
				context.route("**/*", route -> {
					if (allowsRequest(base, ticket.id(), route.request())) {
						route.resume();
					} else {
						LOGGER.debug("MCP PNG blocked request: method={}, resourceType={}, path={}",
								route.request().method(), route.request().resourceType(), diagnosticPath(route.request().url()));
						route.abort();
					}
				});
				context.routeWebSocket("**/*", socket -> socket.close());
				final Page page = context.newPage();
				page.onPageError(error -> LOGGER.debug("MCP PNG browser script error category={}", scriptErrorCategory(error)));
				page.onRequestFailed(failed -> LOGGER.debug("MCP PNG failed request: resourceType={}, path={}",
						failed.resourceType(), diagnosticPath(failed.url())));
				page.onResponse(response -> {
					if (response.status() >= 400) {
						LOGGER.debug("MCP PNG HTTP failure: status={}, resourceType={}, path={}", response.status(),
								response.request().resourceType(), diagnosticPath(response.url()));
					}
				});
				page.navigate(base + "/" + McpMarkdownRenderView.ROUTE + "/" + ticket.id(),
						new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
								.setTimeout(remainingMillis(deadline)));
				page.addStyleTag(new Page.AddStyleTagOptions()
						.setContent("html,body { margin: 0; background: white; }"));
				try {
					page.waitForFunction(READY, null,
							new Page.WaitForFunctionOptions().setTimeout(remainingMillis(deadline)));
				} catch (final com.microsoft.playwright.TimeoutError exception) {
					logReadyDiagnostics(page);
					throw exception;
				}
				if (!Boolean.TRUE.equals(page.evaluate(SETTLE))) {
					throw new RenderFailure("Ein Bild konnte für die PNG-Vorschau nicht geladen werden.");
				}
				final Locator content = page.locator("#" + McpMarkdownRenderView.CONTENT_ID);
				final BoundingBox box = content.boundingBox(new Locator.BoundingBoxOptions()
						.setTimeout(remainingMillis(deadline)));
				if (box == null || Math.ceil(box.width) != McpMarkdownRenderView.WIDTH
						|| box.height < 1 || box.height > MAX_HEIGHT) {
					throw new RenderFailure(TOO_LARGE);
				}
				final byte[] png = content.screenshot(new Locator.ScreenshotOptions().setType(ScreenshotType.PNG)
						.setAnimations(ScreenshotAnimations.DISABLED).setTimeout(remainingMillis(deadline)));
				if (png.length > MAX_OUTPUT_BYTES) {
					throw new RenderFailure(TOO_LARGE);
				}
				return new RenderedPng(png, McpMarkdownRenderView.WIDTH, (int) Math.ceil(box.height));
			}
		} catch (final com.microsoft.playwright.TimeoutError exception) {
			throw new RenderFailure(TIMED_OUT);
		} finally {
			tickets.revoke(ticket);
			ticket.destroyPreviewSession();
		}
	}

	private Browser browser(final long deadline) {
		if (browser != null && browser.isConnected()) {
			return browser;
		}
		closeBrowser();
		final Map<String, String> driverEnvironment = new HashMap<>(System.getenv());
		driverEnvironment.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
		try {
			playwright = Playwright.create(new Playwright.CreateOptions().setEnv(driverEnvironment));
			final BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(true)
					.setTimeout(remainingMillis(deadline));
			if (properties.browserExecutable().isBlank()) {
				options.setChannel(properties.browserChannel());
			} else {
				final Path executable = Path.of(properties.browserExecutable());
				if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
					throw new RenderFailure(UNAVAILABLE);
				}
				options.setExecutablePath(executable);
			}
			browser = playwright.chromium().launch(options);
			return browser;
		} catch (final RuntimeException exception) {
			closeBrowser();
			throw new RenderFailure(UNAVAILABLE);
		}
	}

	static void validateRequest(final RenderRequest request) {
		final long characters = request.sections().stream()
				.mapToLong(section -> (long) section.title().length() + section.markdown().length()).sum();
		final long imageBytes = request.images().stream().mapToLong(image -> image.byteSize()).sum();
		if (request.sections().size() > MAX_SECTIONS || characters > MAX_CHARACTERS
				|| request.images().size() > 100 || imageBytes > MAX_IMAGE_BYTES) {
			throw new IllegalStateException(TOO_LARGE);
		}
	}

	private static void logReadyDiagnostics(final Page page) {
		if (!LOGGER.isDebugEnabled()) {
			return;
		}
		try {
			LOGGER.debug("MCP PNG readiness timeout: {}", page.evaluate(READY_DIAGNOSTICS));
		} catch (final RuntimeException exception) {
			LOGGER.debug("MCP PNG readiness diagnostics unavailable: {}", exception.getClass().getSimpleName());
		}
	}

	private static String diagnosticPath(final String url) {
		try {
			final String path = URI.create(url).getPath();
			if (path == null) {
				return "[inline resource]";
			}
			return path.replaceAll("(_internal/mcp-markdown-preview/)[^/]+", "$1[ticket]");
		} catch (final IllegalArgumentException exception) {
			return "[invalid resource URL]";
		}
	}

	private static String scriptErrorCategory(final String error) {
		for (final String category : new String[] { "TypeError", "ReferenceError", "SyntaxError", "RangeError" }) {
			if (error.contains(category)) {
				return category;
			}
		}
		return "JavaScriptError";
	}

	static boolean allowsRequest(final URI base, final String ticketId, final Request request) {
		return allowsRequest(base, ticketId, request.url(), request.resourceType(), request.method(),
				"true".equalsIgnoreCase(request.headers().get("x-devmodepoll")));
	}

	static boolean allowsRequest(final URI base, final String ticketId, final String url,
			final String resourceType, final String method) {
		return allowsRequest(base, ticketId, url, resourceType, method, false);
	}

	static boolean allowsRequest(final URI base, final String ticketId, final String url,
			final String resourceType, final String method, final boolean developmentPoll) {
		try {
			final URI target = URI.create(url);
			if (!Objects.equals(base.getScheme(), target.getScheme()) || !Objects.equals(base.getHost(), target.getHost())
					|| base.getPort() != target.getPort() || target.getUserInfo() != null) {
				return false;
			}
			final String path = target.getPath();
			final String root = base.getPath() + "/";
			if (!path.startsWith(root) || path.contains("/../") || path.contains("/./")) {
				return false;
			}
			final String relative = path.substring(root.length());
			if (relative.equals(McpMarkdownRenderView.ROUTE + "/" + ticketId)) {
				final boolean navigation = resourceType.equals("document");
				final boolean polling = developmentPoll && (resourceType.equals("fetch") || resourceType.equals("xhr"));
				return ((navigation || polling) && method.equals("GET")) || vaadinRpc(target, method)
						|| vaadinBootstrap(target, method, resourceType, ticketId);
			}
			if (relative.isEmpty()) {
				return vaadinRpc(target, method) || vaadinBootstrap(target, method, resourceType, ticketId);
			}
			return method.equals("GET") && (relative.startsWith("VAADIN/") || relative.startsWith("frontend/")
					|| relative.startsWith("themes/") || relative.startsWith("images/")
					|| relative.startsWith("icons/") || relative.startsWith("@vite/")
					|| relative.startsWith("@id/") || relative.startsWith("@fs/")
					|| relative.startsWith("node_modules/") || relative.startsWith("src/"));
		} catch (final IllegalArgumentException exception) {
			return false;
		}
	}

	private static boolean vaadinRpc(final URI target, final String method) {
		final String query = target.getRawQuery();
		return method.equals("POST") && query != null
				&& query.matches(".*(?:^|&)v-r=(?:init|uidl|heartbeat)(?:&.*)?");
	}

	private static boolean vaadinBootstrap(final URI target, final String method,
			final String resourceType, final String ticketId) {
		return method.equals("GET") && (resourceType.equals("xhr") || resourceType.equals("fetch"))
				&& "init".equals(queryParameter(target, "v-r"))
				&& (McpMarkdownRenderView.ROUTE + "/" + ticketId).equals(queryParameter(target, "location"));
	}

	private static String queryParameter(final URI target, final String name) {
		final String query = target.getRawQuery();
		if (query == null) {
			return null;
		}
		String result = null;
		for (final String parameter : query.split("&")) {
			final String[] pair = parameter.split("=", 2);
			if (name.equals(URLDecoder.decode(pair[0], StandardCharsets.UTF_8))) {
				if (result != null) {
					return null;
				}
				result = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
			}
		}
		return result;
	}

	private static double remainingMillis(final long deadline) {
		final long remaining = deadline - System.nanoTime();
		if (remaining <= 0 || Thread.currentThread().isInterrupted()) {
			throw new RenderFailure(TIMED_OUT);
		}
		return Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining));
	}

	@PreDestroy
	void shutdown() {
		worker.shutdownNow().forEach(task -> {
			if (task instanceof Future<?> queued) {
				queued.cancel(false);
			}
		});
		try {
			worker.awaitTermination(5, TimeUnit.SECONDS);
		} catch (final InterruptedException exception) {
			worker.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private void closeBrowser() {
		final Browser oldBrowser = browser;
		final Playwright oldPlaywright = playwright;
		browser = null;
		playwright = null;
		try {
			if (oldBrowser != null) {
				oldBrowser.close();
			}
		} finally {
			if (oldPlaywright != null) {
				oldPlaywright.close();
			}
		}
	}

	private static final class RenderFailure extends RuntimeException {
		private RenderFailure(final String message) {
			super(message);
		}
	}
}
