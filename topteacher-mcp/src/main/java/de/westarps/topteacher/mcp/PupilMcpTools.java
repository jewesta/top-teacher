package de.westarps.topteacher.mcp;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilConflictResolution;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilConflictView;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilCreateResult;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilCreateStatus;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilDraft;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilListResult;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilListScope;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilUpdateDuplicateAction;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilUpdateResult;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilUpdateStatus;
import de.westarps.topteacher.mcp.PupilMcpSchema.PupilView;
import de.westarps.topteacher.mcp.PupilRosterConflictResolver.ResolutionOutcome;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.SchoolClass;

/**
 * MCP operations for discovering, creating, and renaming pupils independently
 * of courses.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class PupilMcpTools {

	private static final int MAXIMUM_PUPILS = 200;
	private static final int MAXIMUM_QUERY_LENGTH = 200;

	private final PupilRepository pupils;
	private final PupilRosterConflictResolver createConflictResolver;
	private final PupilUpdateConflictResolver updateConflictResolver;
	private final PupilWriter writer;

	public PupilMcpTools(final PupilRepository pupils, final PupilRosterConflictResolver createConflictResolver,
			final PupilUpdateConflictResolver updateConflictResolver, final PupilWriter writer) {
		this.pupils = Objects.requireNonNull(pupils, "pupils");
		this.createConflictResolver = Objects.requireNonNull(createConflictResolver, "createConflictResolver");
		this.updateConflictResolver = Objects.requireNonNull(updateConflictResolver, "updateConflictResolver");
		this.writer = Objects.requireNonNull(writer, "writer");
	}

	@McpTool(name = "list_pupils", title = "List pupils",
			description = "List pupils, optionally filtered by a case-insensitive name fragment. The default scope is ACTIVE. Use ARCHIVED or ALL only when the user explicitly asks for archived historical records.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = true,
					destructiveHint = false, idempotentHint = true, openWorldHint = false))
	public PupilListResult listPupils(@McpToolParam(required = false,
			description = "Optional case-insensitive fragment matched against first-name/last-name and last-name/first-name text.") final String query,
			@McpToolParam(required = false,
					description = "ACTIVE by default. ARCHIVED returns only archived pupils; ALL returns both.") final PupilListScope scope) {
		final String normalizedQuery = normalizedQuery(query);
		final PupilListScope effectiveScope = scope == null ? PupilListScope.ACTIVE : scope;
		final Map<Integer, SchoolClass> latestSchoolClasses = pupils.findLatestSchoolClassByPupilId();
		final List<PupilView> matches = pupils.findAll().stream().filter(pupil -> included(pupil, effectiveScope))
				.filter(pupil -> matches(pupil, normalizedQuery)).map(pupil -> view(pupil, latestSchoolClasses))
				.toList();
		return new PupilListResult(matches);
	}

	@McpTool(name = "create_pupils", title = "Create pupils",
			description = "Create active pupils without assigning them to a course. Active exact-name matches and names repeated in the request require CREATE or SKIP. Archived pupils are historical and do not participate in collision detection. No writes occur until every conflict is resolved.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = false, idempotentHint = false, openWorldHint = false))
	public PupilCreateResult createPupils(final McpSyncRequestContext context, @McpToolParam(
			description = "Pupils to create. entryKey must be unique and stable across a retry.") final List<PupilDraft> pupilDrafts,
			@McpToolParam(required = false,
					description = "CREATE or SKIP decisions from a previous NEEDS_RESOLUTION result.") final List<PupilConflictResolution> resolutions) {
		final List<PupilDraft> validatedDrafts = validateDrafts(pupilDrafts);
		final ResolutionOutcome resolution = createConflictResolver.resolveWithoutReuse(context, validatedDrafts,
				resolutions);
		if (resolution.cancelled()) {
			return cancelledCreation("The user cancelled pupil conflict resolution. No database changes were made.",
					resolution.conflicts(), List.of());
		}
		if (!resolution.conflicts().isEmpty()) {
			return new PupilCreateResult(PupilCreateStatus.NEEDS_RESOLUTION,
					"No database changes were made. Ask the user to choose CREATE or SKIP for each conflict, then call this tool again with resolutions.",
					List.of(), resolution.conflicts(), List.of());
		}
		if (resolution.resolvedPupils().isEmpty()) {
			return cancelledCreation("All pupil entries were skipped. No database changes were made.", List.of(),
					resolution.skippedEntryKeys());
		}

		final List<Pupil> created = writer.create(resolution.resolvedPupils());
		final Map<Integer, SchoolClass> latestSchoolClasses = pupils.findLatestSchoolClassByPupilId();
		return new PupilCreateResult(PupilCreateStatus.CREATED, "Pupils were created in one transaction.",
				created.stream().map(pupil -> view(pupil, latestSchoolClasses)).toList(), List.of(),
				resolution.skippedEntryKeys());
	}

	@McpTool(name = "update_pupil", title = "Rename a pupil",
			description = "Change the first name, surname, or both for one pupil selected by ID from list_pupils. Omitted fields remain unchanged. Lifecycle and course assignments are never changed. If the resulting exact name belongs to another active pupil, explicit UPDATE_ANYWAY confirmation is required.",
			generateOutputSchema = true, annotations = @McpTool.McpAnnotations(readOnlyHint = false,
					destructiveHint = true, idempotentHint = true, openWorldHint = false))
	public PupilUpdateResult updatePupil(final McpSyncRequestContext context,
			@McpToolParam(description = "Stable pupil ID obtained from list_pupils.") final int pupilId,
			@McpToolParam(required = false,
					description = "New first name. Omit to keep the existing first name.") final String name,
			@McpToolParam(required = false,
					description = "New surname. Omit to keep the existing surname.") final String surname,
			@McpToolParam(required = false,
					description = "Decision from a previous NEEDS_CONFIRMATION result. Use UPDATE_ANYWAY or CANCEL.") final PupilUpdateDuplicateAction duplicateNameAction) {
		if (name == null && surname == null) {
			throw new IllegalArgumentException("name or surname must be supplied");
		}
		final Pupil current = pupils.findById(pupilId)
				.orElseThrow(() -> new IllegalArgumentException("Pupil does not exist: " + pupilId));
		final String requestedName = name == null ? current.name()
				: PupilMcpSchema.boundedText(name, "name", PupilMcpSchema.MAXIMUM_NAME_LENGTH);
		final String requestedSurname = surname == null ? current.surname()
				: PupilMcpSchema.boundedText(surname, "surname", PupilMcpSchema.MAXIMUM_NAME_LENGTH);
		final PupilUpdateConflictResolver.ResolutionOutcome resolution = updateConflictResolver.resolve(context,
				current, requestedName, requestedSurname, duplicateNameAction);
		if (resolution.cancelled()) {
			return new PupilUpdateResult(PupilUpdateStatus.CANCELLED,
					"The user cancelled the rename. No database changes were made.", List.of(), resolution.conflicts());
		}
		if (!resolution.conflicts().isEmpty()) {
			return new PupilUpdateResult(PupilUpdateStatus.NEEDS_CONFIRMATION,
					"No database changes were made. Ask whether to UPDATE_ANYWAY or CANCEL, then call this tool again with duplicateNameAction.",
					List.of(), resolution.conflicts());
		}

		final Pupil updated = writer.updateName(pupilId, requestedName, requestedSurname,
				resolution.duplicateNameConfirmed());
		return new PupilUpdateResult(PupilUpdateStatus.UPDATED, "The pupil name was updated.",
				List.of(view(updated, pupils.findLatestSchoolClassByPupilId())), List.of());
	}

	private static List<PupilDraft> validateDrafts(final List<PupilDraft> pupilDrafts) {
		Objects.requireNonNull(pupilDrafts, "pupils must not be null");
		if (pupilDrafts.isEmpty()) {
			throw new IllegalArgumentException("pupils must not be empty");
		}
		if (pupilDrafts.size() > MAXIMUM_PUPILS) {
			throw new IllegalArgumentException("pupils must not contain more than " + MAXIMUM_PUPILS + " entries");
		}
		final Set<String> entryKeys = new LinkedHashSet<>();
		for (final PupilDraft pupil : pupilDrafts) {
			Objects.requireNonNull(pupil, "pupil entry must not be null");
			if (!entryKeys.add(pupil.entryKey())) {
				throw new IllegalArgumentException("pupil entryKey must be unique: " + pupil.entryKey());
			}
		}
		return List.copyOf(pupilDrafts);
	}

	private static PupilCreateResult cancelledCreation(final String message, final List<PupilConflictView> conflicts,
			final List<String> skippedEntryKeys) {
		return new PupilCreateResult(PupilCreateStatus.CANCELLED, message, List.of(), conflicts, skippedEntryKeys);
	}

	private static boolean included(final Pupil pupil, final PupilListScope scope) {
		return switch (scope) {
		case ACTIVE -> pupil.lifecycle() == Lifecycle.ACTIVE;
		case ARCHIVED -> pupil.lifecycle() == Lifecycle.INACTIVE;
		case ALL -> true;
		};
	}

	private static boolean matches(final Pupil pupil, final String query) {
		if (query.isEmpty()) {
			return true;
		}
		final String firstNameFirst = (pupil.name() + " " + pupil.surname()).toLowerCase(Locale.ROOT);
		final String surnameFirst = (pupil.surname() + " " + pupil.name()).toLowerCase(Locale.ROOT);
		return firstNameFirst.contains(query) || surnameFirst.contains(query);
	}

	private static String normalizedQuery(final String query) {
		if (query == null || query.isBlank()) {
			return "";
		}
		return PupilMcpSchema.boundedText(query, "query", MAXIMUM_QUERY_LENGTH).toLowerCase(Locale.ROOT);
	}

	private static PupilView view(final Pupil pupil, final Map<Integer, SchoolClass> latestSchoolClasses) {
		final SchoolClass latestSchoolClass = latestSchoolClasses.get(pupil.id());
		return new PupilView(pupil.id(), pupil.name(), pupil.surname(), pupil.lifecycle().name(),
				latestSchoolClass == null ? "" : latestSchoolClass.name(),
				latestSchoolClass == null ? "" : latestSchoolClass.getDisplayName());
	}
}
