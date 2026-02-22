package com.swgllm;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {

    @Test
    void shouldParseArguments() {
        Main main = new Main();
        int exitCode = new CommandLine(main).execute("--mode", "benchmark");
        assertEquals(0, exitCode);
    }
}
