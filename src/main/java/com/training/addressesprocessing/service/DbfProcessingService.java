package com.training.addressesprocessing.service;

import com.linuxense.javadbf.DBFException;
import com.linuxense.javadbf.DBFReader;
import com.training.addressesprocessing.AddressesProcessingApplicationProperties;
import com.training.addressesprocessing.domain.Settlement;
import com.training.addressesprocessing.domain.Street;
import com.training.addressesprocessing.model.ExternalAddressModel;
import com.training.addressesprocessing.repository.SettlementRepository;
import com.training.addressesprocessing.repository.StreetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private static final Pattern ALLOWABLE_FILE_NAME_PATTERN = Pattern.compile("^ADDROB\\d{2}\\.DBF$");
    private static final String EXTRACTED_FILES_FOLDER_NAME = "TEMP";
    private static final int FEDERAL_ADDRESS_CODE_FIELD_INDEX = 1;  // index of federal address code in database record
    private static final int ADDRESS_CODE_FIELD_INDEX = 8; // index of address code in database record
    private static final int PACKAGE_PROCESSING_SIZE = 5000;
    private static final int THREAD_POOL_SIZE = 8;

    private final SettlementRepository settlementRepository;
    private final StreetRepository streetRepository;
    private final String fullPathArchive;
    private final File destinationFolder;
    private final BatchAddressService batchAddressService;

    public DbfProcessingService(AddressesProcessingApplicationProperties applicationProperties,
                                SettlementRepository settlementRepository,
                                StreetRepository streetRepository,
                                BatchAddressService batchAddressService) {
        this.settlementRepository = settlementRepository;
        this.streetRepository = streetRepository;
        this.fullPathArchive = applicationProperties.getAddressFilePath()
                + applicationProperties.getAddressFileName();
        this.destinationFolder = new File(applicationProperties.getAddressFilePath()
                + EXTRACTED_FILES_FOLDER_NAME);
        this.batchAddressService = batchAddressService;
    }

    @Async
    public void process() {
        extractFiles(fullPathArchive, destinationFolder);
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Future<String>> results = new ArrayList<>();
        logger.info("Start processing files...");
        try {
            List<File> filesInFolder = Files.walk(Paths.get(destinationFolder.getAbsolutePath()))
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .collect(Collectors.toList());
            for (File file : filesInFolder) {
                Future<String> result = executorService.submit(processFile(file));
                results.add(result);
            }
        } catch (IOException e) {
            logger.error("File processing error");
        }
        for (Future<String> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                logger.error("Couldn't process file: " + result);
            }
        }
        logger.info("All files are processed!");
    }

    /**
     * Searching files into zip archive and put in the temporary folder
     */
    private void extractFiles(String fullPathArchive, File destinationFolder) {
        prepareExtractedFilesFolder(destinationFolder);
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(fullPathArchive))) {
            for (ZipEntry zipEntry; (zipEntry = zipInputStream.getNextEntry()) != null; ) {
                String currentFileName = zipEntry.getName();
                if (ALLOWABLE_FILE_NAME_PATTERN.matcher(currentFileName).find()) {
                    Files.copy(zipInputStream, Paths.get(destinationFolder + "/" + currentFileName));
                    logger.info("Extracted: " + currentFileName + " from archive");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Processing one extracted file
     */
    private Callable<String> processFile(File file) {
        return new Callable() {
            @Override
            public String call() {
                AtomicInteger processedDictionaryRecordCount = new AtomicInteger();
                List<Street> streets = new ArrayList<>();
                List<Settlement> settlements = new ArrayList<>();
                try (InputStream inputStream = new FileInputStream(file)) {
                    DBFReader reader = new DBFReader(inputStream);
                    reader.setCharactersetName(IMPORTED_FILE_ENCODING);
                    logger.info("Processing file: " + file.getName());
                    int recordCount = reader.getRecordCount();
                    logger.info("Need to process: " + recordCount + " records");
                    for (int i = 0; i < recordCount; i++) {
                        if (i % PACKAGE_PROCESSING_SIZE == 0) {
                            // save both collections of entities to DB and clear
                            batchAddressService.store(settlements, streets);
                        }
                        ExternalAddressModel externalAddressModel = loadNextAddressData(reader);
                        findByAddressCodeInDictionaries(
                                processedDictionaryRecordCount,
                                externalAddressModel,
                                streets,
                                settlements);
                    }
                    // save rest collections of entities to BD and clear
                    batchAddressService.store(settlements, streets);
                    logger.info("Processed " + recordCount + " DBF records " +
                            "(" + processedDictionaryRecordCount + " matches)");
                    file.deleteOnExit();
                } catch (IOException e) {
                    logger.error("Couldn't process file: " + file.getName());
                }
                return file.getName();
            }
        };
    }

    /**
     * Deleting files from temporary folder (if need) or create if not exist
     */
    private void prepareExtractedFilesFolder(File destinationFolder) {
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
     * Get next row from DBF (one by one)
     */
    private ExternalAddressModel loadNextAddressData(DBFReader reader) {
        try {
            Object[] objects = reader.nextRecord();
            return new ExternalAddressModel(
                    objects[FEDERAL_ADDRESS_CODE_FIELD_INDEX].toString().trim(),
                    objects[ADDRESS_CODE_FIELD_INDEX].toString().trim());
        } catch (DBFException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Searching row in dictionaries by address
     */
    private void findByAddressCodeInDictionaries(AtomicInteger processedDictionaryRecordCount,
                                                 ExternalAddressModel externalAddressModel,
                                                 List<Street> streets,
                                                 List<Settlement> settlements) {
        int federalAddressCodeLength = externalAddressModel.getAddressCode().length();
        if (federalAddressCodeLength == 17) {
            Street street =
                    streetRepository.getByAddressCode(externalAddressModel.getAddressCode());
            if (street != null) {
                addStreetEntityToCollection(
                        processedDictionaryRecordCount,
                        street,
                        externalAddressModel.getFederalAddressCode(),
                        streets);
            } else {
                // attempt to find by part address code (-2 last digits)
                streetRepository.findByAddressCode(
                        getPartOfAddressCode(externalAddressModel.getAddressCode()))
                        .forEach(e -> addStreetEntityToCollection(
                                processedDictionaryRecordCount,
                                e,
                                externalAddressModel.getFederalAddressCode(),
                                streets));
            }
        } else if (federalAddressCodeLength != 0) {
            Settlement settlement =
                    settlementRepository.getByAddressCode(externalAddressModel.getAddressCode());
            if (settlement != null) {
                addSettlementEntityToCollection(
                        processedDictionaryRecordCount,
                        settlement,
                        externalAddressModel.getFederalAddressCode(),
                        settlements);
            } else {
                // attempt to find by part address code (-2 last digits)
                settlementRepository.findByAddressCode(
                        getPartOfAddressCode(externalAddressModel.getAddressCode()))
                        .forEach(e -> addSettlementEntityToCollection(
                                processedDictionaryRecordCount,
                                e,
                                externalAddressModel.getFederalAddressCode(),
                                settlements));
            }
        }
    }

    /**
     * Setting federal address code to entity and addition to collection (for streets)
     */
    private void addStreetEntityToCollection(AtomicInteger processedDictionaryRecordCount,
                                             Street street,
                                             String federalAddressCode,
                                             List<Street> streets) {
        street.setFederalAddressCode(federalAddressCode);
        streets.add(street);
        processedDictionaryRecordCount.incrementAndGet();
    }

    /**
     * Setting federal address code to entity and addition to collection (for settlements)
     */
    private void addSettlementEntityToCollection(AtomicInteger processedDictionaryRecordCount,
                                                 Settlement settlement,
                                                 String federalAddressCode,
                                                 List<Settlement> settlements) {
        settlement.setFederalAddressCode(federalAddressCode);
        settlements.add(settlement);
        processedDictionaryRecordCount.incrementAndGet();
    }

    /**
     * Get part of address code (without 2 last digits) by full address code
     */
    private String getPartOfAddressCode(String fullAddressCode) {
        return fullAddressCode.substring(0, fullAddressCode.length() - 2);
    }
}
