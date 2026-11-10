package com.burgerlab.common.error;

import java.time.OffsetDateTime;

/**
 * Public error shape. It intentionally contains no exception class, SQL,
 * stack trace or credential information.
 */
public record ApiErrorResponse(
        String code,
        String message,
        String traceId,
        OffsetDateTime timestamp,
        String path
) {
}
