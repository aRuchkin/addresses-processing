package com.training.addressesprocessing.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Address dictionary entity
 */
@Entity
@Table(name = "sprav_kladr")
public class Settlement {

    @Id
    @Column(name = "id_kladr")
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
