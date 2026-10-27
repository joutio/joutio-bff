package com.joutio.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BFF Joutio — façade unique REST + WebSocket.
 *
 * <p>Agrège les services headless via les bus distribués Axon (query/command) et diffuse
 * les notifications temps réel (consommation Kafka, fan-out STOMP).</p>
 */
@SpringBootApplication
public class BffServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(BffServiceApplication.class, args);
    }
}
