package com.mimir.blog;

import com.mimir.ai.TextGenerationGateway.DraftTarget;

enum DraftGenerationTarget {
    FULL,
    TITLE,
    BODY,
    TAGS;

    DraftTarget toGatewayTarget() {
        return DraftTarget.valueOf(name());
    }
}
