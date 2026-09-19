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
		content.getUI().ifPresent(
				ui -> ui.beforeClientResponse(content, context -> positionAfterMarkdownRender(position.anchorKey(),
						position.anchorOffset(), position.scrollTop(), true)));
	}

	private void scrollToPendingAnchor() {
		if (pendingAnchorKey == null || !content.isAttached()) {
			return;
		}

		final String anchorKey = pendingAnchorKey;
		pendingAnchorKey = null;
		content.getUI().ifPresent(
				ui -> ui.beforeClientResponse(content, context -> positionAfterMarkdownRender(anchorKey, 0, 0, false)));
	}

	private void positionAfterMarkdownRender(final String anchorKey, final double anchorOffset,
			final double fallbackScrollTop, final boolean restoreFallback) {
		content.getElement().executeJs("""
			const viewport = this;
			const position = () => {
			   const anchor = Array.from(viewport.querySelectorAll('[data-tt-anchor]'))
			      .find(element => element.getAttribute('data-tt-anchor') === $0);
			   if (anchor) {
			      const viewportTop = viewport.getBoundingClientRect().top;
			      const currentOffset = anchor.getBoundingClientRect().top - viewportTop;
			      viewport.scrollTop += currentOffset - $1;
			   } else if ($3) {
			      viewport.scrollTop = $2;
			   }
			};

			const pendingViewers = Array.from(viewport.querySelectorAll('ws-markdown-viewer'))
			   .filter(viewer => !viewer.hasRendered);
			if (pendingViewers.length === 0) {
			   requestAnimationFrame(position);
			} else {
			   let remaining = pendingViewers.length;
			   pendingViewers.forEach(viewer => viewer.addEventListener('render-complete', () => {
			      remaining -= 1;
			      if (remaining === 0) {
			         requestAnimationFrame(position);
			      }
			   }, { once: true }));
			}
			""", anchorKey, anchorOffset, fallbackScrollTop, restoreFallback);
	}

	public record ViewportPosition(String anchorKey, double anchorOffset, double scrollTop) implements Serializable {
	}
}
