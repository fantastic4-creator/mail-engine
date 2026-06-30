package com.mailengine.api;

import com.mailengine.config.MailEngineRuntimeProperties;
import java.util.Arrays;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RuntimeController {

    private final Environment environment;
    private final MailEngineRuntimeProperties runtimeProperties;

    public RuntimeController(Environment environment, MailEngineRuntimeProperties runtimeProperties) {
        this.environment = environment;
        this.runtimeProperties = runtimeProperties;
    }

    @GetMapping("/api/runtime")
    public Map<String, Object> runtime() {
        return Map.of(
                "profiles", Arrays.asList(environment.getActiveProfiles()),
                "deliveryMode", runtimeProperties.getDeliveryMode().name(),
                "storageMode", runtimeProperties.getStorageMode(),
                "fromLocalPart", runtimeProperties.getFromLocalPart(),
                "dkimSelector", runtimeProperties.getDkimSelector()
        );
    }
}
