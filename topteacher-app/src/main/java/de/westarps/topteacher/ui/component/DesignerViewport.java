package de.westarps.topteacher.ui.component;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.function.SerializableRunnable;

public final class DesignerViewport implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private static final String ANCHOR_ATTRIBUTE = "data-tt-anchor";

	private final Component content;
	private String pendingAnchorKey;

	public DesignerViewport(final Component content) {
		this.content = Objects.requireNonNull(content, "content must not be null");
		content.addAttachListener(event -> scrollToPendingAnchor());
	}

	public static void mark(final Component component, final String anchorKey) {
		component.getElement().setAttribute(ANCHOR_ATTRIBUTE, anchorKey);
	}

	public void refreshPreservingPosition(final SerializableRunnable refreshAction) {
		Objects.requireNonNull(refreshAction, "refreshAction must not be null");
		if (!content.isAttached()) {
			refreshAction.run();
			return;
		}

		content.getElement().executeJs("""
			const viewport = this.getBoundingClientRect();
			const anchors = Array.from(this.querySelectorAll('[data-tt-anchor]'))
			   .map(element => ({ element, bounds: element.getBoundingClientRect() }))
			   .filter(candidate => candidate.element.getClientRects().length > 0
			      && candidate.bounds.bottom > viewport.top
			      && candidate.bounds.top < viewport.bottom);
			const above = anchors
			   .filter(candidate => candidate.bounds.top <= viewport.top)
			   .sort((left, right) => right.bounds.top - left.bounds.top);
			const below = anchors
			   .filter(candidate => candidate.bounds.top > viewport.top)
			   .sort((left, right) => left.bounds.top - right.bounds.top);
			const anchor = above[0] || below[0] || null;
			return {
			   anchorKey: anchor ? anchor.element.getAttribute('data-tt-anchor') : null,
			   anchorOffset: anchor ? anchor.bounds.top - viewport.top : 0,
			   scrollTop: this.scrollTop
			};
			""").then(ViewportPosition.class, position -> {
			refreshAction.run();
			restore(position);
		}, error -> refreshAction.run());
	}

	public void scrollTo(final String anchorKey) {
		pendingAnchorKey = Objects.requireNonNull(anchorKey, "anchorKey must not be null");
		scrollToPendingAnchor();
	}

	private void restore(final ViewportPosition position) {
		content.getUI().ifPresent(ui -> ui.beforeClientResponse(content,
				context -> stabilize(position.anchorKey(), position.anchorOffset(), position.scrollTop(), true)));
	}

	private void scrollToPendingAnchor() {
		if (pendingAnchorKey == null || !content.isAttached()) {
			return;
		}

		final String anchorKey = pendingAnchorKey;
		pendingAnchorKey = null;
		content.getUI().ifPresent(ui -> ui.beforeClientResponse(content, context -> stabilize(anchorKey, 0, 0, false)));
	}

	private void stabilize(final String anchorKey, final double anchorOffset, final double fallbackScrollTop,
			final boolean restoreFallback) {
		content.getElement().executeJs("""
			if (this.__ttViewportRestore) {
			   this.__ttViewportRestore.stop();
			}

			const viewport = this;
			let active = true;
			let frame = 0;
			const timeouts = [];
			const anchors = () => Array.from(viewport.querySelectorAll('[data-tt-anchor]'));
			const findAnchor = () => anchors()
			   .find(element => element.getAttribute('data-tt-anchor') === $0);
			const correct = () => {
			   if (!active) {
			      return;
			   }
			   const anchor = findAnchor();
			   if (anchor) {
			      const viewportTop = viewport.getBoundingClientRect().top;
			      const currentOffset = anchor.getBoundingClientRect().top - viewportTop;
			      const correction = currentOffset - $1;
			      if (Math.abs(correction) > 0.5) {
			         viewport.scrollTop += correction;
			      }
			   } else if ($3) {
			      viewport.scrollTop = $2;
			   }
			};
			const schedule = () => {
			   if (!active || frame) {
			      return;
			   }
			   frame = requestAnimationFrame(() => {
			      frame = 0;
			      correct();
			   });
			};
			const stop = () => {
			   if (!active) {
			      return;
			   }
			   active = false;
			   observer.disconnect();
			   if (frame) {
			      cancelAnimationFrame(frame);
			   }
			   timeouts.forEach(clearTimeout);
			   ['wheel', 'touchstart', 'pointerdown', 'keydown']
			      .forEach(type => viewport.removeEventListener(type, stop));
			   delete viewport.__ttViewportRestore;
			};
			const observer = new ResizeObserver(schedule);
			anchors().forEach(element => observer.observe(element));
			['wheel', 'touchstart', 'pointerdown', 'keydown']
			   .forEach(type => viewport.addEventListener(type, stop, { once: true }));
			viewport.__ttViewportRestore = { stop };

			schedule();
			[50, 150, 300, 600].forEach(delay => timeouts.push(setTimeout(schedule, delay)));
			timeouts.push(setTimeout(stop, 1000));
			""", anchorKey, anchorOffset, fallbackScrollTop, restoreFallback);
	}

	public record ViewportPosition(String anchorKey, double anchorOffset, double scrollTop) implements Serializable {
	}
}
