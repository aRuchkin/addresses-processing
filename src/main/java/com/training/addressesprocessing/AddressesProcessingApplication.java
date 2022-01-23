package com.training.addressesprocessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class AddressesProcessingApplication {

	public static void main(String[] args) {
		SpringApplication.run(AddressesProcessingApplication.class, args);
	}

}
