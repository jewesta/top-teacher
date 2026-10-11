package de.westarps.topteacher.ui.component.loe;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;

import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.RenderRequest;

/** One-use handoff of an already authorized snapshot to an isolated preview UI. */
@Component
final class McpMarkdownRenderTickets {

	private final Map<String, Ticket> pending = new HashMap<>();
	private final Clock clock;

	McpMarkdownRenderTickets() {
		this(Clock.systemUTC());
	}

	McpMarkdownRenderTickets(final Clock clock) {
		this.clock = clock;
	}

	synchronized Ticket issue(final RenderRequest request, final Duration lifetime) {
		pending.values().removeIf(ticket -> !ticket.expiresAt.isAfter(clock.instant()));
		if (pending.size() >= 3) {
			throw new IllegalStateException("Die PNG-Vorschau ist gerade ausgelastet. Versuche es erneut.");
		}
		final Ticket ticket = new Ticket(UUID.randomUUID().toString(), request, clock.instant().plus(lifetime));
		pending.put(ticket.id, ticket);
		return ticket;
	}

	synchronized Optional<Ticket> consume(final String id) {
		final Ticket ticket = pending.remove(id);
		return ticket == null || !ticket.expiresAt.isAfter(clock.instant()) ? Optional.empty() : Optional.of(ticket);
	}

	synchronized void revoke(final Ticket ticket) {
		pending.remove(ticket.id);
	}

	static final class Ticket {
		private final String id;
		private final RenderRequest request;
		private final Instant expiresAt;
		private VaadinSession session;
		private McpMarkdownRenderView view;
		private boolean disposed;

		private Ticket(final String id, final RenderRequest request, final Instant expiresAt) {
			this.id = id;
			this.request = request;
			this.expiresAt = expiresAt;
		}

		String id() {
			return id;
		}

		RenderRequest request() {
			return request;
		}

		boolean attach(final VaadinSession session, final McpMarkdownRenderView view) {
			synchronized (this) {
				if (!disposed) {
					this.session = session;
					this.view = view;
					return true;
				}
			}
			destroySession(session, view);
			return false;
		}

		void destroyPreviewSession() {
			final VaadinSession previewSession;
			final McpMarkdownRenderView previewView;
			synchronized (this) {
				disposed = true;
				previewSession = session;
				previewView = view;
				session = null;
				view = null;
			}
			destroySession(previewSession, previewView);
		}

		private static void destroySession(final VaadinSession previewSession,
				final McpMarkdownRenderView previewView) {
			if (previewSession == null) {
				return;
			}
			try {
				previewSession.accessSynchronously(() -> {
					if (previewView != null) {
						previewView.release();
					}
					previewSession.getUIs().forEach(UI::close);
					previewSession.close();
				});
			} finally {
				try {
					previewSession.getSession().invalidate();
				} catch (final IllegalStateException ignored) {
					/* Session expiry or context teardown may have invalidated it already. */
				}
			}
		}
	}
}
