package com.jacolp.document.application.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.document.controller.AdminDocumentTreeNode;
import com.jacolp.document.infrastructure.persistence.dataobject.DocumentDO;
import com.jacolp.document.infrastructure.persistence.mapper.DocumentMapper;
import com.jacolp.system.api.UserProfileApi;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminDocumentServiceTest {

    @Test
    void groupsActiveDocumentsByOwnerAndBatchLoadsProfiles() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        UserProfileApi profiles = mock(UserProfileApi.class);
        when(mapper.listActiveForAdmin()).thenReturn(List.of(
                document(7L, 10L, "Newest", LocalDateTime.of(2026, 9, 20, 11, 0), "document/7/state/a.bin"),
                document(8L, 10L, "Older", LocalDateTime.of(2026, 9, 20, 10, 0), null),
                document(9L, 20L, "Other", LocalDateTime.of(2026, 9, 20, 9, 0), "document/9/state/b.bin")));
        when(profiles.getProfilesByIds(any())).thenReturn(Map.of(
                10L, new UserProfileApi.UserProfile(10L, "alice", "Alice")));

        List<AdminDocumentTreeNode> tree = new AdminDocumentService(mapper, profiles).listTree();

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).userId()).isEqualTo(10L);
        assertThat(tree.get(0).username()).isEqualTo("alice");
        assertThat(tree.get(0).documents()).extracting(item -> item.documentId())
                .containsExactly(7L, 8L);
        assertThat(tree.get(0).documents()).extracting(item -> item.hasSnapshot())
                .containsExactly(true, false);
        assertThat(tree.get(1).userId()).isEqualTo(20L);
        assertThat(tree.get(1).username()).isNull();
        verify(profiles).getProfilesByIds(any());
    }

    @Test
    void returnsEmptyTreeWithoutLookingUpProfiles() {
        DocumentMapper mapper = mock(DocumentMapper.class);
        UserProfileApi profiles = mock(UserProfileApi.class);
        when(mapper.listActiveForAdmin()).thenReturn(List.of());

        assertThat(new AdminDocumentService(mapper, profiles).listTree()).isEmpty();
    }

    private static DocumentDO document(long id, long ownerUserId, String title, LocalDateTime lastModifyTime,
                                       String objectKey) {
        return new DocumentDO(id, ownerUserId, title, objectKey, 0L, lastModifyTime, ownerUserId, false,
                0L, lastModifyTime, lastModifyTime);
    }
}
