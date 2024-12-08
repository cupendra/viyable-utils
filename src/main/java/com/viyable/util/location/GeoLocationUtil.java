package com.viyable.util.location;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GeoLocationUtil {

    private static final String BASE_URL = "https://maps.googleapis.com/maps/api/geocode/json?";

    public static String getAddress(double latitude, double longitude, String apiKey) {
        String address = "";
        try{
            URL url = new URL(BASE_URL + "latlng=" + latitude + "," + longitude + "&key=" + apiKey);
            StringBuilder response = getStringBuilder(url);

            // Parse JSON response (implementation omitted for brevity)
            // You can use libraries like Jackson or Gson to parse the JSON response and extract the formatted address.
            address = response.toString();
            address = parseAddressFromJSON(address);
        }catch(IOException ex){
            ex.printStackTrace();
        }

        return address;
    }



    private static String parseAddressFromJSON(String jsonResponse) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readValue(jsonResponse, JsonNode.class);

        // Check for successful results
        if (!rootNode.path("status").asText().equals("OK")) {
            return "Failed to parse address: " + rootNode.path("status").asText();
        }

        // Get the first result (assuming there's only one relevant address)
        JsonNode addressNode = rootNode.path("results").get(0);

        // Extract components
        String formattedAddress = addressNode.path("formatted_address").asText();
        String locality = "";
        String adminArea = "";
        String country = "";
        String postalCode = "";

        // Loop through address components to find specific details
        for (JsonNode componentNode : addressNode.path("address_components")) {
            String componentType = componentNode.path("types").get(0).asText();
            String componentValue = componentNode.path("long_name").asText();

            switch (componentType) {
                case "locality":
                    locality = componentValue;
                    break;
                case "administrative_area_level_1": // Often refers to state/province
                    adminArea = componentValue;
                    break;
                case "country":
                    country = componentValue;
                    break;
                case "postal_code":
                    postalCode = componentValue;
                    break;
            }
        }

        // Format and return the extracted details
        /*String addressInfo = "Extracted address details:\n";
        addressInfo += "  Formatted Address: " + formattedAddress + "\n";
        addressInfo += "  Locality: " + locality + "\n";
        addressInfo += "  Administrative Area: " + adminArea + "\n";
        addressInfo += "  Country: " + country + "\n";
        addressInfo += "  Postal Code: " + postalCode + "\n";*/

        String addressInfo = "";
        addressInfo += locality + ", " + adminArea + ", " + country + ", " + postalCode;

        return addressInfo;
    }

    private static StringBuilder getStringBuilder(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Check for successful response
        if (connection.getResponseCode() != 200) {
            throw new IOException("Failed to get address: " + connection.getResponseMessage());
        }

        // Read response data
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response;
    }

    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; // Distance in kilometers
    }

}
