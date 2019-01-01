package com.payability;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;


//@SpringBootApplication
@ComponentScan
@EnableAutoConfiguration
public class Application extends SpringBootServletInitializer {

    @Autowired
    private Environment env;

    public Application(){
        super();
        setRegisterErrorPageFilter(false);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Application.class);
    }

    @Bean
    public AmazonSimpleWorkflow swf() {
        String accessKey = env.getProperty("swf.accessKey");
        String secretKey = env.getProperty("swf.secretKey");
        String swfRegion = env.getProperty("swf.region");
        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);

        return AmazonSimpleWorkflowClientBuilder.standard().withRegion(swfRegion)
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .build();
    }


    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
