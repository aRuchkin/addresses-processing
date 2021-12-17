package com.training.addressesprocessing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AddressesProcessingApplicationProperties {

    /**
     * Data Base path
     */
    private String dbfPath;

    /**
     * Data Base archive name (zip)
     */
    private String dbfName;

    public String getDbfPath() {
        return dbfPath;
    }

    public String getDbfName() {
        return dbfName;
    }

    public void setDbfPath(String dbfPath) {
        this.dbfPath = dbfPath;
    }

    public void setDbfName(String dbfName) {
        this.dbfName = dbfName;
    }
}
