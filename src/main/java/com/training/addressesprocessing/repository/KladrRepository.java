package com.training.addressesprocessing.repository;

import com.training.addressesprocessing.domain.Kladr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KladrRepository extends JpaRepository<Kladr, Integer> {

    Kladr findByKladr(String kladr);

}
