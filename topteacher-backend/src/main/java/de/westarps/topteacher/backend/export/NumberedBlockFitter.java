package de.westarps.topteacher.backend.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.openhtmltopdf.newtable.TableBox;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.LineBox;

/** Fits each numbered block using the PDF engine's actual non-wrapping table layout. */
final class NumberedBlockFitter {

	private static final String BLOCK_ID = "data-tt-numbered-fit";
	private static final double BASE_POINTS = 9;
	private final List<Element> blocks = new ArrayList<>();
	private final List<Double> sizes = new ArrayList<>();

	NumberedBlockFitter(final Document document) {
		for (final Element block : document.select("table.ws-markdown-numbered")) {
			block.attr(BLOCK_ID, Integer.toString(blocks.size()));
			blocks.add(block);
			sizes.add(BASE_POINTS);
			setSize(blocks.size() - 1, BASE_POINTS);
		}
	}

	boolean shrinkOverflowingBlocks(final Box root) {
		boolean changed = false;
		if (root instanceof TableBox table && table.getElement() != null
				&& table.getElement().hasAttribute(BLOCK_ID)) {
			final int available = table.getContainingBlock().getContentWidth();
			/* Mixed inline fonts can paint beyond the table's intrinsic-width estimate. */
			final double required = Math.max(table.getWidth(), rightmostLine(table) - table.getAbsX());
			if (required > available) {
				final int id = Integer.parseInt(table.getElement().getAttribute(BLOCK_ID));
				final var firstRow = table.getFirstRow();
				final int gutter = firstRow == null || firstRow.getChildCount() == 0 ? 0 : firstRow.getChild(0).getWidth();
				if (available <= gutter) {
					throw new IllegalStateException("Für den nummerierten Text ist kein Platz neben den Zeilennummern verfügbar.");
				}
				/* The gutter has fixed physical padding; only font-dependent text width scales. */
				final double previous = sizes.get(id);
				final double fitted = Math.floor(previous * (available - gutter - 1d)
						/ (required - gutter) * 1000d) / 1000d;
				final double points = Math.max(0.001, Math.min(previous - 0.001, fitted));
				if (points >= previous) {
					throw new IllegalStateException("Der nummerierte Text konnte nicht auf die verfügbare Breite angepasst werden.");
				}
				setSize(id, points);
				changed = true;
			}
		}
		for (final Box child : root.getChildren()) {
			changed |= shrinkOverflowingBlocks(child);
		}
		return changed;
	}

	private static int rightmostLine(final Box box) {
		int right = box instanceof LineBox ? box.getAbsX() + box.getWidth() : Integer.MIN_VALUE;
		for (final Box child : box.getChildren()) {
			right = Math.max(right, rightmostLine(child));
		}
		return right;
	}

	private void setSize(final int id, final double points) {
		sizes.set(id, points);
		blocks.get(id).attr("style", String.format(Locale.ROOT, "font-size: %.3fpt", points));
	}
}
