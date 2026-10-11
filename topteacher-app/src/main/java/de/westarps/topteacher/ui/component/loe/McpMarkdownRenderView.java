package de.westarps.topteacher.ui.component.loe;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.RenderRequest;
import de.westarps.topteacher.backend.render.FaithfulMarkdownRenderer.Section;
import de.westarps.vaadin.markdown.MarkdownViewer;

/** Internal ticket-only view; it never discovers or loads academic data. */
@Route(McpMarkdownRenderView.ROUTE)
@JsModule("./tt-criterion-markdown.tsx")
public final class McpMarkdownRenderView extends Div implements HasUrlParameter<String> {

	static final String ROUTE = "_internal/mcp-markdown-preview";
	static final String CONTENT_ID = "tt-mcp-markdown-render";
	static final int WIDTH = 960;
	private final McpMarkdownRenderTickets tickets;
	private final List<MaterialMarkdownViewer> materialViewers = new ArrayList<>();

	McpMarkdownRenderView(final McpMarkdownRenderTickets tickets) {
		this.tickets = tickets;
		setId(CONTENT_ID);
		setWidth(WIDTH + "px");
		getStyle().set("box-sizing", "border-box").set("padding", "24px").set("background", "white")
				.set("color", "#1f2328").set("overflow", "hidden");
		getElement().setAttribute("data-render-status", "unavailable");
	}

	@Override
	public void setParameter(final BeforeEvent event, final String parameter) {
		release();
		final var ticket = tickets.consume(parameter);
		if (ticket.isEmpty()) {
			final Div message = new Div();
			message.setText("Die PNG-Vorschau ist nicht verfügbar.");
			add(message);
			return;
		}
		if (!ticket.get().attach(VaadinSession.getCurrent(), this)) {
			return;
		}
		populate(ticket.get().request());
		getElement().setAttribute("data-render-status", "ready");
	}

	void populate(final RenderRequest request) {
		for (final Section section : request.sections()) {
			final Div block = new Div();
			block.getStyle().set("margin-bottom", "24px");
			if (!section.title().isBlank()) {
				final H3 heading = new H3(section.title());
				heading.getStyle().set("margin", "0 0 12px");
				block.add(heading);
			}
			final MarkdownViewer viewer = switch (section.variant()) {
				case STANDARD -> new MarkdownViewer(section.markdown());
				case MATERIAL -> materialViewer(section.markdown(), request);
				case REQUIREMENT -> requirementViewer(section.markdown());
			};
			viewer.setWidthFull();
			viewer.setCanvasColor("white");
			block.add(viewer);
			add(block);
		}
	}

	private MaterialMarkdownViewer materialViewer(final String markdown, final RenderRequest request) {
		final MaterialMarkdownViewer viewer = new MaterialMarkdownViewer(markdown, request.images());
		materialViewers.add(viewer);
		return viewer;
	}

	private static MarkdownViewer requirementViewer(final String markdown) {
		final MarkdownViewer viewer = new MarkdownViewer(markdown);
		viewer.setExtensionIds(List.of(CriterionMarkdownEditor.EXTENSION_ID));
		return viewer;
	}

	void release() {
		materialViewers.forEach(MaterialMarkdownViewer::releaseResources);
		materialViewers.clear();
		removeAll();
		getElement().setAttribute("data-render-status", "unavailable");
	}
}
