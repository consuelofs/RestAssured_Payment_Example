package config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class ConfigReader {
    Config config;
    ObjectMapper objectMapper = new ObjectMapper();
    File file = new File("config.json");

    {
        try {
            config = objectMapper.readValue(file,Config.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Config getConfig()
    {
        return config;
    }

}
