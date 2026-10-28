package com.desyp.entry.entry;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/event")
@RequiredArgsConstructor
public class EntryController {

    private final EntryService entryService;

    /** 새 응모는 201, 같은 requestId 재요청은 처음 번호로 200 */
    @PostMapping(path = "/entries", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EntryResponse> submit(
            @Valid @RequestBody EntryRequest request,
            @RequestHeader(name = "X-Client-Id", required = false) String clientId) {
        EntryResult result = entryService.submit(request, clientId);
        HttpStatus status = result.outcome() == EntryResult.Outcome.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(new EntryResponse(result.receiptNumber()));
    }
}
