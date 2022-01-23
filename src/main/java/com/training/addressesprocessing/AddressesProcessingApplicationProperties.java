package com.training.addressesprocessing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AddressesProcessingApplicationProperties {

    /**
     * Data Base path
     */
    private String addressFilePath;

    /**
     * Data Base archive name (zip)
     */
    private String addressFileName;

    public String getAddressFilePath() {
        return addressFilePath;
    }

    public String getAddressFileName() {
        return addressFileName;
    }

    public void setAddressFilePath(String addressFilePath) {
        this.addressFilePath = addressFilePath;
    }

    public void setAddressFileName(String addressFileName) {
        this.addressFileName = addressFileName;
    }
}
