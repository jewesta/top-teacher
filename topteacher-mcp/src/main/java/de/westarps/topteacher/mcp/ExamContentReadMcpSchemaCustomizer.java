package de.westarps.topteacher.mcp;

import java.util.List;
import java.util.Map;

import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.ExamFooterView;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.MaterialContentView;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.TaskContentView;
import de.westarps.topteacher.mcp.ExamContentReadMcpSchema.TaskRequirementsContentView;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/** Keeps focused output schemas when native images require a CallToolResult return type. */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public final class ExamContentReadMcpSchemaCustomizer implements BeanPostProcessor {

	private static final Map<String, Class<?>> OUTPUT_TYPES = Map.of("get_task", TaskContentView.class,
			"get_material", MaterialContentView.class, "get_exam_footer", ExamFooterView.class,
			"get_task_requirements", TaskRequirementsContentView.class);

	@Override
	public Object postProcessAfterInitialization(final Object bean, final String beanName) {
		if (bean instanceof List<?> values && values.stream().anyMatch(value -> value instanceof SyncToolSpecification spec
				&& OUTPUT_TYPES.containsKey(spec.tool().name()))) {
			return values.stream().map(value -> value instanceof SyncToolSpecification spec ? customize(spec) : value).toList();
		}
		return bean;
	}

	public static List<SyncToolSpecification> customize(final List<SyncToolSpecification> specifications) {
		return specifications.stream().map(ExamContentReadMcpSchemaCustomizer::customize).toList();
	}

	private static SyncToolSpecification customize(final SyncToolSpecification specification) {
		final Tool original = specification.tool();
		final Class<?> outputType = OUTPUT_TYPES.get(original.name());
		if (outputType == null) {
			return specification;
		}
		final Tool tool = Tool.builder(original.name(), original.inputSchema()).title(original.title())
				.description(original.description()).annotations(original.annotations()).meta(original.meta()).icons(original.icons())
				.outputSchema(McpJsonDefaults.getMapper(), McpJsonSchemaGenerator.generateFromType(outputType)).build();
		return SyncToolSpecification.builder().tool(tool).callHandler(specification.callHandler()).build();
	}
}
