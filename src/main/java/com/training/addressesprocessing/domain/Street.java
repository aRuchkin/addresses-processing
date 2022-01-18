package com.training.addressesprocessing.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Street dictionary entity
 */
@Entity
@Table(name = "sprav_kladr_street")
public class Street {

    @Id
    private Integer id;

    /**
     * address code
     */
    @Column(name = "kladr")
    private String addressCode;

    /**
     * federal address code
     */
    @Column(name = "external_id")
    private String federalAddressCode;

    public Integer getId() {
        return id;
    }

    public String getAddressCode() {
        return addressCode;
    }

    public void setFederalAddressCode(String federalAddressCode) {
        this.federalAddressCode = federalAddressCode;
    }
}
