package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilConflictAction;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilConflictResolution;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilCreateStatus;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilDraft;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilListScope;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilUpdateDuplicateAction;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilUpdateStatus;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;

@ExtendWith(MockitoExtension.class)
class PupilMcpToolsTests {

	@Mock
	private PupilRepository pupils;

	@Mock
	private PupilWriter writer;

	private PupilMcpTools tools;

	@BeforeEach
	void setUp() {
		tools = new PupilMcpTools(pupils, new PupilRosterConflictResolver(pupils),
				new PupilUpdateConflictResolver(pupils), writer);
	}

	@Test
	void listsOnlyActivePupilsByDefaultAndArchivedPupilsOnExplicitRequest() {
		final Pupil active = new Pupil(1, "Emma", "Schneider", Lifecycle.ACTIVE);
		final Pupil archived = new Pupil(2, "Emil", "Schneider", Lifecycle.INACTIVE);
		when(pupils.findAll()).thenReturn(List.of(active, archived));
		when(pupils.findLatestSchoolClassByPupilId()).thenReturn(Map.of(1, SchoolClass.CLS_Q1));

		assertThat(tools.listPupils("Schneider", null).pupils()).singleElement().satisfies(result -> {
			assertThat(result.id()).isEqualTo(1);
			assertThat(result.lifecycle()).isEqualTo("ACTIVE");
			assertThat(result.latestSchoolClass()).isEqualTo("CLS_Q1");
		});
		assertThat(tools.listPupils("Schneider", PupilListScope.ARCHIVED).pupils()).singleElement()
				.satisfies(result -> assertThat(result.id()).isEqualTo(2));
	}

	@Test
	void standaloneCreationOffersCreateOrSkipForActiveExactMatches() {
		when(pupils.findActiveByExactName("Emma", "Schneider"))
				.thenReturn(List.of(new Pupil(1, "Emma", "Schneider", Lifecycle.ACTIVE)));
		when(pupils.findLatestSchoolClassByPupilId()).thenReturn(Map.of(1, SchoolClass.CLS_Q2));

		final var result = tools.createPupils(contextWithoutElicitation(),
				List.of(new PupilDraft("row-1", "Emma", "Schneider")), null);

		assertThat(result.status()).isEqualTo(PupilCreateStatus.NEEDS_RESOLUTION);
		assertThat(result.conflicts()).singleElement()
				.satisfies(conflict -> assertThat(conflict.allowedActions()).containsExactly("CREATE", "SKIP"));
		verifyNoInteractions(writer);
	}

	@Test
	void archivedPupilsDoNotParticipateInStandaloneCreationConflicts() {
		when(pupils.findActiveByExactName("Emma", "Schneider")).thenReturn(List.of());
		when(writer.create(anyList())).thenReturn(List.of(new Pupil(3, "Emma", "Schneider", Lifecycle.ACTIVE)));
		when(pupils.findLatestSchoolClassByPupilId()).thenReturn(Map.of());

		final var result = tools.createPupils(contextWithoutElicitation(),
				List.of(new PupilDraft("row-1", "Emma", "Schneider")), null);

		assertThat(result.status()).isEqualTo(PupilCreateStatus.CREATED);
		assertThat(result.createdPupils()).singleElement().satisfies(created -> assertThat(created.id()).isEqualTo(3));
	}

	@Test
	void allSkippedStandaloneEntriesLeaveTheDatabaseUntouched() {
		when(pupils.findActiveByExactName("Emma", "Schneider"))
				.thenReturn(List.of(new Pupil(1, "Emma", "Schneider", Lifecycle.ACTIVE)));
		when(pupils.findLatestSchoolClassByPupilId()).thenReturn(Map.of());

		final var result = tools.createPupils(contextWithoutElicitation(),
				List.of(new PupilDraft("row-1", "Emma", "Schneider")),
				List.of(new PupilConflictResolution("row-1", PupilConflictAction.SKIP, null)));

		assertThat(result.status()).isEqualTo(PupilCreateStatus.CANCELLED);
		assertThat(result.skippedEntryKeys()).containsExactly("row-1");
		verifyNoInteractions(writer);
	}

	@Test
	void updatesOnlyTheSuppliedNamePart() {
		final Pupil current = new Pupil(17, "Tobias", "Wager", Lifecycle.ACTIVE);
		final Pupil updated = new Pupil(17, "Tobias", "Wagner", Lifecycle.ACTIVE);
		when(pupils.findById(17)).thenReturn(Optional.of(current));
		when(pupils.findActiveByExactName("Tobias", "Wagner")).thenReturn(List.of());
		when(writer.updateName(17, "Tobias", "Wagner", false)).thenReturn(updated);
		when(pupils.findLatestSchoolClassByPupilId()).thenReturn(Map.of(17, SchoolClass.CLS_Q1));

		final var result = tools.updatePupil(contextWithoutElicitation(), 17, null, "Wagner", null);

		assertThat(result.status()).isEqualTo(PupilUpdateStatus.UPDATED);
		assertThat(result.updatedPupils()).singleElement().satisfies(pupil -> {
			assertThat(pupil.name()).isEqualTo("Tobias");
			assertThat(pupil.surname()).isEqualTo("Wagner");
		});
	}

	@Test
	void renameCollisionRequiresConfirmationAndCanBeRetried() {
		final Pupil current = new Pupil(17, "Tobias", "Wager", Lifecycle.ACTIVE);
		final Pupil duplicate = new Pupil(18, "Tobias", "Wagner", Lifecycle.ACTIVE);
		final Pupil updated = new Pupil(17, "Tobias", "Wagner", Lifecycle.ACTIVE);
		when(pupils.findById(17)).thenReturn(Optional.of(current));
		when(pupils.findActiveByExactName("Tobias", "Wagner")).thenReturn(List.of(duplicate));
		when(pupils.findLatestSchoolClassByPupilId()).thenReturn(Map.of(18, SchoolClass.CLS_Q2));

		final var unresolved = tools.updatePupil(contextWithoutElicitation(), 17, null, "Wagner", null);

		assertThat(unresolved.status()).isEqualTo(PupilUpdateStatus.NEEDS_CONFIRMATION);
		assertThat(unresolved.conflicts()).singleElement().satisfies(conflict -> {
			assertThat(conflict.existingPupils()).singleElement()
					.satisfies(candidate -> assertThat(candidate.latestSchoolClass()).isEqualTo("CLS_Q2"));
			assertThat(conflict.allowedActions()).containsExactly("UPDATE_ANYWAY", "CANCEL");
		});
		verify(writer, never()).updateName(17, "Tobias", "Wagner", false);

		when(writer.updateName(17, "Tobias", "Wagner", true)).thenReturn(updated);
		final var confirmed = tools.updatePupil(contextWithoutElicitation(), 17, null, "Wagner",
				PupilUpdateDuplicateAction.UPDATE_ANYWAY);

		assertThat(confirmed.status()).isEqualTo(PupilUpdateStatus.UPDATED);
		verify(writer).updateName(17, "Tobias", "Wagner", true);
	}

	private static McpSyncRequestContext contextWithoutElicitation() {
		return mock(McpSyncRequestContext.class);
	}
}
