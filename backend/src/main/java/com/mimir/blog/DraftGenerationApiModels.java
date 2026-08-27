package com.mimir.blog;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class DraftGenerationApiModels {

    private DraftGenerationApiModels() {
    }

    record CreateDraftGenerationJobRequest(
            @NotNull UUID baseVersionId,
            @NotBlank @Size(max = 10_000) String revisionInstruction) {
    }
}
