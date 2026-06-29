package com.mailengine.domain;

public enum MessageJobStatus {
    PENDING,
    CLAIMED,
    SENT,
    FAILED,
    SUPPRESSED,
    RETRY_SCHEDULED
}
