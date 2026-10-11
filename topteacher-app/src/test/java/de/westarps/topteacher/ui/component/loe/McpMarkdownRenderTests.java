package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.WrappedSession;

import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.RenderRequest;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.Section;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.Variant;
import de.westarps.vaadin.markdown.MarkdownViewer;

class McpMarkdownRenderTests {

	private static final RenderRequest REQUEST = new RenderRequest(List.of(
			new Section("Task", "**Selected task only**", Variant.STANDARD)), List.of());
	private static final URI BASE = URI.create("http://127.0.0.1:8081/top-teacher");

	@Test
	void ticketsExpireAndCanBeConsumedOnlyOnce() {
		final Clock clock = mock(Clock.class);
		final Instant now = Instant.parse("2026-10-11T12:00:00Z");
		when(clock.instant()).thenReturn(now);
		final McpMarkdownRenderTickets tickets = new McpMarkdownRenderTickets(clock);
		final var first = tickets.issue(REQUEST, Duration.ofSeconds(30));
		assertThat(tickets.consume(first.id())).contains(first);
		assertThat(tickets.consume(first.id())).isEmpty();
		assertThat(tickets.consume("unknown")).isEmpty();

		final var expired = tickets.issue(REQUEST, Duration.ofSeconds(30));
		when(clock.instant()).thenReturn(now.plusSeconds(30));
		assertThat(tickets.consume(expired.id())).isEmpty();
	}

	@Test
	void ticketsCanBeRevokedAndStorageIsBounded() {
		final var tickets = new McpMarkdownRenderTickets(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
		final var first = tickets.issue(REQUEST, Duration.ofSeconds(30));
		tickets.issue(REQUEST, Duration.ofSeconds(30));
		tickets.issue(REQUEST, Duration.ofSeconds(30));
		assertThatIllegalStateException().isThrownBy(() -> tickets.issue(REQUEST, Duration.ofSeconds(30)));
		tickets.revoke(first);
		assertThat(tickets.consume(first.id())).isEmpty();
		assertThat(tickets.issue(REQUEST, Duration.ofSeconds(30))).isNotNull();
	}

	@Test
	void requestRulesPermitOnlyTicketBootstrapRpcAndLocalAssets() {
		assertThat(allowed("_internal/mcp-markdown-preview/ticket", "document", "GET")).isTrue();
		assertThat(allowed("_internal/mcp-markdown-preview/ticket?v-r=uidl&v-uiId=0", "fetch", "POST")).isTrue();
		assertThat(allowed("?v-r=init&location=preview", "fetch", "POST")).isTrue();
		assertThat(allowed("?v-uiId=0&v-r=heartbeat", "fetch", "POST")).isTrue();
		assertThat(allowed("VAADIN/dynamic/resource/0/abc/chart.png", "image", "GET")).isTrue();
		assertThat(allowed("VAADIN/build/assets/index.js", "script", "GET")).isTrue();
		assertThat(allowed("frontend/theme.css", "stylesheet", "GET")).isTrue();
		assertThat(allowed("_internal/mcp-markdown-preview/another", "document", "GET")).isFalse();
		assertThat(allowed("exams/2", "image", "GET")).isFalse();
		assertThat(allowed("h2-console", "document", "GET")).isFalse();
		assertThat(allowed("mcp", "fetch", "POST")).isFalse();
		assertThat(allowed("?v-r=export", "fetch", "POST")).isFalse();
		assertThat(allowed("VAADIN/../exams", "image", "GET")).isFalse();
		assertThat(allowed("VAADIN/%2e%2e/exams", "image", "GET")).isFalse();
		assertThat(PlaywrightFaithfulMarkdownRenderer.allowsRequest(BASE, "ticket",
				"http://localhost:8081/top-teacher/VAADIN/test.js", "script", "GET")).isFalse();
		assertThat(PlaywrightFaithfulMarkdownRenderer.allowsRequest(BASE, "ticket",
				"http://127.0.0.1:5173/frontend/test.js", "script", "GET")).isFalse();
		assertThat(PlaywrightFaithfulMarkdownRenderer.allowsRequest(BASE, "ticket",
				"https://example.org/image.png", "image", "GET")).isFalse();
		assertThat(PlaywrightFaithfulMarkdownRenderer.allowsRequest(BASE, "ticket",
				"file:///private/data.png", "image", "GET")).isFalse();
	}

	@Test
	void previewUsesSharedViewersWithoutResultControlsAndCanReleaseContent() {
		final var view = new McpMarkdownRenderView(new McpMarkdownRenderTickets());
		view.populate(new RenderRequest(List.of(new Section("Task", "**Task text**", Variant.STANDARD),
				new Section("Material", "Source text", Variant.MATERIAL),
				new Section("Requirement", "[criterion](crit:1)", Variant.REQUIREMENT)), List.of()));
		final List<MarkdownViewer> viewers = view.getChildren().flatMap(Component::getChildren)
				.filter(MarkdownViewer.class::isInstance).map(MarkdownViewer.class::cast).toList();
		assertThat(view.getWidth()).isEqualTo("960px");
		assertThat(viewers).hasSize(3);
		assertThat(viewers.get(0).getExtensionIds()).isEmpty();
		assertThat(viewers.get(1)).isInstanceOf(MaterialMarkdownViewer.class);
		assertThat(viewers.get(1).getExtensionIds()).containsExactly("tt-material-image");
		assertThat(viewers.get(2).getExtensionIds()).containsExactly("tt-criterion");
		assertThat(viewers.get(2).getExtensionState()).doesNotContainKey("criterionCheckboxes");
		view.release();
		assertThat(view.getChildren()).isEmpty();
		assertThat(viewers.get(1).getContent()).isEmpty();
		assertThat(viewers.get(1).getExtensionState()).isEmpty();
	}

	@Test
	void developmentBundlePollsRequireTheirHeaderAndTheCurrentTicketUrl() {
		final String url = BASE + "/_internal/mcp-markdown-preview/ticket";
		assertThat(PlaywrightFaithfulMarkdownRenderer.allowsRequest(BASE, "ticket", url, "fetch", "GET", true)).isTrue();
		assertThat(PlaywrightFaithfulMarkdownRenderer.allowsRequest(BASE, "ticket", url, "xhr", "GET", true)).isTrue();
		assertThat(PlaywrightFaithfulMarkdownRenderer.allowsRequest(BASE, "ticket", url, "fetch", "GET", false)).isFalse();
		assertThat(PlaywrightFaithfulMarkdownRenderer.allowsRequest(BASE, "another", url, "fetch", "GET", true)).isFalse();
		assertThat(PlaywrightFaithfulMarkdownRenderer.allowsRequest(BASE, "ticket", BASE + "/exams/2", "fetch", "GET", true)).isFalse();
		assertThat(PlaywrightFaithfulMarkdownRenderer.allowsRequest(BASE, "ticket", url, "image", "GET", true)).isFalse();
	}

	@Test
	void getBootstrapCanInitializeOnlyTheCurrentTicketRoute() {
		final String bootstrap = "?v-r=init&location=_internal%2Fmcp-markdown-preview%2Fticket&query=";
		assertThat(allowed(bootstrap, "xhr", "GET")).isTrue();
		assertThat(allowed(bootstrap, "fetch", "GET")).isTrue();
		assertThat(allowed(bootstrap, "image", "GET")).isFalse();
		assertThat(allowed(bootstrap, "document", "GET")).isFalse();
		assertThat(allowed("?v-r=init&location=_internal%2Fmcp-markdown-preview%2Fanother", "xhr", "GET")).isFalse();
		assertThat(allowed("?v-r=init&location=exams%2F2", "xhr", "GET")).isFalse();
		assertThat(allowed("?v-r=init", "xhr", "GET")).isFalse();
		assertThat(allowed(bootstrap + "&location=exams%2F2", "xhr", "GET")).isFalse();
		assertThat(allowed(bootstrap + "&v-r=uidl", "xhr", "GET")).isFalse();
		assertThat(allowed("?v-r=uidl&location=_internal%2Fmcp-markdown-preview%2Fticket", "xhr", "GET")).isFalse();
	}

	@Test
	void previewTeardownReleasesItsViewAndInvalidatesTheHttpSessionOnce() {
		final var tickets = new McpMarkdownRenderTickets();
		final var ticket = tickets.issue(REQUEST, Duration.ofSeconds(30));
		final VaadinSession session = mock(VaadinSession.class);
		final WrappedSession httpSession = mock(WrappedSession.class);
		final McpMarkdownRenderView view = mock(McpMarkdownRenderView.class);
		when(session.getSession()).thenReturn(httpSession);
		when(session.getUIs()).thenReturn(List.of());
		doAnswer(invocation -> {
			invocation.getArgument(0, Command.class).execute();
			return null;
		}).when(session).accessSynchronously(any(Command.class));
		ticket.attach(session, view);
		ticket.destroyPreviewSession();
		ticket.destroyPreviewSession();
		verify(view).release();
		verify(session).close();
		verify(httpSession).invalidate();
	}

	@Test
	void invalidBrowserConfigurationIsRejectedWithoutLaunchingAnything() {
		assertThat(new McpMarkdownRenderProperties("", "chrome", 30).browserChannel()).isEqualTo("chrome");
		assertThatIllegalArgumentException().isThrownBy(() -> new McpMarkdownRenderProperties("relative/path", "chrome", 30));
		assertThatIllegalArgumentException().isThrownBy(() -> new McpMarkdownRenderProperties("", "firefox", 30));
		assertThatIllegalArgumentException().isThrownBy(() -> new McpMarkdownRenderProperties("", "chrome", 1));
	}

	@Test
	void attachmentAfterTimeoutCannotRetainAPreviewSession() {
		final var tickets = new McpMarkdownRenderTickets();
		final var ticket = tickets.issue(REQUEST, Duration.ofSeconds(30));
		assertThat(tickets.consume(ticket.id())).contains(ticket);
		ticket.destroyPreviewSession();

		final VaadinSession session = mock(VaadinSession.class);
		final WrappedSession httpSession = mock(WrappedSession.class);
		final McpMarkdownRenderView view = mock(McpMarkdownRenderView.class);
		when(session.getSession()).thenReturn(httpSession);
		when(session.getUIs()).thenReturn(List.of());
		doAnswer(invocation -> {
			invocation.getArgument(0, Command.class).execute();
			return null;
		}).when(session).accessSynchronously(any(Command.class));
		assertThat(ticket.attach(session, view)).isFalse();
		ticket.destroyPreviewSession();
		verify(view).release();
		verify(session).close();
		verify(httpSession).invalidate();
	}

	@Test
	void oversizedSourceIsRejectedBeforeBrowserLaunch() {
		assertThatIllegalStateException().isThrownBy(() -> PlaywrightFaithfulMarkdownRenderer.validateRequest(
				new RenderRequest(List.of(new Section("", "x".repeat(200_001), Variant.STANDARD)), List.of())))
				.withMessage("Der Inhalt ist für eine einzelne PNG-Vorschau zu groß.");
	}

	private static boolean allowed(final String relative, final String resourceType, final String method) {
		return PlaywrightFaithfulMarkdownRenderer.allowsRequest(BASE, "ticket", BASE + "/" + relative, resourceType, method);
	}
}
