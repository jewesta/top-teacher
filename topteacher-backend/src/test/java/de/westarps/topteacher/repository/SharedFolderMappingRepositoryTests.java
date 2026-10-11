package de.westarps.topteacher.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.westarps.topteacher.backend.repo.SharedFolderMappingRepository;
import de.westarps.topteacher.model.SharedFolderMapping;

@SpringBootTest
class SharedFolderMappingRepositoryTests {

	@Autowired
	private SharedFolderMappingRepository repository;

	@AfterEach
	void removeMappings() {
		repository.replaceAll(List.of());
	}

	@Test
	void replacesAllMappingsAndPreservesTheirOrder() {
		repository.replaceAll(List.of(new SharedFolderMapping("/Volumes/One", "/mnt/one", 7),
				new SharedFolderMapping("/Volumes/Two", "/mnt/two", 4)));

		assertThat(repository.findAll()).containsExactly(new SharedFolderMapping("/Volumes/One", "/mnt/one", 0),
				new SharedFolderMapping("/Volumes/Two", "/mnt/two", 1));
	}

	@Test
	void rejectsDuplicateClientRootsBeforeReplacingStoredMappings() {
		repository.replaceAll(List.of(new SharedFolderMapping("/Volumes/Existing", "/mnt/existing", 0)));

		assertThatThrownBy(() -> repository.replaceAll(List.of(
				new SharedFolderMapping("/Volumes/Duplicate", "/mnt/one", 0),
				new SharedFolderMapping("/Volumes/Duplicate", "/mnt/two", 1))))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unique");
		assertThat(repository.findAll())
				.containsExactly(new SharedFolderMapping("/Volumes/Existing", "/mnt/existing", 0));
	}
}
