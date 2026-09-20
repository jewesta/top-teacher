package de.westarps.vaadin.badge;

import java.util.Objects;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.shared.HasTooltip;
import com.vaadin.flow.dom.Style;

/**
 * A span with Vaadin's Lumo badge theme applied.
 */
@SuppressWarnings("serial")
public class Badge extends Span implements HasTooltip {

	public static final String TICK_MARK = "✓";

	public enum BadgeVariant {
		NORMAL(null),
		SUCCESS("success"),
		ERROR("error"),
		WARNING("warning"),
		CONTRAST("contrast");

		private final String variant;

		BadgeVariant(final String variant) {
			this.variant = variant;
		}

		public String getVariant() {
			return variant;
		}
	}

	private String text = "";
	private Icon icon;
	private boolean iconFirst;
	private String html;

	public Badge() {
	}

	public Badge(final String text) {
		setText(text);
	}

	/**
	 * Sets plain text content and removes any HTML content.
	 *
	 * @param text
	 *            plain text, or {@code null} to show no text
	 */
	@Override
	public void setText(final String text) {
		this.text = text;
		html = null;
		buildContent();
	}

	public void setVariant(final BadgeVariant variant) {
		for (final BadgeVariant currentVariant : BadgeVariant.values()) {
			if (currentVariant.getVariant() != null) {
				removeThemeName(currentVariant.getVariant());
			}
		}
		final BadgeVariant requiredVariant = Objects.requireNonNull(variant);
		if (requiredVariant != BadgeVariant.NORMAL) {
			addThemeName(requiredVariant.getVariant());
		}
	}

	public void setPrimary(final boolean primary) {
		setThemeName("primary", primary);
	}

	public void setPill(final boolean pill) {
		setThemeName("pill", pill);
	}

	public void setSmall(final boolean small) {
		setThemeName("small", small);
	}

	public void setPadding(final boolean padding) {
		setThemeName("badge", padding);
	}

	public void setIcon(final Icon icon) {
		setIcon(icon, true);
	}

	public void setIcon(final Icon icon, final boolean first) {
		this.icon = icon;
		iconFirst = first;
		buildContent();
	}

	/**
	 * Sets HTML content while preserving the current icon.
	 * <p>
	 * The supplied HTML is not sanitized and must therefore come from a trusted
	 * source.
	 *
	 * @param html
	 *            trusted HTML, or {@code null} to show no HTML content
	 */
	public void setHtml(final String html) {
		this.html = html;
		text = null;
		buildContent();
	}

	private void buildContent() {
		getElement().removeAllChildren();
		shrinkPadding(icon != null);
		if (icon != null && iconFirst) {
			addContent(icon);
		}
		if (html != null && !html.isBlank()) {
			addContent(new Html(html));
		} else if (text != null && !text.isBlank()) {
			addContent(new Span(text));
		}
		if (icon != null && !iconFirst) {
			addContent(icon);
		}
	}

	private void shrinkPadding(final boolean shrinkPadding) {
		final Style style = getStyle();
		if (shrinkPadding) {
			/*
			 * Circular icons would make a badge unnecessarily wide with the normal
			 * text padding.
			 */
			style.set("padding-left", "0.4rem");
			style.set("padding-right", "0.4rem");
		} else {
			style.remove("padding-left");
			style.remove("padding-right");
		}
	}

	private void setThemeName(final String name, final boolean enabled) {
		if (enabled) {
			addThemeName(name);
		} else {
			removeThemeName(name);
		}
	}

	private void addThemeName(final String name) {
		getElement().getThemeList().add(name);
	}

	private void removeThemeName(final String name) {
		getElement().getThemeList().remove(name);
	}

	private void addContent(final Component component) {
		if (!(component instanceof Text)) {
			component.getElement().setAttribute("slot", "content");
		}
		getElement().appendChild(component.getElement());
	}
}
