package com.mimir.blog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mimir.ai.VisionAnalysisGateway;
import com.mimir.ai.VisionAnalysisGateway.VisionAnalysis;
import com.mimir.ai.VisionAnalysisGateway.VisionImage;
import com.mimir.storage.StorageProvider;

@Component
class ImageAnalysisJobRunner {

    private final ImageAnalysisJobService service;
    private final VisionAnalysisGateway gateway;
    private final StorageProvider storageProvider;
    private final int batchSize;

    ImageAnalysisJobRunner(
            ImageAnalysisJobService service,
            VisionAnalysisGateway gateway,
            StorageProvider storageProvider,
            @Value("${mimir.ai.vision-batch-size:3}") int batchSize) {
        if (batchSize < 2 || batchSize > 4) {
            throw new IllegalArgumentException("Vision batch size must be between 2 and 4.");
        }
        this.service = service;
        this.gateway = gateway;
        this.storageProvider = storageProvider;
        this.batchSize = batchSize;
    }

    void run(UUID jobId) {
        boolean started = false;
        try {
            service.start(jobId);
            started = true;
            List<ImageAnalysisJobService.AnalysisWorkItem> items = service.pendingItems(jobId);
            for (int start = 0; start < items.size(); start += batchSize) {
                int end = Math.min(items.size(), start + batchSize);
                analyzeBatch(jobId, items.subList(start, end));
            }
        } catch (RuntimeException error) {
            if (started) {
                service.failRemaining(jobId, "JOB_EXECUTION_FAILED");
                return;
            }
            throw error;
        }
    }

    private void analyzeBatch(UUID jobId, List<ImageAnalysisJobService.AnalysisWorkItem> batch) {
        Map<UUID, String> failures = new HashMap<>();
        List<VisionImage> images = new ArrayList<>();
        for (ImageAnalysisJobService.AnalysisWorkItem item : batch) {
            if (item.analysisStorageKey() == null) {
                failures.put(item.assetId(), "ANALYSIS_IMAGE_UNAVAILABLE");
                continue;
            }
            try {
                images.add(new VisionImage(
                        item.assetId(),
                        item.displayOrder(),
                        storageProvider.read(item.analysisStorageKey())));
            } catch (RuntimeException error) {
                failures.put(item.assetId(), "ANALYSIS_IMAGE_READ_FAILED");
            }
        }

        List<VisionAnalysis> successes = List.of();
        if (!images.isEmpty()) {
            try {
                List<VisionAnalysis> results = gateway.analyze(images);
                if (!sameAssets(images, results)) {
                    images.forEach(image -> failures.put(image.assetId(), "VISION_OUTPUT_INVALID"));
                } else {
                    successes = results;
                }
            } catch (RuntimeException error) {
                images.forEach(image -> failures.put(image.assetId(), "VISION_PROVIDER_FAILED"));
            }
        }
        service.completeBatch(jobId, successes, failures);
    }

    private boolean sameAssets(List<VisionImage> images, List<VisionAnalysis> results) {
        if (results == null || images.size() != results.size()) {
            return false;
        }
        Set<UUID> expected = images.stream().map(VisionImage::assetId).collect(HashSet::new, Set::add, Set::addAll);
        Set<UUID> actual = results.stream().map(VisionAnalysis::assetId).collect(HashSet::new, Set::add, Set::addAll);
        return expected.size() == images.size() && expected.equals(actual);
    }
}
