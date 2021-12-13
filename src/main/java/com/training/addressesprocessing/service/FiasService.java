package com.training.addressesprocessing.service;

import com.training.addressesprocessing.model.FromDbfFiasAndKladrModel;
import org.springframework.stereotype.Service;

@Service
public class FiasService {

    public FromDbfFiasAndKladrModel getFiadAndKladr() {
        // todo implement
        return new FromDbfFiasAndKladrModel("exampleFiasId", "2200000000000");
    }
}
