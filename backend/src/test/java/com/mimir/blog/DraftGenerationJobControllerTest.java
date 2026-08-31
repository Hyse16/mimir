package com.mimir.blog;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DraftGenerationJobControllerTest {

    private DraftGenerationJobCoordinator coordinator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        coordinator = mock(DraftGenerationJobCoordinator.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DraftGenerationJobController(coordinator)).build();
    }

    @Test
    void defaultsOmittedTargetToFull() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID baseVersionId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/blog-posts/{postId}/draft-generation-jobs", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseVersionId": "%s",
                                  "revisionInstruction": "전체 초안을 다듬어줘"
                                }
                                """.formatted(baseVersionId)))
                .andExpect(status().isAccepted());

        verify(coordinator).create(
                postId, baseVersionId, "전체 초안을 다듬어줘", DraftGenerationTarget.FULL, null);
    }

    @Test
    void passesValidTargetToCoordinator() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID baseVersionId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/blog-posts/{postId}/draft-generation-jobs", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseVersionId": "%s",
                                  "revisionInstruction": "제목만 바꿔줘",
                                  "target": "TITLE"
                                }
                                """.formatted(baseVersionId)))
                .andExpect(status().isAccepted());

        verify(coordinator).create(
                postId, baseVersionId, "제목만 바꿔줘", DraftGenerationTarget.TITLE, null);
    }

    @Test
    void passesPreviousTurnIdToCoordinator() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID baseVersionId = UUID.randomUUID();
        UUID previousTurnId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/blog-posts/{postId}/draft-generation-jobs", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseVersionId": "%s",
                                  "revisionInstruction": "직전 요청을 이어서 다듬어줘",
                                  "previousTurnId": "%s"
                                }
                                """.formatted(baseVersionId, previousTurnId)))
                .andExpect(status().isAccepted());

        verify(coordinator).create(postId, baseVersionId, "직전 요청을 이어서 다듬어줘",
                DraftGenerationTarget.FULL, previousTurnId);
    }

    @Test
    void returnsBadRequestForInvalidTarget() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID baseVersionId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/blog-posts/{postId}/draft-generation-jobs", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseVersionId": "%s",
                                  "revisionInstruction": "제목만 바꿔줘",
                                  "target": "INVALID"
                                }
                                """.formatted(baseVersionId)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(coordinator);
    }
}
