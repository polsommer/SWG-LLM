package com.swgllm.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class IntelGpuCapabilityProbe {
    private final SystemInspector inspector;

    public IntelGpuCapabilityProbe() {
        this(new DefaultSystemInspector());
    }

    IntelGpuCapabilityProbe(SystemInspector inspector) {
        this.inspector = inspector;
    }

    public CapabilityReport probeUbuntu2204() {
        Map<String, String> checks = new LinkedHashMap<>();

        String osRelease = inspector.readOsRelease();
        boolean ubuntu2204 = osRelease.toLowerCase(Locale.ROOT).contains("id=ubuntu")
                && osRelease.contains("VERSION_ID=\"22.04\"");
        checks.put("ubuntu22.04", status(ubuntu2204, "Expected Ubuntu 22.04 host"));

        boolean driAvailable = inspector.fileExists("/dev/dri/renderD128") || inspector.fileExists("/dev/dri/card0");
        checks.put("intelDriNode", status(driAvailable, "Missing /dev/dri render node"));

        boolean levelZeroRuntime = inspector.commandExists("sycl-ls")
                || inspector.commandExists("clinfo")
                || inspector.envEnabled("ONEAPI_ROOT");
        checks.put("computeRuntime", status(levelZeroRuntime, "Missing Intel oneAPI/OpenCL runtime tools"));

        boolean mediaDriver = inspector.commandExists("vainfo") || inspector.fileExists("/usr/lib/x86_64-linux-gnu/dri/iHD_drv_video.so");
        checks.put("mediaDriver", status(mediaDriver, "Missing Intel media driver (iHD)"));

        String failure = checks.entrySet().stream()
                .filter(entry -> entry.getValue().startsWith("FAIL"))
                .map(entry -> entry.getKey() + ": " + entry.getValue().substring(6))
                .findFirst()
                .orElse("");
        boolean available = failure.isEmpty();
        return new CapabilityReport(available, available ? "" : failure, checks);
    }

    private static String status(boolean pass, String reason) {
        return pass ? "PASS" : "FAIL: " + reason;
    }

    public record CapabilityReport(boolean available, String reason, Map<String, String> checks) {
    }

    interface SystemInspector {
        String readOsRelease();

        boolean fileExists(String path);

        boolean commandExists(String command);

        boolean envEnabled(String key);
    }

    static class DefaultSystemInspector implements SystemInspector {
        @Override
        public String readOsRelease() {
            try {
                return Files.readString(Path.of("/etc/os-release"));
            } catch (IOException e) {
                return "";
            }
        }

        @Override
        public boolean fileExists(String path) {
            return Files.exists(Path.of(path));
        }

        @Override
        public boolean commandExists(String command) {
            try {
                Process process = new ProcessBuilder("bash", "-lc", "command -v " + command).start();
                return process.waitFor() == 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public boolean envEnabled(String key) {
            String value = System.getenv(key);
            return value != null && !value.isBlank();
        }
    }
}
