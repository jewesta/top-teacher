package de.westarps.topteacher.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.mcp.PupilMcpSchema.ExistingPupilView;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilUpdateConflictView;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilUpdateDuplicateAction;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

/**
 * Resolves exact-name collisions caused by a proposed pupil rename without
 * writing to the database.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class PupilUpdateConflictResolver {

	private final PupilRepository pupils;

	public PupilUpdateConflictResolver(final PupilRepository pupils) {
		this.pupils = Objects.requireNonNull(pupils, "pupils");
	}

	public ResolutionOutcome resolve(final McpSyncRequestContext context, final Pupil current, final String name,
			final String surname, final PupilUpdateDuplicateAction suppliedAction) {
		final boolean nameChanged = !current.name().equals(name) || !current.surname().equals(surname);
		final List<Pupil> exactMatches = current.lifecycle() == Lifecycle.ACTIVE && nameChanged
				? pupils.findActiveByExactName(name, surname).stream()
						.filter(candidate -> !candidate.id().equals(current.id())).toList()
				: List.of();
		if (exactMatches.isEmpty()) {
			if (suppliedAction != null) {
				throw new IllegalArgumentException("duplicateNameAction does not refer to a current conflict");
			}
			return ResolutionOutcome.proceed(false);
		}

		final PupilUpdateConflictView conflict = conflict(current, name, surname, exactMatches,
				pupils.findLatestSchoolClassByPupilId());
		PupilUpdateDuplicateAction action = suppliedAction;
		if (action == null && context != null && context.elicitEnabled()) {
			final ElicitResult result = context
					.elicit(ElicitFormRequest.builder(elicitationMessage(conflict), schema()).build());
			if (result.action() != ElicitResult.Action.ACCEPT) {
				return ResolutionOutcome.cancelled(conflict);
			}
			final Object actionValue = result.content() == null ? null : result.content().get("action");
			if (actionValue == null) {
				throw new IllegalArgumentException("Elicitation response did not contain action");
			}
			try {
				action = PupilUpdateDuplicateAction.valueOf(actionValue.toString().toUpperCase());
			} catch (final IllegalArgumentException invalidAction) {
				throw new IllegalArgumentException("Unsupported pupil update action: " + actionValue, invalidAction);
			}
		}
		if (action == null) {
			return ResolutionOutcome.needsConfirmation(conflict);
		}
		return switch (action) {
		case UPDATE_ANYWAY -> ResolutionOutcome.proceed(true);
		case CANCEL -> ResolutionOutcome.cancelled(conflict);
		};
	}

	private static Map<String, Object> schema() {
		final Map<String, Object> action = new LinkedHashMap<>();
		action.put("type", "string");
		action.put("title", "Entscheidung");
		action.put("description", "UPDATE_ANYWAY = trotz Namensgleichheit umbenennen, CANCEL = nicht ändern");
		action.put("enum",
				List.of(PupilUpdateDuplicateAction.UPDATE_ANYWAY.name(), PupilUpdateDuplicateAction.CANCEL.name()));
		final Map<String, Object> requestedSchema = new LinkedHashMap<>();
		requestedSchema.put("type", "object");
		requestedSchema.put("properties", Map.of("action", action));
		requestedSchema.put("required", List.of("action"));
		requestedSchema.put("additionalProperties", false);
		return requestedSchema;
	}

	private static String elicitationMessage(final PupilUpdateConflictView conflict) {
		final StringBuilder message = new StringBuilder("Die Umbenennung von ").append(conflict.currentName())
				.append(' ').append(conflict.currentSurname()).append(" in ").append(conflict.requestedName())
				.append(' ').append(conflict.requestedSurname())
				.append(" erzeugt eine Namensgleichheit. Vorhandene aktive Treffer: ");
		for (int index = 0; index < conflict.existingPupils().size(); index++) {
			if (index > 0) {
				message.append("; ");
			}
			final ExistingPupilView candidate = conflict.existingPupils().get(index);
			message.append("ID ").append(candidate.id());
			if (!candidate.latestSchoolClassDisplayName().isEmpty()) {
				message.append(" (zuletzt ").append(candidate.latestSchoolClassDisplayName()).append(')');
			}
		}
		return message.append(". Wähle UPDATE_ANYWAY oder CANCEL.").toString();
	}

	private static PupilUpdateConflictView conflict(final Pupil current, final String name, final String surname,
			final List<Pupil> exactMatches, final Map<Integer, SchoolClass> latestSchoolClasses) {
		final List<ExistingPupilView> existingPupils = exactMatches.stream().map(existing -> {
			final SchoolClass latestSchoolClass = latestSchoolClasses.get(existing.id());
			return new ExistingPupilView(existing.id(), existing.name(), existing.surname(),
					existing.lifecycle().name(), latestSchoolClass == null ? "" : latestSchoolClass.name(),
					latestSchoolClass == null ? "" : latestSchoolClass.getDisplayName());
		}).toList();
		return new PupilUpdateConflictView(current.id(), current.name(), current.surname(), name, surname,
				existingPupils,
				List.of(PupilUpdateDuplicateAction.UPDATE_ANYWAY.name(), PupilUpdateDuplicateAction.CANCEL.name()));
	}

	public record ResolutionOutcome(boolean cancelled, boolean duplicateNameConfirmed,
			List<PupilUpdateConflictView> conflicts) {

		public ResolutionOutcome {
			conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts"));
		}

		private static ResolutionOutcome proceed(final boolean duplicateNameConfirmed) {
			return new ResolutionOutcome(false, duplicateNameConfirmed, List.of());
		}

		private static ResolutionOutcome needsConfirmation(final PupilUpdateConflictView conflict) {
			return new ResolutionOutcome(false, false, List.of(conflict));
		}

		private static ResolutionOutcome cancelled(final PupilUpdateConflictView conflict) {
			return new ResolutionOutcome(true, false, List.of(conflict));
		}
	}
}
