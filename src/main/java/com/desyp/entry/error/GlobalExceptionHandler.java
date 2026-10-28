package com.desyp.entry.error;

import com.desyp.entry.metrics.EntryMetrics;
import com.desyp.entry.metrics.EntryOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String INVALID_REQUEST = "입력한 정보를 다시 확인해주세요.";

    private final EntryMetrics metrics;

    @ExceptionHandler(EntryException.class)
    public ResponseEntity<ErrorResponse> handleEntry(EntryException e) {
        return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleInvalid(Exception e) {
        metrics.count(EntryOutcome.INVALID);
        return ResponseEntity.badRequest().body(new ErrorResponse(INVALID_REQUEST));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(new ErrorResponse(INVALID_REQUEST));
    }

    /** Redis 장애·과부하는 혼잡으로 알린다. 프론트가 같은 requestId로 자동 재시도한다 */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleRedis(DataAccessException e) {
        metrics.count(EntryOutcome.ERROR);
        log.error("Redis 처리 실패", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("접속자가 많아 접수하지 못했어요. 잠시 후 다시 응모해주세요."));
    }
}
