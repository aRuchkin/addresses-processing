package com.training.addressesprocessing.service;

import com.linuxense.javadbf.DBFException;
import com.linuxense.javadbf.DBFReader;
import com.training.addressesprocessing.AddressesProcessingApplicationProperties;
import com.training.addressesprocessing.domain.KladrStreetDictionary;
import com.training.addressesprocessing.model.FromDbfFiasAndKladrModel;
import com.training.addressesprocessing.repository.KladrDictionaryRepository;
import com.training.addressesprocessing.repository.KladrStreetDictionaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Main service for extraction data from DBF and processing dictionaries
 */
@Service
public class DbfProcessingService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final String ENCODING = "CP866";
    private static final int FIAS_DBF_INDEX = 1;  // index of FIAS identifier in dbf record
    private static final int KLADR_DBF_INDEX = 8; // index of KLADR identifier in dbf record

    private final AddressesProcessingApplicationProperties applicationProperties;
    private final KladrDictionaryRepository kladrDictionaryRepository;
    private final KladrStreetDictionaryRepository kladrStreetDictionaryRepository;

    public DbfProcessingService(AddressesProcessingApplicationProperties applicationProperties,
                                KladrDictionaryRepository kladrDictionaryRepository,
                                KladrStreetDictionaryRepository kladrStreetDictionaryRepository) {
        this.applicationProperties = applicationProperties;
        this.kladrDictionaryRepository = kladrDictionaryRepository;
        this.kladrStreetDictionaryRepository = kladrStreetDictionaryRepository;
    }

    public void processingDbfDataBase() {
        // todo need to do well (?)
        searchFileInArchive("todo: fullPath to zip archive from properties will here")
                .forEach(fileName -> {
                            DBFReader reader = initDbfReader(fileName);
                            logger.info("Processing file: " + fileName);
                            int processedDictionaryRecordCount = 0;
                            int recordCount = reader.getRecordCount();
                            for (int i = 0; i < recordCount; i++) {
                                FromDbfFiasAndKladrModel fiasAndKladrModel = loadNextEntityData(reader);
                                // searching in kladr street dictionary
                                if (findByKladrAndSetFiasInKladrStreetDictionary(fiasAndKladrModel)) {
                                    processedDictionaryRecordCount++;
                                }
                                // searching in kladr dictionary
                                if (findByKladrAndSetFiasInKladrDictionary(fiasAndKladrModel)) {
                                    processedDictionaryRecordCount++;
                                }
                            }
                            logger.info("Processed " + recordCount + " DBF records " +
                                    "(" + processedDictionaryRecordCount + " matches)");
                        }
                );
    }

    /**
     * Searching files ADDROB*.DBF into zip archive
     */
    private ArrayList<String> searchFileInArchive(String pathZip) {
        ArrayList<String> listOfFileNames = new ArrayList<>();
        // todo implement searching files into zip archive
        listOfFileNames.add(applicationProperties.getDbfPath() + applicationProperties.getDbfName());
        return listOfFileNames;
    }

    private DBFReader initDbfReader(String fullFileName) {
        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(fullFileName);
        } catch (FileNotFoundException e) {
            throw new Error(e);
        }
        DBFReader reader;
        try {
            reader = openReader(fileInputStream);
        } catch (DBFException e) {
            throw new Error(e);
        }
        return reader;
    }

    private DBFReader openReader(InputStream file) throws DBFException {
        DBFReader reader = new DBFReader(file);
        reader.setCharactersetName(ENCODING);
        return reader;
    }

    /**
     * Get next row from DBF (one by one)
     */
    private FromDbfFiasAndKladrModel loadNextEntityData(DBFReader reader) {
        FromDbfFiasAndKladrModel dbfData;
        try {
            Object[] objects = reader.nextRecord();
            dbfData = new FromDbfFiasAndKladrModel(
                    objects[FIAS_DBF_INDEX].toString(),
                    objects[KLADR_DBF_INDEX].toString());
        } catch (DBFException e) {
            throw new Error(e);
        }
        return dbfData;
    }

    /**
     * Return true if match is found
     */
    private boolean findByKladrAndSetFiasInKladrStreetDictionary(FromDbfFiasAndKladrModel fiasAndKladr) {
        // attempt to find by full KLADR
        KladrStreetDictionary kladrStreetDictionary =
                kladrStreetDictionaryRepository.findByKladr(fiasAndKladr.getKladr());
        if (kladrStreetDictionary == null) {
            // attempt to find by part KLADR (-2 last digits)

            // I need to comment this part of code "findByPartKladr" because
            // query returns error "query did not return a unique result"
            // I don't know why. Can DB has non unique kladr?
//            kladrStreetDictionary = kladrStreetDictionaryRepository.findByPartKladr(
//                    fiasAndKladr.getKladr().substring(0, fiasAndKladr.getKladr().length() - 2));
        }
        if (kladrStreetDictionary != null) {
            kladrStreetDictionary.setFias(fiasAndKladr.getFias());
            kladrStreetDictionaryRepository.save(kladrStreetDictionary);
            return true;
        }
        return false;
    }

    private boolean findByKladrAndSetFiasInKladrDictionary(FromDbfFiasAndKladrModel fiasAndKladr) {
        // todo implement
        return false;
    }

}
