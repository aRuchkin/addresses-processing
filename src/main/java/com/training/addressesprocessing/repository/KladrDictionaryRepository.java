package com.training.addressesprocessing.repository;

import com.training.addressesprocessing.domain.KladrDictionary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KladrDictionaryRepository extends JpaRepository<KladrDictionary, Integer> {

    KladrDictionary findByKladr(String kladr);

    @Query("select ksd from KladrDictionary ksd where ksd.kladr like :partKladr%")
    List<KladrDictionary> findByPartKladr(@Param("partKladr") String partKladr);
}
