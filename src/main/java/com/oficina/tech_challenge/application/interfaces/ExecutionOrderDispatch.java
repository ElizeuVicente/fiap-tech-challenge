package com.oficina.tech_challenge.application.interfaces;

import java.util.UUID;

public interface ExecutionOrderDispatch {
    void schedule(UUID orderId);
    void schedule(UUID orderId, UUID branchId);
}
