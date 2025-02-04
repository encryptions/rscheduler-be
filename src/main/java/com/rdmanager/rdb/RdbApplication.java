package com.rdmanager.rdb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RdbApplication {

	public static void main(String[] args) {
		SpringApplication.run(RdbApplication.class, args);
		
	}

}
