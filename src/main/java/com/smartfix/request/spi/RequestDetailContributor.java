package com.smartfix.request.spi;

import java.util.List;

/**
 * Optional real read information from workorder/SLA; no placeholder deadlines or cross-module
 * entities.
 */
public interface RequestDetailContributor {
    record DetailItem(String label, String value) {}

    List<DetailItem> describe(Long requestId);
}
