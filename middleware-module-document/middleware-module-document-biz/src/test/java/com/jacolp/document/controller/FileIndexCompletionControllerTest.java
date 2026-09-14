package com.jacolp.document.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jacolp.common.core.result.Result;
import com.jacolp.document.application.fileindex.FileCompletionItem;
import com.jacolp.document.application.fileindex.FileIndexCompletionService;
import com.jacolp.document.application.fileindex.FileIndexResourceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileIndexCompletionControllerTest {

    @Test
    void delegatesKeywordAndLimitAndReturnsOnlyCompletionItems() {
        FileIndexCompletionService service = mock(FileIndexCompletionService.class);
        when(service.complete("设计", 5)).thenReturn(List.of(
                new FileCompletionItem("设计文档", FileIndexResourceType.DOCUMENT, "9", null)));
        FileIndexCompletionController controller = new FileIndexCompletionController(service);

        Result<List<FileCompletionItem>> result = controller.completion("设计", 5);

        assertThat(result.getCode()).isEqualTo(Result.SUCCESS);
        assertThat(result.getData()).containsExactly(
                new FileCompletionItem("设计文档", FileIndexResourceType.DOCUMENT, "9", null));
    }
}
