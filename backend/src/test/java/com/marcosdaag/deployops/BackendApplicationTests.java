package com.marcosdaag.deployops;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requiere base de datos PostgreSQL activa")
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
