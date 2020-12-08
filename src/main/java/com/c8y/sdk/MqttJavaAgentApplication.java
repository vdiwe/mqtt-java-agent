package com.c8y.sdk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;



@SpringBootApplication
public class MqttJavaAgentApplication {
	
    public static void main(String[] args){

		ApplicationContext applicationContext = SpringApplication.run(MqttJavaAgentApplication.class, args);
		DeviceRegistration deviceRegistration = applicationContext.getBean(DeviceRegistration.class);
		deviceRegistration.checkIfDeviceRegistered();
	}
}