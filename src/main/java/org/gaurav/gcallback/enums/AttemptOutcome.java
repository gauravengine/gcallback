package org.gaurav.gcallback.enums;

public enum AttemptOutcome {
    IN_FLIGHT, // attempt started, result not recorded yet
    SUCCESS, // 2XX
    HTTP_ERROR, // non 2XX responses
    TIMEOUT, // connect or request timeout
    CONNECT_ERROR, // network i/o failure
    REQUEST_ERROR, //request could not be built (bad uri, etc) never retried
    WORKER_LOST // worker died mid-attempt , set by the reaper
}
