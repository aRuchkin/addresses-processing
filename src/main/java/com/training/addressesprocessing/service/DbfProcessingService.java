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
import java.util.ArrayList;
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
    private static final int PACKET_PROCESSING_SIZE = 5000;

    private final KladrDictionaryRepository kladrDictionaryRepository;
    private final KladrStreetDictionaryRepository kladrStreetDictionaryRepository;
    private final String fullPathArchive;
    private final File destinationFolder;
    private final EntitiesSavingService entitiesSavingService;

    public DbfProcessingService(AddressesProcessingApplicationProperties applicationProperties,
                                KladrDictionaryRepository kladrDictionaryRepository,
                                KladrStreetDictionaryRepository kladrStreetDictionaryRepository,
                                EntitiesSavingService entitiesSavingService) {
        this.kladrDictionaryRepository = kladrDictionaryRepository;
        this.kladrStreetDictionaryRepository = kladrStreetDictionaryRepository;
        this.fullPathArchive = applicationProperties.getAddressFilePath()
                + applicationProperties.getAddressFileName();
        this.destinationFolder = new File(applicationProperties.getAddressFilePath()
                + TEMPORARY_FOLDER_FOR_UNZIPPED_FILES);
        this.entitiesSavingService = entitiesSavingService;
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
                    List<KladrStreetDictionary> kladrStreetDictionaries = new ArrayList<>();
                    List<KladrDictionary> kladrDictionaries = new ArrayList<>();
                    int recordCount = reader.getRecordCount();
                    logger.info("Need to process: " + recordCount + " records");
                    LocalDateTime packetProcessingTime = LocalDateTime.now();
                    for (int i = 0; i < recordCount; i++) {
                        if (i % PACKET_PROCESSING_SIZE == 0) {
                            logger.info("Processed " + i + " records");
                            logger.info((Duration.between(packetProcessingTime, LocalDateTime.now())).getSeconds() + " seconds");
                            packetProcessingTime = LocalDateTime.now();
                            // save both collections of entities to BD and clear
                            entitiesSavingService.savePacketsOfEntities(kladrDictionaries, kladrStreetDictionaries);
                        }
                        FromDbfFiasAndKladrModel fiasAndKladrModel = loadNextEntityData(reader);
                        findByKladrInDictionaries(
                                processedDictionaryRecordCount,
                                fiasAndKladrModel,
                                kladrStreetDictionaries,
                                kladrDictionaries);
                    }
                    // save rest collections of entities to BD and clear
                    entitiesSavingService.savePacketsOfEntities(kladrDictionaries, kladrStreetDictionaries);
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
                    objects[FIAS_FIELD_INDEX].toString().trim(),
                    objects[KLADR_FIELD_INDEX].toString().trim());
        } catch (DBFException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Searching row in Dictionaries by KLADR and part KLADR
     */
    private void findByKladrInDictionaries(AtomicInteger processedDictionaryRecordCount,
                                           FromDbfFiasAndKladrModel fiasAndKladr,
                                           List<KladrStreetDictionary> kladrStreetDictionaries,
                                           List<KladrDictionary> kladrDictionaries) {
        int fiasFromDbfLength = fiasAndKladr.getKladr().length();
        if (fiasFromDbfLength == 17) {
            KladrStreetDictionary kladrStreetDictionary =
                    kladrStreetDictionaryRepository.findByKladr(fiasAndKladr.getKladr());
            if (kladrStreetDictionary != null) {
                addEntityKladrStreetToCollection(
                        processedDictionaryRecordCount,
                        kladrStreetDictionary,
                        fiasAndKladr.getFias(),
                        kladrStreetDictionaries);
            } else {
                // attempt to find by part KLADR (-2 last digits)
                kladrStreetDictionaryRepository.findByPartKladr(
                        getPartOfKladr(fiasAndKladr.getKladr()))
                        .forEach(e -> addEntityKladrStreetToCollection(
                                processedDictionaryRecordCount,
                                e,
                                fiasAndKladr.getFias(),
                                kladrStreetDictionaries));
            }
        } else if (fiasFromDbfLength != 0) {
            KladrDictionary kladrDictionary =
                    kladrDictionaryRepository.findByKladr(fiasAndKladr.getKladr());
            if (kladrDictionary != null) {
                addEntityKladrToCollection(
                        processedDictionaryRecordCount,
                        kladrDictionary,
                        fiasAndKladr.getFias(),
                        kladrDictionaries);
            } else {
                // attempt to find by part KLADR (-2 last digits)
                kladrDictionaryRepository.findByPartKladr(
                        getPartOfKladr(fiasAndKladr.getKladr()))
                        .forEach(e -> addEntityKladrToCollection(
                                processedDictionaryRecordCount,
                                e,
                                fiasAndKladr.getFias(),
                                kladrDictionaries));
            }
        }
    }

    /**
     * Setting FIAS to entity and addition to collection (for streets only)
     */
    private void addEntityKladrStreetToCollection(AtomicInteger processedDictionaryRecordCount,
                                                  KladrStreetDictionary kladrStreetDictionary,
                                                  String fias,
                                                  List<KladrStreetDictionary> kladrStreetDictionaries) {
        kladrStreetDictionary.setFias(fias);
        kladrStreetDictionaries.add(kladrStreetDictionary);
        processedDictionaryRecordCount.incrementAndGet();
    }

    /**
     * Setting FIAS to entity and addition to collection (for not streets)
     */
    private void addEntityKladrToCollection(AtomicInteger processedDictionaryRecordCount,
                                            KladrDictionary kladrDictionary,
                                            String fias,
                                            List<KladrDictionary> kladrDictionaries) {
        kladrDictionary.setFias(fias);
        kladrDictionaries.add(kladrDictionary);
        processedDictionaryRecordCount.incrementAndGet();
    }

    /**
     * Get part KLADR (without 2 last digits) by full KLADR
     */
    private String getPartOfKladr(String fullKladr) {
        return fullKladr.substring(0, fullKladr.length() - 2);
    }
}
