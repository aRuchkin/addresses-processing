package com.training.addressesprocessing.service;

import com.training.addressesprocessing.domain.Settlement;
import com.training.addressesprocessing.domain.Street;
import com.training.addressesprocessing.repository.SettlementRepository;
import com.training.addressesprocessing.repository.StreetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for saving entities to DB
 */
@Service
public class BatchAddressService {

    private final SettlementRepository settlementRepository;
    private final StreetRepository streetRepository;

    public BatchAddressService(SettlementRepository settlementRepository,
                               StreetRepository streetRepository) {
        this.settlementRepository = settlementRepository;
        this.streetRepository = streetRepository;
    }

    /**
     * Saving collections of entities to DB and clear
     *
     * @param settlements
     * @param streets
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void store(
            List<Settlement> settlements,
            List<Street> streets) {
        settlementRepository.saveAll(settlements);
        settlements.clear();
        streetRepository.saveAll(streets);
        streets.clear();
    }
}
