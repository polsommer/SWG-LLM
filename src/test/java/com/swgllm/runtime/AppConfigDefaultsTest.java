package com.swgllm.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AppConfigDefaultsTest {

    @Test
    void shouldDefaultToAlwaysOnContinuousAndLiveAutopublish() {
        AppConfig config = new AppConfig();

        assertEquals(0, config.getContinuous().getIdleTimeoutMs());
        assertTrue(config.getAutopublish().isEnabled());
        assertFalse(config.getAutopublish().isDryRun());
        assertFalse(config.getAutopublish().isManualApprovalForHighImpactChanges());
    }
}
