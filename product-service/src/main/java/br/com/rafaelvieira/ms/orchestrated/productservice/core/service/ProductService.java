package br.com.rafaelvieira.ms.orchestrated.productservice.core.service;

import br.com.rafaelvieira.ms.orchestrated.productservice.config.exception.ValidationException;
import br.com.rafaelvieira.ms.orchestrated.productservice.core.domain.Validation;
import br.com.rafaelvieira.ms.orchestrated.productservice.core.dto.Event;
import br.com.rafaelvieira.ms.orchestrated.productservice.core.dto.History;
import br.com.rafaelvieira.ms.orchestrated.productservice.core.dto.OrderProducts;
import br.com.rafaelvieira.ms.orchestrated.productservice.core.producer.KafkaProducer;
import br.com.rafaelvieira.ms.orchestrated.productservice.core.repository.ProductRepository;
import br.com.rafaelvieira.ms.orchestrated.productservice.core.repository.ValidationRepository;
import br.com.rafaelvieira.ms.orchestrated.productservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static br.com.rafaelvieira.ms.orchestrated.productservice.core.enums.ESagaStatus.*;
import static org.springframework.util.ObjectUtils.isEmpty;

@Slf4j
@Service
@AllArgsConstructor
public class ProductService {

    private static final String CURRENT_SOURCE = "PRODUCT_SERVICE";

    private final JsonUtil jsonUtil;
    private final KafkaProducer producer;
    private final ProductRepository productRepository;
    private final ValidationRepository validationRepository;

    public void validateExistingProducts(Event event) {
        try {
            checkCurrentValidation(event);
            createValidation(event, true);
            handleSuccess(event);
        } catch (Exception ex) {
            log.error("Error trying to validate product: ", ex);
            handleFailCurrentNotExecuted(event, ex.getMessage());
        }
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void validateProductsInformed(Event event) {
        if (isEmpty(event.getPayload()) || isEmpty(event.getPayload().getProducts())) {
            throw new ValidationException("Product list is empty!");
        }
        if (isEmpty(event.getPayload().getId()) || isEmpty(event.getTransactionId())) {
            throw new ValidationException("OrderID and TransactionID must be informed!");
        }
    }

    private void checkCurrentValidation(Event event) {
        validateProductsInformed(event);
        if (Boolean.TRUE.equals(validationRepository.existsByOrderIdAndTransactionId(
                event.getOrderId(), event.getTransactionId()))) {
            throw new ValidationException("There's another transactionId for this validation.");
        }
        event.getPayload().getProducts().forEach(product -> {
            validateProductInformed(product);
            validateExistingProduct(product.getProduct().getCode());
        });
    }

    private void validateProductInformed(OrderProducts product) {
        if (isEmpty(product.getProduct()) || isEmpty(product.getProduct().getCode())) {
            throw new ValidationException("Product must be informed!");
        }
    }

    private void validateExistingProduct(String code) {
        if (Boolean.FALSE.equals(productRepository.existsByCode(code))) {
            throw new ValidationException("Product does not exists in database!");
        }
    }

    private void createValidation(Event event, boolean success) {
        var validation = Validation
                .builder()
                .orderId(event.getPayload().getId())
                .transactionId(event.getTransactionId())
                .success(success)
                .build();
        validationRepository.save(validation);
    }

    private void handleSuccess(Event event) {
        event.setStatus(SUCCESS);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Products are validated successfully!");
    }

    private void addHistory(Event event, String message) {
        var history = History
                .builder()
                .source(event.getSource())
                .status(event.getStatus())
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
        event.addToHistory(history);
    }

    private void handleFailCurrentNotExecuted(Event event, String message) {
        event.setStatus(ROLLBACK_PENDING);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Fail to validate products: ".concat(message));
    }

    public void rollbackEvent(Event event) {
        changeValidationToFail(event);
        event.setStatus(FAIL);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Rollback executed on product validation!");
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void changeValidationToFail(Event event) {
        validationRepository
                .findByOrderIdAndTransactionId(event.getOrderId(), event.getTransactionId())
                .ifPresentOrElse(validation -> {
                            validation.setSuccess(false);
                            validationRepository.save(validation);
                        },
                        () -> createValidation(event, false));
    }
}
