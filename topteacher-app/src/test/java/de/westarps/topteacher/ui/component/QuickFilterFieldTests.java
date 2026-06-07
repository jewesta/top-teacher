package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.data.value.ValueChangeMode;

class QuickFilterFieldTests {

	@Test
	void fixesSharedQuickFilterConfiguration() {
		final QuickFilterField field = new QuickFilterField();

		assertThat(field.getClassNames()).contains("tt-quick-filter");
		assertThat(field.getLabel()).isEqualTo("Schnellfilter");
		assertThat(field.getPlaceholder()).isEqualTo("Suchbegriff");
		assertThat(field.isClearButtonVisible()).isTrue();
		assertThat(field.getPrefixComponent()).isNotNull();
		assertThat(field.getValueChangeMode()).isEqualTo(ValueChangeMode.EAGER);
	}
}
