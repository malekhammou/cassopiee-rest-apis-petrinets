package splitminerapi.example.v1.controllers;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import rest.api.MinePetrinetWithSplitMiner;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RestController
@RequestMapping("/api")
public class HelloController {

    private final MinePetrinetWithSplitMiner petrinetGenerator;

    public HelloController(MinePetrinetWithSplitMiner petrinetGenerator) {
        this.petrinetGenerator = petrinetGenerator;
    }

    @GetMapping("/welcome")
    @ResponseBody
    public String sayHello() {
        return "Welcome to split miner API";
    }
    @GetMapping("/petrinet")
    public ResponseEntity<ByteArrayResource> generatePetrinetImage() {
        BufferedImage petriNetImage = MinePetrinetWithSplitMiner.generatePetrinet();

        if (petriNetImage != null) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(petriNetImage, "png", baos);
                byte[] imageBytes = baos.toByteArray();

                ByteArrayResource resource = new ByteArrayResource(imageBytes);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.IMAGE_PNG);
                headers.setContentLength(imageBytes.length);

                return ResponseEntity.ok()
                        .headers(headers)
                        .body(resource);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return ResponseEntity.notFound().build();
    }




 
}
