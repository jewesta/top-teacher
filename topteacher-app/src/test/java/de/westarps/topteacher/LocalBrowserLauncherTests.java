package de.westarps.topteacher;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class LocalBrowserLauncherTests {

	@Test
	void opensConfiguredLocalApplicationUriWhenEnabled() {
		final List<URI> openedUris = new ArrayList<>();
		final LocalBrowserLauncher launcher = new LocalBrowserLauncher(true, 8081, "/top-teacher", openedUris::add);

		launcher.openBrowser();

		assertThat(openedUris).containsExactly(URI.create("http://localhost:8081/top-teacher/"));
	}

	@Test
	void doesNotOpenBrowserWhenDisabled() {
		final List<URI> openedUris = new ArrayList<>();
		final LocalBrowserLauncher launcher = new LocalBrowserLauncher(false, 8081, "/top-teacher", openedUris::add);

		launcher.openBrowser();

		assertThat(openedUris).isEmpty();
	}

	@Test
	void normalizesBlankRootAndRelativeContextPaths() {
		assertThat(LocalBrowserLauncher.localApplicationUri(8081, null))
				.isEqualTo(URI.create("http://localhost:8081/"));
		assertThat(LocalBrowserLauncher.localApplicationUri(8081, "/")).isEqualTo(URI.create("http://localhost:8081/"));
		assertThat(LocalBrowserLauncher.localApplicationUri(8081, "top-teacher/"))
				.isEqualTo(URI.create("http://localhost:8081/top-teacher/"));
	}
}
