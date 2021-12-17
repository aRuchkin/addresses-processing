package com.training.addressesprocessing.repository;

import com.training.addressesprocessing.domain.KladrStreetDictionary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface KladrStreetDictionaryRepository extends JpaRepository<KladrStreetDictionary, Integer> {

    KladrStreetDictionary findByKladr(String kladr);

    @Query("select ksd from KladrStreetDictionary ksd where kladr like :partKladr%")
    KladrStreetDictionary findByPartKladr(@Param("partKladr") String partKladr);

}
