package net.amzscout.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.http.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate template;

    @Autowired
    private Environment env;

    @Test
    void run() {
        String path = "http://localhost:" + port + "/";

        List<String> ips = new ArrayList<>();
        ips.add("127.0.0.1");
        ips.add("127.0.0.2");
        ips.add("127.0.0.3");

        int max_requests = env.getRequiredProperty("throttled.max_requests", Integer.class);
        int expire_after_minutes = env.getRequiredProperty("throttled.expire_after_minutes", Integer.class);

        ExecutorService executor = Executors.newFixedThreadPool(20);
        try {
            for (String ip : ips) {

                for (int i = 0; i < max_requests; i++) {
                    executor.execute(() -> assertThat(makeRequest(path, ip) == HttpStatus.OK));
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }

        for (String ip : ips) {
            assertThat(makeRequest(path, ip) == HttpStatus.BAD_GATEWAY);
        }

        try {
            Thread.sleep(expire_after_minutes * 60 * 1000);
        } catch (
                InterruptedException e) {
            e.printStackTrace();
        }

        for (String ip : ips) {
            assertThat(makeRequest(path, ip) == HttpStatus.OK);
        }
    }


    private HttpStatus makeRequest(String path, String ip) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", ip);
        ResponseEntity<String> response = template.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return response.getStatusCode();
    }

}
