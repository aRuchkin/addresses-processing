package com.training.addressesprocessing.service;

import com.linuxense.javadbf.DBFException;
import com.linuxense.javadbf.DBFReader;
import com.training.addressesprocessing.AddressesProcessingApplicationProperties;
import com.training.addressesprocessing.domain.KladrDictionary;
import com.training.addressesprocessing.domain.KladrStreetDictionary;
import com.training.addressesprocessing.model.FromDbfFiasAndKladrModel;
import com.training.addressesprocessing.repository.KladrDictionaryRepository;
import com.training.addressesprocessing.repository.KladrStreetDictionaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Main service for extraction data from DBF and processing dictionaries
 */
@Service
public class DbfProcessingService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final String IMPORTED_FILE_ENCODING = "CP866";
    private static final String ALLOWABLE_FILE_NAME_PREFIX = "ADDROB";
    private static final String TEMPORARY_FOLDER_FOR_UNZIPPED_FILES = "TEMP";
    private static final int FIAS_FIELD_INDEX = 1;  // index of FIAS identifier in database record
    private static final int KLADR_FIELD_INDEX = 8; // index of KLADR identifier in database record

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
        String fullPathArchive = applicationProperties.getAddressFilePath()
                + applicationProperties.getAddressFileName();
        File destinationFolder = new File(applicationProperties.getAddressFilePath()
                + TEMPORARY_FOLDER_FOR_UNZIPPED_FILES);
        extractNecessaryFilesFromArchive(fullPathArchive, destinationFolder);
        processingExtractedFiles(destinationFolder);
        deleteTemporaryFolder(destinationFolder);
        logger.info("All files are processed!");
    }

    /**
     * Searching files into zip archive and put in the temporary folder
     */
    private void extractNecessaryFilesFromArchive(String fullPathArchive, File destinationFolder) {
        try {
            if (destinationFolder.mkdir()) {
                logger.info("Created folder: " + destinationFolder.getName());
            }
            ZipInputStream zis = new ZipInputStream(new FileInputStream(fullPathArchive));
            ZipEntry zipEntry = zis.getNextEntry();
            byte[] buffer = new byte[1024];
            while (zipEntry != null) {
                String currentFileName = zipEntry.getName();
                if (isFileNeedsToProcessing(currentFileName)) {
                    File newFile = new File(destinationFolder, currentFileName);
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    logger.info("Extracted: " + currentFileName + " from archive");
                }
                zipEntry = zis.getNextEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Processing extracted files from temporary folder
     */
    private void processingExtractedFiles(File destinationFolder) {
        for (final File fileEntry : destinationFolder.listFiles()) {
            DBFReader reader =
                    createReader(fileEntry.getPath());
            logger.info("Processing file: " + fileEntry.getName());
            AtomicInteger processedDictionaryRecordCount = new AtomicInteger();
            int recordCount = reader.getRecordCount();
            for (int i = 0; i < recordCount; i++) {
                FromDbfFiasAndKladrModel fiasAndKladrModel = loadNextEntityData(reader);
                // searching in kladr street dictionary
                findByKladrInKladrStreetDictionary(processedDictionaryRecordCount, fiasAndKladrModel);
                // searching in kladr dictionary
                findByKladrAndSetFiasInKladrDictionary(processedDictionaryRecordCount, fiasAndKladrModel);
            }
            logger.info("Processed " + recordCount + " DBF records " +
                    "(" + processedDictionaryRecordCount + " matches)");
        }
    }

    /**
     * Deleting temporary folder with files
     */
    private void deleteTemporaryFolder(File destinationFolder) {
        for (File fileEntry : destinationFolder.listFiles()) {
            if (fileEntry.delete())
                logger.info("Deleted: " + fileEntry.getName());
        }
        if (destinationFolder.delete())
            logger.info("Deleted: " + destinationFolder.getName());
    }

    /**
     * Validation file name (ADDROB*.DBF)
     */
    private boolean isFileNeedsToProcessing(String fileName) {
        // todo check extension if needed
        return fileName.contains(ALLOWABLE_FILE_NAME_PREFIX);
    }

    private DBFReader createReader(String fileName) {
        InputStream stream;
        try {
            stream = new FileInputStream(fileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        DBFReader reader;
        try {
            reader = new DBFReader(stream);
        } catch (DBFException e) {
            throw new RuntimeException(e);
        }
        reader.setCharactersetName(IMPORTED_FILE_ENCODING);
        return reader;
    }

    /**
     * Get next row from DBF (one by one)
     */
    private FromDbfFiasAndKladrModel loadNextEntityData(DBFReader reader) {
        Object[] objects;
        try {
            objects = reader.nextRecord();
        } catch (DBFException e) {
            throw new RuntimeException(e);
        }
        return new FromDbfFiasAndKladrModel(
                objects[FIAS_FIELD_INDEX].toString(),
                objects[KLADR_FIELD_INDEX].toString());
    }

    /**
     * Searching row in KLADR Street Dictionary by KLADR and part KLADR
     */
    private void findByKladrInKladrStreetDictionary(AtomicInteger processedDictionaryRecordCount,
                                                    FromDbfFiasAndKladrModel fiasAndKladr) {
        // attempt to find by full KLADR
        KladrStreetDictionary kladrStreetDictionary =
                kladrStreetDictionaryRepository.findByKladr(fiasAndKladr.getKladr());
        if (kladrStreetDictionary != null) {
            setFiasInKladrStreetDictionary(processedDictionaryRecordCount, kladrStreetDictionary, fiasAndKladr.getFias());
//            setFiasInDictionary(processedDictionaryRecordCount, kladrStreetDictionary, fiasAndKladr.getFias());
        } else {
            // attempt to find by part KLADR (-2 last digits)
            kladrStreetDictionaryRepository.findByPartKladr(
                    fiasAndKladr.getKladr().substring(0, fiasAndKladr.getKladr().length() - 2))
                    .forEach(e -> setFiasInKladrStreetDictionary(processedDictionaryRecordCount, e, fiasAndKladr.getFias()));
        }
    }

    /**
     * Searching row in KLADR Dictionary by KLADR and part KLADR
     */
    private void findByKladrAndSetFiasInKladrDictionary(AtomicInteger processedDictionaryRecordCount,
                                                        FromDbfFiasAndKladrModel fiasAndKladr) {
        // attempt to find by full KLADR
        KladrDictionary kladrDictionary =
                kladrDictionaryRepository.findByKladr(fiasAndKladr.getKladr());
        if (kladrDictionary != null) {
            setFiasInKladrDictionary(processedDictionaryRecordCount, kladrDictionary, fiasAndKladr.getFias());
        } else {
            // attempt to find by part KLADR (-2 last digits)
            kladrDictionaryRepository.findByPartKladr(
                    fiasAndKladr.getKladr().substring(0, fiasAndKladr.getKladr().length() - 2))
                    .forEach(e -> setFiasInKladrDictionary(processedDictionaryRecordCount, e, fiasAndKladr.getFias()));
        }
    }

    /**
     * Set FIAS in KLADR Street Dictionary row and increment count
     */
    private void setFiasInKladrStreetDictionary(AtomicInteger processedDictionaryRecordCount,
                                                KladrStreetDictionary kladrStreetDictionary,
                                                String fiasId) {
        kladrStreetDictionary.setFias(fiasId);
        kladrStreetDictionaryRepository.save(kladrStreetDictionary);
        processedDictionaryRecordCount.incrementAndGet();
    }

    /**
     * Set FIAS in KLADR Dictionary row and increment count
     */
    private void setFiasInKladrDictionary(AtomicInteger processedDictionaryRecordCount,
                                          KladrDictionary kladrDictionary,
                                          String fiasId) {
        kladrDictionary.setFias(fiasId);
        kladrDictionaryRepository.save(kladrDictionary);
        processedDictionaryRecordCount.incrementAndGet();
    }

}
