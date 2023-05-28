package rest.api;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/api/petrinet")
public class PetriNetResource {

    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getImage() {
        String imagePath = "C:/Users/Malek Hammou/eclipse-workspace/SequencePredictionWithPetriNets/petri_net.png";  // Path to your image file

        try {
            // Read the image file as a byte array
            Path path = (javax.ws.rs.Path) Paths.get(imagePath);
            byte[] imageBytes = Files.readAllBytes((java.nio.file.Path) path);

            // Set the response content type
            String contentType = MediaType.APPLICATION_OCTET_STREAM;

            // Set the response header to suggest downloading the file
            String contentDisposition = "attachment; filename=petrinet.png";

            // Build the response with the image byte array and appropriate headers
            return Response.ok(imageBytes, contentType)
                    .header("Content-Disposition", contentDisposition)
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
            return Response.serverError().build();
        }
    }
}