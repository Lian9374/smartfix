package com.smartfix.request.spi;

import com.smartfix.request.dto.RequestTransitionResponse;

/** Synchronous invariant hook, joining the lifecycle transaction. Not a notification listener. */
public interface RequestTransitionParticipant {
    void beforeTransition(RequestTransitionResponse change);

    void afterTransition(RequestTransitionResponse change);
}
