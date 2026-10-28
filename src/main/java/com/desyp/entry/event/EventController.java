package com.desyp.entry.event;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/event")
@RequiredArgsConstructor
public class EventController {

    private final EventStatusService eventStatusService;

    @GetMapping("/status")
    public EventStatusResponse status() {
        return eventStatusService.status();
    }
}
