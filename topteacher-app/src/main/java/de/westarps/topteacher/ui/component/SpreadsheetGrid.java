package de.westarps.topteacher.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.function.ValueProvider;

/**
 * Grid optimized for spreadsheet-like result tables with narrow data columns and
 * tilted column headers.
 *
 * @param <T> item type
 */
public class SpreadsheetGrid<T> extends Grid<T> {

	private static final String CLASS_NAME = "tt-spreadsheet-grid";
	private static final String HEADER_CLASS_NAME = "tt-spreadsheet-header";
	private static final String HEADER_LABEL_CLASS_NAME = "tt-spreadsheet-header-label";
	private static final String TILTED_HEADER_PART_NAME = "tt-spreadsheet-tilted-header-cell";
	private static final int DEFAULT_HEADER_LABEL_MAX_LENGTH = 24;
	private static final String HEADER_LABEL_SUFFIX = "...";

	private int headerLabelMaxLength = DEFAULT_HEADER_LABEL_MAX_LENGTH;

	public SpreadsheetGrid() {
		super();
		configure();
	}

	public SpreadsheetGrid(final Class<T> beanType) {
		super(beanType);
		configure();
	}

	public SpreadsheetGrid(final Class<T> beanType, final boolean autoCreateColumns) {
		super(beanType, autoCreateColumns);
		configure();
	}

	public Column<T> addSpreadsheetColumn(final ValueProvider<T, ?> valueProvider, final String headerText) {
		return configureSpreadsheetColumn(addColumn(valueProvider), headerText);
	}

	public Column<T> configureSpreadsheetColumn(final Column<T> column, final String headerText) {
		return column.setHeader(tiltedHeader(headerText)).setHeaderPartName(TILTED_HEADER_PART_NAME);
	}

	public Column<T> addSpacerColumn(final String width) {
		return addColumn(item -> "").setHeader("").setHeaderPartName(TILTED_HEADER_PART_NAME).setWidth(width)
				.setFlexGrow(0);
	}

	public void setHeaderLabelMaxLength(final int headerLabelMaxLength) {
		if (headerLabelMaxLength <= HEADER_LABEL_SUFFIX.length()) {
			throw new IllegalArgumentException("Header label max length must leave room for the suffix.");
		}
		this.headerLabelMaxLength = headerLabelMaxLength;
	}

	private void configure() {
		addClassName(CLASS_NAME);
	}

	private Component tiltedHeader(final String text) {
		final Span label = new Span(abbreviatedHeaderText(text));
		label.addClassName(HEADER_LABEL_CLASS_NAME);

		final Div header = new Div(label);
		header.addClassName(HEADER_CLASS_NAME);
		header.getElement().setAttribute("title", text);
		return header;
	}

	private String abbreviatedHeaderText(final String text) {
		if (text.length() <= headerLabelMaxLength) {
			return text;
		}
		return text.substring(0, headerLabelMaxLength - HEADER_LABEL_SUFFIX.length()) + HEADER_LABEL_SUFFIX;
	}
}
