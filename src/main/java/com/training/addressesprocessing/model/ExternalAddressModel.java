package com.training.addressesprocessing.model;

public class ExternalAddressModel {

    public ExternalAddressModel(String federalAddressCode, String addressCode) {
        this.federalAddressCode = federalAddressCode;
        this.addressCode = addressCode;
    }

    /**
     * federal address code from DBF
     */
    private String federalAddressCode;

    /**
     * address code from DBF
     */
    private String addressCode;

    public String getFederalAddressCode() {
        return federalAddressCode;
    }

    public String getAddressCode() {
        return addressCode;
    }
}
