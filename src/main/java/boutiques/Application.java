package boutiques;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {

    
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        // Retrieve all tool descriptors (run `bosh.py search -m 1000`) to store them in ~/.cache/boutiques/zenodo-ID.json files
        BoutiquesUtils.updateToolDatabase();
    }
}
