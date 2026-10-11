package de.westarps.topteacher.model.loe;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Mutation metadata and canonical current content; retry receipts cache only neutral metadata. */
public record ExamAuthoringResult(int examId, long revision, long mutationRevision, boolean replayed,
		List<CreatedNode> created, List<DeletionImpact> deletions, List<String> diagnostics, Changes changes) {

	public ExamAuthoringResult {
		created = List.copyOf(created);
		deletions = List.copyOf(deletions);
		diagnostics = List.copyOf(diagnostics);
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record CreatedNode(@Nullable String clientKey, String type, int id) {
	}

	public record DeletionImpact(String type, int id, int parts, int categories, int tasks, int materials,
			int requirements, int criteria) {
	}

	/** Canonical affected content, read under the mutation lock rather than cached in its receipt. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Changes(List<LoePart> parts, List<LoeCategory> categories, List<LoeTask> tasks,
			List<LoeMaterial> materials, List<LoeRequirement> requirements, List<LoeCriterion> criteria,
			@Nullable String footerMarkdown, List<SiblingOrder> orders) {

		public Changes {
			parts = List.copyOf(parts);
			categories = List.copyOf(categories);
			tasks = List.copyOf(tasks);
			materials = List.copyOf(materials);
			requirements = List.copyOf(requirements);
			criteria = List.copyOf(criteria);
			orders = List.copyOf(orders);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record SiblingOrder(String type, @Nullable Integer parentId, List<Integer> ids) {

		public SiblingOrder {
			ids = List.copyOf(ids);
		}
	}
}
