package com.training.addressesprocessing.repository;

import com.training.addressesprocessing.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Integer> {

    Settlement getByAddressCode(String addressCode);

    @Query("select s from Settlement s where s.addressCode like :addressCode%")
    List<Settlement> findByAddressCode(@Param("addressCode") String addressCode);
}
