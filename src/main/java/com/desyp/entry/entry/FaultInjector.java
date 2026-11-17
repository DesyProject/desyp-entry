package com.desyp.entry.entry;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FaultInjector {

    private final boolean enabled;
    private final String phase;
    private final String triggerPrefix;
    private final Path markerFile;

    public FaultInjector(
            @Value("${fault-injection.enabled:false}") boolean enabled,
            @Value("${fault-injection.phase:after-redis}") String phase,
            @Value("${fault-injection.trigger-prefix:fault-api-}") String triggerPrefix,
            @Value("${fault-injection.marker-file:/tmp/fault-api-triggered}") String markerFile) {
        this.enabled = enabled;
        this.phase = phase;
        this.triggerPrefix = triggerPrefix;
        this.markerFile = Path.of(markerFile);
    }

    public void crashOnce(String requestId, String currentPhase) {
        if (!enabled || !phase.equals(currentPhase) || !requestId.startsWith(triggerPrefix)) {
            return;
        }

        try {
            Path parent = markerFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    markerFile,
                    currentPhase + ":" + requestId,
                    StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException e) {
            return;
        } catch (IOException e) {
            throw new IllegalStateException("장애 주입 마커를 만들지 못했습니다.", e);
        }

        Runtime.getRuntime().halt(137);
    }
}
