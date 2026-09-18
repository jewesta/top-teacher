package de.westarps.topteacher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import de.westarps.topteacher.mcp.CourseMcpTools;
import de.westarps.topteacher.mcp.ExamMcpTools;
import de.westarps.topteacher.mcp.LevelOfExpectationsMcpTools;
import de.westarps.topteacher.mcp.PupilResultMcpTools;

@SpringBootTest
class TopTeacherApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context.getBeansOfType(CourseMcpTools.class)).isEmpty();
		assertThat(context.getBeansOfType(ExamMcpTools.class)).isEmpty();
		assertThat(context.getBeansOfType(LevelOfExpectationsMcpTools.class)).isEmpty();
		assertThat(context.getBeansOfType(PupilResultMcpTools.class)).isEmpty();
	}
}
