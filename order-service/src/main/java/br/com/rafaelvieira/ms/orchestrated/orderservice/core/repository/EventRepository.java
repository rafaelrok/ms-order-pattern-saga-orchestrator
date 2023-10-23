package br.com.rafaelvieira.ms.orchestrated.orderservice.core.repository;

import br.com.rafaelvieira.ms.orchestrated.orderservice.core.document.Event;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends MongoRepository<Event, String> {

    List<Event> findAllByOrderByCreatedAtDesc();
    Optional<Event> findTopOneByOrderIdOrderByCreatedAtDesc(String orderId);
    Optional<Event> findTopOneByTransactionIdOrderByCreatedAtDesc(String transactionId);
}

