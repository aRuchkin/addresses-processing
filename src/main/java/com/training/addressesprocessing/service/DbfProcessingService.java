package com.training.addressesprocessing.service;

import com.linuxense.javadbf.DBFException;
import com.linuxense.javadbf.DBFReader;
import com.training.addressesprocessing.AddressesProcessingApplicationProperties;
import com.training.addressesprocessing.domain.KladrDictionary;
import com.training.addressesprocessing.domain.KladrStreetDictionary;
import com.training.addressesprocessing.model.FromDbfFiasAndKladrModel;
import com.training.addressesprocessing.repository.KladrDictionaryRepository;
import com.training.addressesprocessing.repository.KladrStreetDictionaryRepository;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
        String fullArchiveName = applicationProperties.getAddressFilePath()
                + applicationProperties.getAddressFileName();
        searchFilesInArchiveAndProcessing(fullArchiveName);
    }

    /**
     * Searching files into zip archive and processing
     */
    private void searchFilesInArchiveAndProcessing(String pathZip) {
        ZipFile zipFile = initZipFile(pathZip);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry currentProcessingFile = entries.nextElement();
            if (isFileNeedsToProcessing(currentProcessingFile.getName())) {
                File extractedFile = extractFileFromArchive(zipFile, currentProcessingFile);
                DBFReader reader =
                        createReader(extractedFile.getPath());
                logger.info("Processing file: " + currentProcessingFile.getName());
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
                // todo delete file
                // doesn't work
//                try {
//                    Files.delete(Paths.get(extractedFile.getPath()));
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
            }
        }
        logger.info("Zip archive processed!");
        // todo delete temporary folder
    }

    private File extractFileFromArchive(ZipFile zipFile, ZipEntry currentProcessingFile) {
        File extractingFile = new File(
                applicationProperties.getAddressFilePath() + TEMPORARY_FOLDER_FOR_UNZIPPED_FILES,
                String.valueOf(currentProcessingFile));
        try {
            extractingFile.getParentFile().mkdir();
            InputStream inputStream = zipFile.getInputStream(currentProcessingFile);
            OutputStream outputStream = new FileOutputStream(extractingFile);
            IOUtils.copy(inputStream, outputStream);
            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return extractingFile;
    }

    /**
     * Validation file name (ADDROB*.DBF)
     */
    private boolean isFileNeedsToProcessing(String fileName) {
        // todo check extension if needed
        return fileName.contains(ALLOWABLE_FILE_NAME_PREFIX);
    }

    private ZipFile initZipFile(String pathZip) {
        try {
            return new ZipFile(pathZip);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
