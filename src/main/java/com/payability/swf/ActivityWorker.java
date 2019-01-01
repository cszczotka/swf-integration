package com.payability.swf;

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.boot.json.JsonParser;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


@Component
public class ActivityWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActivityWorker.class);

    @Autowired
    private AmazonSimpleWorkflow swf;

    private AtomicBoolean stop = new AtomicBoolean(false);

    @Value("${swf.domain}")
    private String swfDomain;

    @Value("${swf.tasklist.name}")
    private String taskList;

    boolean isRunning() {
        return !stop.get();
    }

    void stop() {
        stop.set(true);
    }


    @PostConstruct
    public void startWorker() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            while (!stop.get()) {
                LOGGER.info("Polling for an activity task from the tasklist '" + swfDomain + "' in the domain '" + taskList + "'.");

                ActivityTask task = swf.pollForActivityTask(
                        new PollForActivityTaskRequest()
                                .withDomain(swfDomain)
                                .withTaskList(
                                        new TaskList().withName(taskList)));

                String taskToken = task.getTaskToken();

                if (taskToken != null) {
                    String result = null;
                    Throwable error = null;

                    try {
                        LOGGER.info("Executing the activity task with input '" + task.getInput() + "'.");
                        result = execute(task.getInput());
                    } catch (Throwable th) {
                        error = th;
                    }

                    if (error == null) {
                        LOGGER.info("The activity task succeeded with result '" + result + "'.");
                        swf.respondActivityTaskCompleted(
                                new RespondActivityTaskCompletedRequest()
                                        .withTaskToken(taskToken)
                                        .withResult(result));
                    } else {
                        LOGGER.info("The activity task failed with the error '" + error.getClass().getSimpleName() + "'.");
                        swf.respondActivityTaskFailed(
                                new RespondActivityTaskFailedRequest()
                                        .withTaskToken(taskToken)
                                        .withReason(error.getClass().getSimpleName())
                                        .withDetails(error.getMessage()));
                    }
                }
            }

        });
    }

    private String execute(String input) throws InterruptedException {
        JsonParser parser = new JacksonJsonParser();
        Map<String, Object> params = parser.parseMap(input);
        Long delay = Long.valueOf((String) params.getOrDefault("delay", "1000"));
        Thread.sleep(delay);
        return "Executed, " + input + "!";
    }


}
