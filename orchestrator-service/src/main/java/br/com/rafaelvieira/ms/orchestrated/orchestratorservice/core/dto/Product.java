package br.com.rafaelvieira.ms.orchestrated.orchestratorservice.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    private String code;
    private String unitValue;
}
