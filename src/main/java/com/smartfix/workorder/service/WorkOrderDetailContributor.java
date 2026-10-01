package com.smartfix.workorder.service;

import com.smartfix.request.spi.RequestDetailContributor;
import com.smartfix.workorder.repository.WorkOrderRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class WorkOrderDetailContributor implements RequestDetailContributor {
    private final WorkOrderRepository orders;

    public WorkOrderDetailContributor(WorkOrderRepository orders) {
        this.orders = orders;
    }

    public List<DetailItem> describe(Long requestId) {
        return orders.findByRequestId(requestId)
                .map(
                        w ->
                                List.of(
                                        new DetailItem("Work order", w.getId().toString()),
                                        new DetailItem("Work order status", w.getStatus().name()),
                                        new DetailItem(
                                                "Solution",
                                                w.getResolutionNote() == null
                                                        ? "Pending"
                                                        : w.getResolutionNote())))
                .orElse(List.of());
    }
}
