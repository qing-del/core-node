package com.jacolp.middleware.module.media.api;

import com.jacolp.module.media.api.command.MediaFileLookupCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaFileLookupContractTest {

    @Test
    void lookupCommandCopiesFilenamesAndAllowsUnclassifiedTopic() {
        List<String> filenames = new ArrayList<>(List.of("cover.png", "diagram.png"));
        MediaFileLookupCommand command = new MediaFileLookupCommand(8L, null, filenames);

        filenames.clear();

        assertEquals(8L, command.userId());
        assertEquals(null, command.topicId());
        assertEquals(List.of("cover.png", "diagram.png"), command.filenames());
        assertThrows(UnsupportedOperationException.class, () -> command.filenames().add("late.png"));
    }
}
