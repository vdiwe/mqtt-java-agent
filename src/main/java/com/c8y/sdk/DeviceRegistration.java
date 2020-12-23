package com.c8y.sdk;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeviceRegistration {

	@Value("${bootstrap_tenant}")
	private String bootstrap_tenant;
	@Value("${bootstrap_username}")
	private String bootstrap_username;
	@Value("${bootstrap_password}")
	private String bootstrap_password;
	@Value("${serverUrl}")
	private String serverUrl;
	@Value("${protocol}")
	private String protocol;
	@Value("${clientId}")
	private String clientId;
	@Value("${device_name}")
	private String device_name;
	@Value("${credFileName}")
	private String credFileName;
    
	
	String credFilePath=System.getProperty("user.dir")+"\\config\\";
    
	/**
	 * @param file
	 * @throws FileNotFoundException
	 * @throws MqttException
	 */
    
    public void checkIfDeviceRegistered() {
    	System.out.print(credFilePath+credFileName);
    	try {
			File file = new File(credFilePath+credFileName);
	        if(!file.exists()) {
	        	getDeviceCredentails();
	        	registerDeviceAndSendMeasurement(file);
	        
	        } else {
	        	registerDeviceAndSendMeasurement(file);
	        }
		
		}catch(Exception e) {
			System.out.print("errored out");
			e.printStackTrace();
		}
	}

    
	public void registerDeviceAndSendMeasurement(File file) throws FileNotFoundException, MqttException {
		 Scanner scanner = new Scanner(file);
   	  String cred="";
   	  while(scanner.hasNextLine()) {
   		  cred = scanner.nextLine();
   		  break;
   	  }
   	  scanner.close();
   	  
   	  System.out.print(cred);
   	  
   	  final MqttConnectOptions options1 = new MqttConnectOptions();
         //  options.setUserName(tenant + "/" + username);
         //  options.setPassword(password.toCharArray());
           options1.setUserName(cred.split(",")[1] + "/" + cred.split(",")[2]);
           options1.setPassword(cred.split(",")[3].toCharArray());

           // connect the client to Cumulocity
           @SuppressWarnings("resource")
		final MqttClient client1 = new MqttClient(protocol+"://"+serverUrl, clientId, null);
           client1.connect(options1);
       
       // register a new device
           client1.publish("s/us", ("100," + device_name + ",c8y_MQTTDevice").getBytes(), 2, false);

       // set device's hardware information
           client1.publish("s/us", "110,S123456789,MQTT test model,Rev0.1".getBytes(), 2, false);

       // add restart operation
           client1.publish("s/us", "114,c8y_Restart".getBytes(), 2, false);
           
        // register a new device
           client1.publish("s/us", ("117,1").getBytes(), 2, false);

       System.out.println("The device \"" + device_name + "\" has been registered successfully!");

       // listen for operations
       client1.subscribe("s/ds", new IMqttMessageListener() {
           public void messageArrived (final String topic, final MqttMessage message) throws Exception {
               final String payload = new String(message.getPayload());

               System.out.println("Received operation " + payload);
               if (payload.startsWith("510")) {
                   // execute the operation in another thread to allow the MQTT client to
                   // finish processing this message and acknowledge receipt to the server
                   Executors.newSingleThreadScheduledExecutor().execute(new Runnable() {
                       public void run() {
                           try {
                               System.out.println("Simulating device restart...");
                               client1.publish("s/us", "501,c8y_Restart".getBytes(), 2, false);
                               System.out.println("...restarting...");
                               Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                               client1.publish("s/us", "503,c8y_Restart".getBytes(), 2, false);
                               System.out.println("...done...");
                           } catch (MqttException e) {
                               e.printStackTrace();
                           } catch (InterruptedException e) {
                               e.printStackTrace();
                           }
                       }
                   });
               }else {
            	   System.out.println("Simulating device restart...");
                   client1.publish("s/us", "502,Failed,This operation not handled in Agent".getBytes(), 2, false);
               }
           }


       });

       // generate a random temperature (10º-20º) measurement and send it every 7 seconds
       Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(new Runnable() {
           public void run () {
               try {
                   int temp = (int) (Math.random() * 10 + 40);

                   System.out.println("Sending temperature measurement (" + temp + "º) ...");
                   client1.publish("s/us", new MqttMessage(("211," + temp).getBytes()));
               } catch (MqttException e) {
                   e.printStackTrace();
               }
           }
       }, 1, 7, TimeUnit.SECONDS);
       
	}
	
	/**
	 * @throws MqttSecurityException
	 * @throws MqttException
	 */
	public void getDeviceCredentails() throws MqttSecurityException, MqttException {
		final MqttConnectOptions options = new MqttConnectOptions();
	      
		options.setUserName(bootstrap_tenant + "/" + bootstrap_username);
	    options.setPassword(bootstrap_password.toCharArray());

	    final MqttClient client = new MqttClient(protocol+"://"+serverUrl, clientId, null);
	    client.connect(options);

	        //obtain credentials
	        Executors.newSingleThreadScheduledExecutor().execute(new Runnable() {
	            public void run() {
	                try {
	                    System.out.println("waiting for credentials...");
	                    client.subscribe("s/dcr", new IMqttMessageListener() {
	                    //	@Override
	                        public void messageArrived (final String topic, final MqttMessage message) throws Exception {
	                            final String payload = new String(message.getPayload());
	                            File file = new File(credFilePath+credFileName);
	                            if(!file.exists()) {
	                            	boolean filecreated = file.createNewFile();
	                            	if (filecreated) {
	                            		FileWriter fw = new FileWriter(file.getAbsoluteFile());
	                            		fw.append(payload);
	                            		fw.flush();
	                            		fw.close();
	                            	}
	                            }
	                            
	                            System.out.println("Received credentials " + payload);
	                        }});
	                    System.out.println("...done...");
	                } catch (Exception e) {
	                	System.out.print("erroredout1: "+e.getMessage());
	                    e.printStackTrace();
	                }
	            }
	        });
	        

	        for(int i= 0;i<5;i++) {
	       	 try {

				client.publish("s/ucr","".getBytes(),0,true);
				System.out.print("\npublished Empty message");
				Thread.sleep(5000);
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
	       	 
	       }
	}

}
