package de.westarps.topteacher.backend.export;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Component
public class HtmlRenderer {

	private final TemplateEngine templateEngine;

	public HtmlRenderer() {
		this.templateEngine = templateEngine();
	}

	public String render(final String templateName, final Map<String, ?> variables) {
		final Context context = new Context(Locale.GERMANY);
		variables.forEach(context::setVariable);
		return templateEngine.process(templateName, context);
	}

	public String renderModel(final String templateName, final Object model) {
		return render(templateName, Map.of("model", model));
	}

	private static TemplateEngine templateEngine() {
		final ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
		templateResolver.setPrefix("templates/");
		templateResolver.setTemplateMode(TemplateMode.HTML);
		templateResolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
		templateResolver.setCacheable(true);

		final TemplateEngine templateEngine = new SpringTemplateEngine();
		templateEngine.setTemplateResolver(templateResolver);
		return templateEngine;
	}
}
