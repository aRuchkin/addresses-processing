package com.training.addressesprocessing.controller;

import com.training.addressesprocessing.service.DbfProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class AddressesProcessingController {

    private final DbfProcessingService dbfProcessingService;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public AddressesProcessingController(DbfProcessingService dbfProcessingService) {
        this.dbfProcessingService = dbfProcessingService;
    }

    /**
     * Method for start processing
     */
    @GetMapping
    public void startProcessing() {
        logger.info("Start processing...");
        dbfProcessingService.process();
    }

}
