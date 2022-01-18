package com.training.addressesprocessing.repository;

import com.training.addressesprocessing.domain.Street;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StreetRepository extends JpaRepository<Street, Integer> {

    Street getByAddressCode(String addressCode);

    @Query("select s from Street s where s.addressCode like :addressCode%")
    List<Street> findByAddressCode(@Param("addressCode") String addressCode);

}
