package de.westarps.topteacher.model.loe;

import java.util.List;

import org.jspecify.annotations.Nullable;

/** Targeted assessment updates; omitted values preserve saved values. */
public record RequirementAssessment(Integer requirementId, @Nullable Integer freePointUnits,
		@Nullable List<CriterionAward> criterionAwards, @Nullable String comment) {

	public RequirementAssessment {
		criterionAwards = criterionAwards == null ? null : List.copyOf(criterionAwards);
	}

	public record CriterionAward(Integer criterionId, int pointUnits) {
	}
}
