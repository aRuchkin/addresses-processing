package com.training.addressesprocessing.service;

import com.training.addressesprocessing.domain.KladrDictionary;
import com.training.addressesprocessing.domain.KladrStreetDictionary;
import com.training.addressesprocessing.repository.KladrDictionaryRepository;
import com.training.addressesprocessing.repository.KladrStreetDictionaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for saving entities to DB
 */
@Service
public class EntitiesSavingService {

    private final KladrDictionaryRepository kladrDictionaryRepository;
    private final KladrStreetDictionaryRepository kladrStreetDictionaryRepository;

    public EntitiesSavingService(KladrDictionaryRepository kladrDictionaryRepository,
                                 KladrStreetDictionaryRepository kladrStreetDictionaryRepository) {
        this.kladrDictionaryRepository = kladrDictionaryRepository;
        this.kladrStreetDictionaryRepository = kladrStreetDictionaryRepository;
    }

    /**
     * Saving collections of entities to DB and clear
     * @param kladrDictionaries
     * @param kladrStreetDictionaries
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void savePacketsOfEntities(
            List<KladrDictionary> kladrDictionaries,
            List<KladrStreetDictionary> kladrStreetDictionaries) {
        kladrDictionaryRepository.saveAll(kladrDictionaries);
        kladrDictionaries.clear();
        kladrStreetDictionaryRepository.saveAll(kladrStreetDictionaries);
        kladrStreetDictionaries.clear();
    }
}
