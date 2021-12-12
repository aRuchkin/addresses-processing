package com.training.addressesprocessing.repository;

import com.training.addressesprocessing.domain.KladrStreet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KladrStreetRepository extends JpaRepository<KladrStreet, Integer> {

    KladrStreet findByKladr(String kladr);

}
