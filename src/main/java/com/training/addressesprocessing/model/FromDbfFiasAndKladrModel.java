package com.training.addressesprocessing.model;

public class FromDbfFiasAndKladrModel {

    public FromDbfFiasAndKladrModel(String fias, String kladr) {
        this.fias = fias;
        this.kladr = kladr;
    }

    /**
     * Id FIAS from DBF
     */
    private String fias;

    /**
     * Id KLADR from DBF
     */
    private String kladr;

    public String getFias() {
        return fias;
    }

    public String getKladr() {
        return kladr;
    }
}
