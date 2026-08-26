package com.mimir.ai;

import java.util.List;
import java.util.UUID;

public interface VisionAnalysisGateway {

    List<VisionAnalysis> analyze(List<VisionImage> images);

    record VisionImage(UUID assetId, int displayOrder, byte[] content) {
    }

    record VisionAnalysis(
            UUID assetId,
            int displayOrder,
            String category,
            String description,
            List<String> objects,
            String visibleText) {
    }
}
