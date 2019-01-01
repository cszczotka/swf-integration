package com.payability.swf;

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClientBuilder;
import com.amazonaws.services.simpleworkflow.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class WorkflowWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowWorker.class);

    @Autowired
    private AmazonSimpleWorkflow swf;

    private AtomicBoolean stop = new AtomicBoolean(false);

    @Value("${swf.domain}")
    private String swfDomain;

    @Value("${swf.tasklist.name}")
    private String taskList;

    @Value("${swf.activity.name}")
    private String activity;

    @Value("${swf.activity.version}")
    private String activityVersion;

    @Value("${swf.workflow.type}")
    private String workflowType;

    boolean isRunning(){
        return !stop.get();
    }

    void stop() {
        stop.set(true);
    }

    @PostConstruct
    public void start() {

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            PollForDecisionTaskRequest task_request =
                    new PollForDecisionTaskRequest()
                            .withDomain(swfDomain)
                            .withTaskList(new TaskList().withName(taskList));
            while (!stop.get()) {
                LOGGER.info(
                        "Polling for a decision task from the tasklist '" +
                                taskList + "' in the domain '" +
                                swfDomain + "'.");

                DecisionTask task = swf.pollForDecisionTask(task_request);

                String taskToken = task.getTaskToken();
                String type = task.getWorkflowType() != null ? task.getWorkflowType().getName() : "N/A";
                if (taskToken != null && workflowType.equals(type)) {
                    try {
                        executeDecisionTask(taskToken, task.getEvents());
                    } catch (Throwable th) {
                        LOGGER.error("Error during executing decision task ", th);
                    }
                }
            }
        });
    }

    public void executeDecisionTask(String taskToken, List<HistoryEvent> events ) {

        List<Decision> decisions = new ArrayList<Decision>();

        String workflowInput = null;
        int scheduled_activities = 0;
        int open_activities = 0;
        boolean activityCompleted = false;
        String result = null;

        LOGGER.info("Executing the decision task for the history events: [");
        for (HistoryEvent event : events) {
            LOGGER.info("  " + event);
            switch(event.getEventType()) {
                case "WorkflowExecutionStarted":
                    workflowInput =
                            event.getWorkflowExecutionStartedEventAttributes()
                                    .getInput();
                    break;
                case "ActivityTaskScheduled":
                    scheduled_activities++;
                    break;
                case "ScheduleActivityTaskFailed":
                    scheduled_activities--;
                    break;
                case "ActivityTaskStarted":
                    scheduled_activities--;
                    open_activities++;
                    break;
                case "ActivityTaskCompleted":
                    open_activities--;
                    activityCompleted = true;
                    result = event.getActivityTaskCompletedEventAttributes()
                            .getResult();
                    break;
                case "ActivityTaskFailed":
                    open_activities--;
                    break;
                case "ActivityTaskTimedOut":
                    open_activities--;
                    break;
            }
        }
        LOGGER.info("]");

        if (activityCompleted) {
            decisions.add(
                    new Decision()
                            .withDecisionType(DecisionType.CompleteWorkflowExecution)
                            .withCompleteWorkflowExecutionDecisionAttributes(
                                    new CompleteWorkflowExecutionDecisionAttributes()
                                            .withResult(result)));
        } else {
            if (open_activities == 0 && scheduled_activities == 0) {

                ScheduleActivityTaskDecisionAttributes attrs =
                        new ScheduleActivityTaskDecisionAttributes()
                                .withActivityType(new ActivityType()
                                        .withName(activity)
                                        .withVersion(activityVersion))
                                .withActivityId(UUID.randomUUID().toString())
                                .withInput(workflowInput);

                decisions.add(
                        new Decision()
                                .withDecisionType(DecisionType.ScheduleActivityTask)
                                .withScheduleActivityTaskDecisionAttributes(attrs));
            } else {
                // an instance of HelloActivity is already scheduled or running. Do nothing, another
                // task will be scheduled once the activity completes, fails or times out
            }
        }

        LOGGER.info("Exiting the decision task with the decisions " + decisions);

        swf.respondDecisionTaskCompleted(
                new RespondDecisionTaskCompletedRequest()
                        .withTaskToken(taskToken)
                        .withDecisions(decisions));
    }
}
