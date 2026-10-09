package com.smartfix.technician.service;

import com.smartfix.common.exception.BusinessConflictException;
import com.smartfix.common.exception.InputValidationException;
import com.smartfix.request.service.RequestAssignmentAccessService;
import com.smartfix.workorder.service.WorkOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public workload API for recommendations and reports; work orders remain owned by C. */
@Service
@Transactional(readOnly = true)
public class TechnicianWorkloadService {
    private final WorkOrderService workOrders;
    private final RequestAssignmentAccessService assignments;

    public TechnicianWorkloadService(WorkOrderService workOrders, RequestAssignmentAccessService assignments) {
        this.workOrders = workOrders;
        this.assignments = assignments;
    }

    /**
     * Counts CREATED, IN_PROGRESS, ON_HOLD and REOPENED orders with a current active assignment.
     * The argument is users.id, not technician_profiles.id. Withdrawn, completed and closed
     * work orders do not count. The owning service defines and enforces these rules.
     */
    public long countOpenWorkOrders(Long technicianUserId) {
        if (technicianUserId == null || technicianUserId <= 0) {
            throw new InputValidationException("A valid technician account id is required.");
        }
        // C intentionally treats a missing lookup as no readable assignment. For recommendations
        // that must not be misrepresented as an actual zero workload for every technician.
        if (!assignments.isAvailable()) {
            throw new BusinessConflictException(
                    "Technician workload is unavailable until assignment lookup is connected.");
        }
        return workOrders.countOpenWorkOrders(technicianUserId);
    }
}
