package com.mimir.ai;

import java.util.List;

public interface TextGenerationGateway {

    GeneratedDraft generate(DraftGenerationRequest request);

    record DraftGenerationRequest(
            String baseTitle,
            String baseBody,
            List<String> baseTags,
            String visitContext,
            List<ImageFact> imageFacts,
            String revisionInstruction,
            DraftTarget target) {

        public DraftGenerationRequest {
            baseTags = baseTags == null ? List.of() : List.copyOf(baseTags);
            imageFacts = imageFacts == null ? List.of() : List.copyOf(imageFacts);
            target = target == null ? DraftTarget.FULL : target;
        }
    }

    enum DraftTarget {
        FULL,
        TITLE,
        BODY,
        TAGS
    }

    record ImageFact(
            int displayOrder,
            String category,
            String description,
            List<String> objects,
            String visibleText) {

        public ImageFact {
            objects = objects == null ? List.of() : List.copyOf(objects);
        }
    }

    record GeneratedDraft(String title, String body, List<String> tags) {

        public GeneratedDraft {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }
}
