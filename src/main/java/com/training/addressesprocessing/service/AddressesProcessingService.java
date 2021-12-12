package com.training.addressesprocessing.service;

import com.training.addressesprocessing.domain.Kladr;
import com.training.addressesprocessing.model.FromDbfFiasAndKladrModel;
import com.training.addressesprocessing.repository.KladrRepository;
import com.training.addressesprocessing.repository.KladrStreetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AddressesProcessingService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final KladrRepository kladrRepository;
    private final KladrStreetRepository kladrStreetRepository;
    private final FiasService fiasService;

    public AddressesProcessingService(KladrRepository kladrRepository,
                                      KladrStreetRepository kladrStreetRepository, FiasService fiasService) {
        this.kladrRepository = kladrRepository;
        this.kladrStreetRepository = kladrStreetRepository;
        this.fiasService = fiasService;
    }

    public void processingAddresses() {
        processingKladr();
        processingKladrStreet();
    }

    /**
     * Modify address dictionary (find by KLADR identifier -> add FIAS identifier)
     */
    private void processingKladr() {
        logger.info("Processing Kladr Dictionary...");
        // todo implement
        // below example
        FromDbfFiasAndKladrModel fromDbfFiasAndKladr = fiasService.getFiadAndKladr();
        Kladr kladr = kladrRepository.findByKladr(fromDbfFiasAndKladr.getKladr());
        kladr.setFias(fromDbfFiasAndKladr.getFias());
        kladrRepository.save(kladr);
    }

    /**
     * Modify street dictionary (find by KLADR identifier -> add FIAS identifier)
     */
    private void processingKladrStreet() {
        logger.info("Processing Kladr Street Dictionary...");
        // todo implement
    }

}
