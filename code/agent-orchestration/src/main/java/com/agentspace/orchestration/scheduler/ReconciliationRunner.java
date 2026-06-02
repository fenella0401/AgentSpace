package com.agentspace.orchestration.scheduler;

import com.agentspace.orchestration.service.ReconciliationService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 启动时触发重启恢复 reconcile（FE7）。test profile 下不自动跑（由测试显式调用）。
 */
@Component
@Profile("!test")
public class ReconciliationRunner implements ApplicationRunner {

    private final ReconciliationService reconciliationService;

    public ReconciliationRunner(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        reconciliationService.reconcileInFlight();
    }
}
