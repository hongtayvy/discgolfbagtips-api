package com.discgolfbagtips.api.profile;

import com.discgolfbagtips.api.recommendation.dto.BagAnalysisRequest;

/**
 * A saved bag with its full request body, so the front end can drop it straight back into
 * {@code /recommendations} or {@code /lineup} without rebuilding anything.
 */
public record BagProfileDetail(BagProfileSummary profile, BagAnalysisRequest request) {

    public static BagProfileDetail from(BagProfile saved, BagAnalysisRequest request) {
        return new BagProfileDetail(BagProfileSummary.from(saved), request);
    }
}
