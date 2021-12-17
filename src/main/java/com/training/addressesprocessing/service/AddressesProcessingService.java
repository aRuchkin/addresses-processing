package com.training.addressesprocessing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AddressesProcessingService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final DbfProcessingService dbfProcessingService;

    public AddressesProcessingService(DbfProcessingService dbfProcessingService) {
        this.dbfProcessingService = dbfProcessingService;
    }

    /**
     * Modify street dictionary (find by KLADR identifier -> add FIAS identifier)
     */
    public void processingAddresses() {
        logger.info("Processing Dictionaries...");
        dbfProcessingService.processingDbfDataBase();
    }
}
