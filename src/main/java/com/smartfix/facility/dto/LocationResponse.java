package com.smartfix.facility.dto;

public record LocationResponse(
    Long id,
    String locationCode,
    String building,
    String floor,
    String room,
    String displayName,
    boolean active
) {
}
