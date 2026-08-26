package com.mimir.ai;

import java.util.List;

public interface TextGenerationGateway {

    GeneratedDraft generate(DraftGenerationRequest request);

    record DraftGenerationRequest(
            String baseTitle,
            String baseBody,
            String visitContext,
            List<ImageFact> imageFacts,
            String revisionInstruction) {

        public DraftGenerationRequest {
            imageFacts = imageFacts == null ? List.of() : List.copyOf(imageFacts);
        }
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
