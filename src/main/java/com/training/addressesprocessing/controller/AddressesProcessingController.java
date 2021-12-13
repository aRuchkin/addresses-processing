package com.training.addressesprocessing.controller;

import com.training.addressesprocessing.service.AddressesProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class AddressesProcessingController {

    private final AddressesProcessingService addressesProcessingService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public AddressesProcessingController(AddressesProcessingService addressesProcessingService) {
        this.addressesProcessingService = addressesProcessingService;
    }

    /**
     * Method for start processing
     */
    @GetMapping
    public void startProcessing() {
        logger.info("Start processing...");
        addressesProcessingService.processingAddresses();
    }

}
