package com.smartfix.request.dto;

import com.smartfix.request.domain.RequestStatus;

/** Map reporting boundary: aggregate counts only, never individual requests or identities. */
public record LocationRequestCount(Long locationId, RequestStatus status, long count) {}
