package de.westarps.topteacher.mcp.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Requires bearer authentication for every enabled MCP HTTP exchange. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
@EnableConfigurationProperties(McpEndpointSecurityProperties.class)
class McpEndpointSecurityConfiguration {

	@Bean
	McpTokenLoader mcpTokenLoader(final McpEndpointSecurityProperties properties) {
		return new McpTokenLoader(properties);
	}

	@Bean
	FilterRegistrationBean<McpBearerTokenFilter> mcpBearerTokenFilter(final McpTokenLoader tokenLoader) {
		final FilterRegistrationBean<McpBearerTokenFilter> registration = new FilterRegistrationBean<>();
		registration.setFilter(new McpBearerTokenFilter(tokenLoader.load()));
		registration.setName("mcpBearerTokenFilter");
		registration.addUrlPatterns("/mcp", "/mcp/*");
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}
}
