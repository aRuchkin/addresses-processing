package com.training.addressesprocessing.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "sprav_kladr")
public class Kladr {

    @Id
    @Column(name = "id_kladr")
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

    public void setFias(String fias) {
        this.fias = fias;
    }
}
