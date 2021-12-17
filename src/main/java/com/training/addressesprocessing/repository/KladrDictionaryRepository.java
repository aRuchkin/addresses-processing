package com.training.addressesprocessing.repository;

import com.training.addressesprocessing.domain.KladrDictionary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KladrDictionaryRepository extends JpaRepository<KladrDictionary, Integer> {

    KladrDictionary findByKladr(String kladr);

}
