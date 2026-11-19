package dev.applyops.orchestrator.discovery;

import dev.applyops.orchestrator.ApplyOpsProperties;
import dev.applyops.orchestrator.TaskQueues;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleAlreadyRunningException;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleClientOptions;
import io.temporal.client.schedules.ScheduleIntervalSpec;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.SchedulePolicy;
import io.temporal.client.schedules.ScheduleSpec;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Creates the "job-discovery" Temporal Schedule on startup (idempotent). */
@Component
@ConditionalOnProperty(name = "applyops.discovery.schedule-enabled", havingValue = "true")
public class DiscoveryScheduleInitializer implements ApplicationRunner {

    static final String SCHEDULE_ID = "job-discovery";
    private static final Logger log = LoggerFactory.getLogger(DiscoveryScheduleInitializer.class);

    private final WorkflowClient workflowClient;
    private final ApplyOpsProperties props;

    public DiscoveryScheduleInitializer(WorkflowClient workflowClient, ApplyOpsProperties props) {
        this.workflowClient = workflowClient;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        ScheduleClient schedules = ScheduleClient.newInstance(
                workflowClient.getWorkflowServiceStubs(),
                ScheduleClientOptions.newBuilder()
                        .setNamespace(workflowClient.getOptions().getNamespace())
                        .build());
        Schedule schedule = Schedule.newBuilder()
                .setAction(ScheduleActionStartWorkflow.newBuilder()
                        .setWorkflowType(JobDiscoveryWorkflow.class)
                        .setOptions(WorkflowOptions.newBuilder()
                                .setWorkflowId(SCHEDULE_ID)
                                .setTaskQueue(TaskQueues.ORCHESTRATOR)
                                .build())
                        .build())
                .setSpec(ScheduleSpec.newBuilder()
                        .setIntervals(List.of(new ScheduleIntervalSpec(props.discovery().interval())))
                        .build())
                .setPolicy(SchedulePolicy.newBuilder()
                        .setOverlap(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP)
                        .build())
                .build();
        try {
            schedules.createSchedule(SCHEDULE_ID, schedule, ScheduleOptions.newBuilder().build());
            log.info("Created schedule {} every {}", SCHEDULE_ID, props.discovery().interval());
        } catch (ScheduleAlreadyRunningException e) {
            log.info("Schedule {} already exists", SCHEDULE_ID);
        }
    }
}
