package de.westarps.topteacher.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.mcp.CourseRosterMcpTools.ExistingPupilView;
import de.westarps.topteacher.mcp.CourseRosterMcpTools.PupilConflictAction;
import de.westarps.topteacher.mcp.CourseRosterMcpTools.PupilConflictResolution;
import de.westarps.topteacher.mcp.CourseRosterMcpTools.PupilConflictView;
import de.westarps.topteacher.mcp.CourseRosterMcpTools.PupilDraft;
import de.westarps.topteacher.mcp.CourseRosterWriter.ResolvedPupil;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

/**
 * Detects pupil identity conflicts and resolves them through supplied decisions
 * or optional MCP elicitation without writing to the database.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class PupilRosterConflictResolver {

	private final PupilRepository pupils;

	public PupilRosterConflictResolver(final PupilRepository pupils) {
		this.pupils = Objects.requireNonNull(pupils, "pupils");
	}

	public ResolutionOutcome resolve(final McpSyncRequestContext context, final List<PupilDraft> roster,
			final List<PupilConflictResolution> resolutions) {
		final List<ConflictPlan> conflicts = findConflicts(roster);
		final Map<String, PupilConflictResolution> resolutionsByEntryKey = resolutions(resolutions, conflicts);
		final List<ResolvedPupil> resolvedPupils = new ArrayList<>();
		final List<String> skippedEntryKeys = new ArrayList<>();
		boolean unresolved = false;

		for (final PupilDraft pupilDraft : roster) {
			final ConflictPlan conflict = conflictFor(conflicts, pupilDraft.entryKey());
			if (conflict == null) {
				resolvedPupils.add(new ResolvedPupil(null, pupilDraft.name(), pupilDraft.surname()));
				continue;
			}

			PupilConflictResolution resolution = resolutionsByEntryKey.get(pupilDraft.entryKey());
			if (resolution == null && context != null && context.elicitEnabled()) {
				final ElicitationDecision decision = elicitResolution(context, conflict);
				if (decision.cancelled()) {
					return ResolutionOutcome.cancelled(conflict.view());
				}
				resolution = decision.resolution();
			}
			if (resolution == null) {
				unresolved = true;
				continue;
			}

			applyResolution(conflict, resolution, resolvedPupils, skippedEntryKeys);
		}

		if (unresolved) {
			return ResolutionOutcome.needsResolution(conflicts.stream().map(ConflictPlan::view).toList());
		}
		return ResolutionOutcome.resolved(resolvedPupils, skippedEntryKeys);
	}

	private List<ConflictPlan> findConflicts(final List<PupilDraft> roster) {
		final Map<NameKey, Long> rosterNameCounts = roster.stream().collect(Collectors.groupingBy(
				pupil -> new NameKey(pupil.name(), pupil.surname()), LinkedHashMap::new, Collectors.counting()));
		final Map<Integer, SchoolClass> latestSchoolClasses = pupils.findLatestSchoolClassByPupilId();
		final List<ConflictPlan> conflicts = new ArrayList<>();
		for (final PupilDraft pupilDraft : roster) {
			final NameKey name = new NameKey(pupilDraft.name(), pupilDraft.surname());
			final List<Pupil> exactMatches = pupils.findByExactName(pupilDraft.name(), pupilDraft.surname());
			final boolean repeatedInRoster = rosterNameCounts.get(name) > 1;
			if (!exactMatches.isEmpty() || repeatedInRoster) {
				conflicts.add(new ConflictPlan(pupilDraft, exactMatches,
						conflictView(pupilDraft, exactMatches, repeatedInRoster, latestSchoolClasses)));
			}
		}
		return conflicts;
	}

	private static Map<String, PupilConflictResolution> resolutions(final List<PupilConflictResolution> resolutions,
			final List<ConflictPlan> conflicts) {
		final Set<String> conflictKeys = conflicts.stream().map(conflict -> conflict.pupil().entryKey())
				.collect(Collectors.toSet());
		final Map<String, PupilConflictResolution> byEntryKey = new LinkedHashMap<>();
		for (final PupilConflictResolution resolution : resolutions == null ? List.<PupilConflictResolution> of()
				: resolutions) {
			Objects.requireNonNull(resolution, "resolution must not be null");
			if (!conflictKeys.contains(resolution.entryKey())) {
				throw new IllegalArgumentException(
						"resolution does not refer to a current conflict: " + resolution.entryKey());
			}
			if (byEntryKey.put(resolution.entryKey(), resolution) != null) {
				throw new IllegalArgumentException(
						"only one resolution is allowed for entryKey: " + resolution.entryKey());
			}
		}
		return byEntryKey;
	}

	private static ConflictPlan conflictFor(final List<ConflictPlan> conflicts, final String entryKey) {
		return conflicts.stream().filter(conflict -> conflict.pupil().entryKey().equals(entryKey)).findFirst()
				.orElse(null);
	}

	private static void applyResolution(final ConflictPlan conflict, final PupilConflictResolution resolution,
			final List<ResolvedPupil> resolvedPupils, final List<String> skippedEntryKeys) {
		final PupilDraft pupil = conflict.pupil();
		switch (resolution.action()) {
		case CREATE -> {
			requireNoPupilId(resolution);
			resolvedPupils.add(new ResolvedPupil(null, pupil.name(), pupil.surname()));
		}
		case REUSE -> {
			if (resolution.pupilId() == null) {
				throw new IllegalArgumentException("REUSE requires pupilId for entryKey: " + resolution.entryKey());
			}
			final Pupil existing = conflict.exactMatches().stream()
					.filter(candidate -> candidate.id().equals(resolution.pupilId())).findFirst()
					.orElseThrow(() -> new IllegalArgumentException("pupilId " + resolution.pupilId()
							+ " is not an exact-name candidate for entryKey: " + resolution.entryKey()));
			resolvedPupils.add(new ResolvedPupil(existing.id(), pupil.name(), pupil.surname()));
		}
		case SKIP -> {
			requireNoPupilId(resolution);
			skippedEntryKeys.add(pupil.entryKey());
		}
		}
	}

	private static void requireNoPupilId(final PupilConflictResolution resolution) {
		if (resolution.pupilId() != null) {
			throw new IllegalArgumentException(
					resolution.action() + " must not include pupilId for entryKey: " + resolution.entryKey());
		}
	}

	private static ElicitationDecision elicitResolution(final McpSyncRequestContext context,
			final ConflictPlan conflict) {
		final Map<String, Object> actionSchema = new LinkedHashMap<>();
		actionSchema.put("type", "string");
		actionSchema.put("title", "Entscheidung");
		actionSchema.put("description", "REUSE = vorhandene Person verwenden, CREATE = neu anlegen, SKIP = auslassen");
		actionSchema.put("enum", conflict.view().allowedActions());

		final Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("action", actionSchema);
		if (!conflict.exactMatches().isEmpty()) {
			properties.put("pupilId", Map.of("type", "integer", "title", "Vorhandene Schüler:innen-ID", "description",
					"Nur bei REUSE erforderlich; wähle eine der im Hinweis genannten IDs."));
		}
		final Map<String, Object> requestedSchema = new LinkedHashMap<>();
		requestedSchema.put("type", "object");
		requestedSchema.put("properties", properties);
		requestedSchema.put("required", List.of("action"));
		requestedSchema.put("additionalProperties", false);

		final ElicitResult result = context
				.elicit(ElicitFormRequest.builder(elicitationMessage(conflict), requestedSchema).build());
		if (result.action() != ElicitResult.Action.ACCEPT) {
			return new ElicitationDecision(true, null);
		}
		final Map<String, Object> content = result.content() == null ? Map.of() : result.content();
		final Object actionValue = content.get("action");
		if (actionValue == null) {
			throw new IllegalArgumentException("Elicitation response did not contain action");
		}
		final PupilConflictAction action;
		try {
			action = PupilConflictAction.valueOf(actionValue.toString().toUpperCase());
		} catch (final IllegalArgumentException invalidAction) {
			throw new IllegalArgumentException("Unsupported pupil conflict action: " + actionValue, invalidAction);
		}
		final Integer pupilId = integer(content.get("pupilId"));
		return new ElicitationDecision(false,
				new PupilConflictResolution(conflict.pupil().entryKey(), action, pupilId));
	}

	private static String elicitationMessage(final ConflictPlan conflict) {
		final PupilDraft pupil = conflict.pupil();
		final StringBuilder message = new StringBuilder("Für ").append(pupil.name()).append(' ').append(pupil.surname())
				.append(" ist eine Entscheidung nötig.");
		if (!conflict.exactMatches().isEmpty()) {
			message.append(" Vorhandene exakte Treffer: ");
			for (int index = 0; index < conflict.view().existingPupils().size(); index++) {
				if (index > 0) {
					message.append("; ");
				}
				final ExistingPupilView candidate = conflict.view().existingPupils().get(index);
				message.append("ID ").append(candidate.id()).append(" (").append(candidate.lifecycle());
				if (!candidate.latestSchoolClassDisplayName().isEmpty()) {
					message.append(", zuletzt ").append(candidate.latestSchoolClassDisplayName());
				}
				message.append(')');
			}
			message.append('.');
		}
		if (conflict.view().repeatedInRoster()) {
			message.append(" Der Name kommt außerdem mehrfach in der Importliste vor.");
		}
		message.append(" Wähle ").append(String.join(" / ", conflict.view().allowedActions())).append('.');
		return message.toString();
	}

	private static Integer integer(final Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof final Number number) {
			return number.intValue();
		}
		try {
			return Integer.valueOf(value.toString());
		} catch (final NumberFormatException invalidNumber) {
			throw new IllegalArgumentException("pupilId must be an integer", invalidNumber);
		}
	}

	private static PupilConflictView conflictView(final PupilDraft pupil, final List<Pupil> exactMatches,
			final boolean repeatedInRoster, final Map<Integer, SchoolClass> latestSchoolClasses) {
		final List<ExistingPupilView> existingPupils = exactMatches.stream().map(existing -> {
			final SchoolClass latestSchoolClass = latestSchoolClasses.get(existing.id());
			return new ExistingPupilView(existing.id(), existing.name(), existing.surname(),
					existing.lifecycle().name(), latestSchoolClass == null ? "" : latestSchoolClass.name(),
					latestSchoolClass == null ? "" : latestSchoolClass.getDisplayName());
		}).toList();
		final List<String> allowedActions = exactMatches.isEmpty()
				? List.of(PupilConflictAction.CREATE.name(), PupilConflictAction.SKIP.name())
				: List.of(PupilConflictAction.REUSE.name(), PupilConflictAction.CREATE.name(),
						PupilConflictAction.SKIP.name());
		return new PupilConflictView(pupil.entryKey(), pupil.name(), pupil.surname(), !exactMatches.isEmpty(),
				repeatedInRoster, existingPupils, allowedActions);
	}

	public record ResolutionOutcome(boolean cancelled, List<ResolvedPupil> resolvedPupils,
			List<String> skippedEntryKeys, List<PupilConflictView> conflicts) {

		public ResolutionOutcome {
			resolvedPupils = List.copyOf(Objects.requireNonNull(resolvedPupils, "resolvedPupils"));
			skippedEntryKeys = List.copyOf(Objects.requireNonNull(skippedEntryKeys, "skippedEntryKeys"));
			conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts"));
		}

		private static ResolutionOutcome resolved(final List<ResolvedPupil> pupils,
				final List<String> skippedEntryKeys) {
			return new ResolutionOutcome(false, pupils, skippedEntryKeys, List.of());
		}

		private static ResolutionOutcome needsResolution(final List<PupilConflictView> conflicts) {
			return new ResolutionOutcome(false, List.of(), List.of(), conflicts);
		}

		private static ResolutionOutcome cancelled(final PupilConflictView conflict) {
			return new ResolutionOutcome(true, List.of(), List.of(), List.of(conflict));
		}
	}

	private record NameKey(String name, String surname) {
	}

	private record ConflictPlan(PupilDraft pupil, List<Pupil> exactMatches, PupilConflictView view) {

		private ConflictPlan {
			exactMatches = List.copyOf(exactMatches);
		}
	}

	private record ElicitationDecision(boolean cancelled, PupilConflictResolution resolution) {
	}
}
