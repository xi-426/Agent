package com.yan.agent.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderRepository
        extends JpaRepository<WorkOrder, Long> {

    List<WorkOrder> findByUserIdOrderByIdDesc(
            Long userId);

    List<WorkOrder> findByUserIdAndStatusOrderByIdDesc(
            Long userId,
            WorkOrder.Status status);

    long countByUserIdAndStatus(
            Long userId,
            WorkOrder.Status status);
}
