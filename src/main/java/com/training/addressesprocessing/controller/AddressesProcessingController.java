package com.training.addressesprocessing.controller;

import com.training.addressesprocessing.service.DbfProcessingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class AddressesProcessingController {

    private final DbfProcessingService dbfProcessingService;

    public AddressesProcessingController(DbfProcessingService dbfProcessingService) {
        this.dbfProcessingService = dbfProcessingService;
    }

    /**
     * Method for start processing
     */
    @GetMapping
    public String startProcessing() {
        dbfProcessingService.process();
        return "Process has started, look at log for details...";
    }

}
