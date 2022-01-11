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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Main service for extraction data from DBF and processing dictionaries
 */
@Service
public class DbfProcessingService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final String IMPORTED_FILE_ENCODING = "CP866";
    private static final Pattern ALLOWABLE_FILE_NAME_PATTERN = Pattern.compile("\\bADDROB\\d{2}\\.DBF\\b");
    private static final String TEMPORARY_FOLDER_FOR_UNZIPPED_FILES = "TEMP";
    private static final int FIAS_FIELD_INDEX = 1;  // index of FIAS identifier in database record
    private static final int KLADR_FIELD_INDEX = 8; // index of KLADR identifier in database record

    private final KladrDictionaryRepository kladrDictionaryRepository;
    private final KladrStreetDictionaryRepository kladrStreetDictionaryRepository;
    private final String fullPathArchive;
    private final File destinationFolder;

    public DbfProcessingService(AddressesProcessingApplicationProperties applicationProperties,
                                KladrDictionaryRepository kladrDictionaryRepository,
                                KladrStreetDictionaryRepository kladrStreetDictionaryRepository) {
        this.kladrDictionaryRepository = kladrDictionaryRepository;
        this.kladrStreetDictionaryRepository = kladrStreetDictionaryRepository;
        this.fullPathArchive = applicationProperties.getAddressFilePath()
                + applicationProperties.getAddressFileName();
        this.destinationFolder = new File(applicationProperties.getAddressFilePath()
                + TEMPORARY_FOLDER_FOR_UNZIPPED_FILES);
    }

    public void processingDbfDataBase() {
        extractNecessaryFilesFromArchive(fullPathArchive, destinationFolder);
        processingExtractedFiles(destinationFolder);
        logger.info("All files are processed!");
    }

    /**
     * Searching files into zip archive and put in the temporary folder
     */
    private void extractNecessaryFilesFromArchive(String fullPathArchive, File destinationFolder) {
        prepareTemporaryFolder(destinationFolder);
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(fullPathArchive))) {
            for (ZipEntry zipEntry; (zipEntry = zipInputStream.getNextEntry()) != null; ) {
                String currentFileName = zipEntry.getName();
                if (isFileNeedsToProcessing(currentFileName)) {
                    Files.copy(zipInputStream, Paths.get(destinationFolder + "/" + currentFileName));
                    logger.info("Extracted: " + currentFileName + " from archive");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Processing extracted files from temporary folder
     */
    private void processingExtractedFiles(File destinationFolder) {
        try {
            List<File> filesInFolder = Files.walk(Paths.get(destinationFolder.getAbsolutePath()))
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .collect(Collectors.toList());
            for (File file : filesInFolder) {
                try (InputStream inputStream = new FileInputStream(file)) {
                    DBFReader reader = new DBFReader(inputStream);
                    reader.setCharactersetName(IMPORTED_FILE_ENCODING);
                    logger.info("Processing file: " + file.getName());
                    AtomicInteger processedDictionaryRecordCount = new AtomicInteger();
                    int recordCount = reader.getRecordCount();
                    logger.info("Need to process: " + recordCount + " records");
                    LocalDateTime packetProcessingTime = LocalDateTime.now();
                    for (int i = 0; i < recordCount; i++) {
                        if (i % 5000 == 0) {
                            logger.info("Processed " + i + " records");
                            logger.info((Duration.between(packetProcessingTime, LocalDateTime.now())).getSeconds() + " seconds");
                            packetProcessingTime = LocalDateTime.now();
                        }
                        FromDbfFiasAndKladrModel fiasAndKladrModel = loadNextEntityData(reader);
                        findByKladrAndSetFiasInDictionaries(processedDictionaryRecordCount, fiasAndKladrModel);
                    }
                    logger.info("Processed " + recordCount + " DBF records " +
                            "(" + processedDictionaryRecordCount + " matches)");
                    file.deleteOnExit();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deleting files from temporary folder (if need) or create if not exist
     */
    private void prepareTemporaryFolder(File destinationFolder) {
        if (destinationFolder.isDirectory()) {
            for (File fileEntry : destinationFolder.listFiles()) {
                if (fileEntry.delete())
                    logger.info("Deleted: " + fileEntry.getName());
            }
        } else {
            logger.info("Created temporary folder: " + destinationFolder.getName());
            destinationFolder.mkdir();
        }
    }

    /**
     * Validation file name (ADDROB*.DBF)
     */
    private boolean isFileNeedsToProcessing(String fileName) {
        return ALLOWABLE_FILE_NAME_PATTERN.matcher(fileName).find();
    }

    /**
     * Get next row from DBF (one by one)
     */
    private FromDbfFiasAndKladrModel loadNextEntityData(DBFReader reader) {
        Object[] objects;
        try {
            objects = reader.nextRecord();
            return new FromDbfFiasAndKladrModel(
                    objects[FIAS_FIELD_INDEX].toString(),
                    objects[KLADR_FIELD_INDEX].toString());
        } catch (DBFException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Searching row in Dictionaries by KLADR and part KLADR
     */
    private void findByKladrAndSetFiasInDictionaries(AtomicInteger processedDictionaryRecordCount,
                                                     FromDbfFiasAndKladrModel fiasAndKladr) {
        if (fiasAndKladr.getKladr().trim().length() == 17) {
            // find in KLADR Street Dictionary
            KladrStreetDictionary kladrStreetDictionary =
                    kladrStreetDictionaryRepository.findByKladr(fiasAndKladr.getKladr());
            if (kladrStreetDictionary != null) {
                setFiasInDictionary(processedDictionaryRecordCount, kladrStreetDictionary, fiasAndKladr.getFias());
            } else {
                // attempt to find by part KLADR (-2 last digits)
                kladrStreetDictionaryRepository.findByPartKladr(
                        getPartOfKladr(fiasAndKladr.getKladr()))
                        .forEach(e -> setFiasInDictionary(processedDictionaryRecordCount, e, fiasAndKladr.getFias()));
            }
        } else {
            // find in KLADR Dictionary
            KladrDictionary kladrDictionary =
                    kladrDictionaryRepository.findByKladr(fiasAndKladr.getKladr());
            if (kladrDictionary != null) {
                setFiasInDictionary(processedDictionaryRecordCount, kladrDictionary, fiasAndKladr.getFias());
            } else {
                // attempt to find by part KLADR (-2 last digits)
                kladrDictionaryRepository.findByPartKladr(
                        getPartOfKladr(fiasAndKladr.getKladr()))
                        .forEach(e -> setFiasInDictionary(processedDictionaryRecordCount, e, fiasAndKladr.getFias()));
            }
        }
    }

    /**
     * Get part KLADR (without 2 last digits) by full KLADR
     */
    private String getPartOfKladr(String fullKladr) {
        return fullKladr.substring(0, fullKladr.length() - 2);
    }

    /**
     * Set FIAS in KLADR Street Dictionary row and increment count
     */
    private void setFiasInDictionary(AtomicInteger processedDictionaryRecordCount,
                                     KladrStreetDictionary kladrStreetDictionary,
                                     String fiasId) {
        kladrStreetDictionary.setFias(fiasId);
        kladrStreetDictionaryRepository.save(kladrStreetDictionary);
        processedDictionaryRecordCount.incrementAndGet();
    }

    /**
     * Set FIAS in KLADR Dictionary row and increment count
     */
    private void setFiasInDictionary(AtomicInteger processedDictionaryRecordCount,
                                     KladrDictionary kladrDictionary,
                                     String fiasId) {
        kladrDictionary.setFias(fiasId);
        kladrDictionaryRepository.save(kladrDictionary);
        processedDictionaryRecordCount.incrementAndGet();
    }
}
