package com.training.addressesprocessing.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;


@Entity
@Table(name = "sprav_kladr_street")
public class KladrStreet {

    @Id
    private Integer id;

    /**
     * KLADR identifier
     */
    private String kladr;

    /**
     * FIAS identifier
     */
    @Column(name = "external_id")
    private String fias;

    public Integer getId() {
        return id;
    }

    public String getKladr() {
        return kladr;
    }
}
