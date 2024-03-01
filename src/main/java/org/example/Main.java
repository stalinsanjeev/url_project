package org.example;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigtable.admin.v2.BigtableInstanceAdminClient;
import com.google.cloud.bigtable.admin.v2.models.Instance;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.models.Mutation;
import com.google.cloud.bigtable.data.v2.models.Query;
import com.google.cloud.bigtable.data.v2.models.Row;
import com.google.cloud.bigtable.data.v2.models.RowMutation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import com.google.cloud.bigtable.data.v2.models.Filters;


import java.util.List;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.models.Query;
import com.google.cloud.bigtable.data.v2.models.Row;
import com.google.cloud.bigtable.data.v2.models.RowCell;

import java.io.IOException;



public class Main {

    private static final String ROW_KEY_PREFIX = "row";
    private static void printRow(Row row) {
        if (row == null) {
            return;
        }
        System.out.printf("Reading data for %s%n", row.getKey().toStringUtf8());
        String colFamily = "";
        for (RowCell cell : row.getCells()) {
            if (!cell.getFamily().equals(colFamily)) {
                colFamily = cell.getFamily();
                System.out.printf("Column Family %s%n", colFamily);
            }

            System.out.printf("\t%s: %s%n",
                    cell.getQualifier().toStringUtf8(),
                    cell.getValue().toStringUtf8());



        }
        System.out.println();

    }

    public  static String insertUrlData( BigtableDataClient client,String tableId, String longUrl) {
        String shortUrl = null;
        try {

            String id = generateShortId(longUrl);
            String createdAt = Instant.now().toString();
            shortUrl = "http://short.url/" + id;

            RowMutation mutation = RowMutation.create(tableId, id)
                    .setCell("url_data", "id", id)
                    .setCell("url_data", "created_at", createdAt)
                    .setCell("url_data", "short_url", shortUrl)
                    .setCell("url_data", "long_url", longUrl);

            client.mutateRow(mutation);


            System.out.println("Rows after insertion:");
            Query query = Query.create(tableId).rowKey(id).filter(Filters.FILTERS.limit().cellsPerColumn(1));
            for (Row row : client.readRows(query)) {
                printRow(row);
            }



        } catch (Exception e) {
            System.err.println("Exception encountered while inserting URL data: " + e.getMessage());
            e.printStackTrace();
        }
        return shortUrl;

    }

    private static String generateShortId(String longUrl) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(longUrl.getBytes(StandardCharsets.UTF_8));
            // Use a portion of the hash to keep the URL short
            return bytesToHex(hash).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static String getLongUrl(BigtableDataClient client, String tableId, String shortUrl) {
        // Extract the ID from the short URL
        String id = shortUrl;
        String longUrl = null;

        // Create a query to fetch the row with the corresponding ID
        Query query1 = Query.create(tableId).rowKey(id).filter(Filters.FILTERS.limit().cellsPerColumn(1));

        // Execute the query
        for (Row row : client.readRows(query1)) {
            printRow(row);
            System.out.println("\n");

            if (row != null) {
                // Assuming 'long_url' is the column qualifier for the long URL
                longUrl = row.getCells("url_data", "long_url").get(0).getValue().toStringUtf8();

            } else {
                System.out.println("No data found for the given short URL: " + shortUrl);
                longUrl =  null;
            }

        }

        return longUrl;
    }

    public static void getTable(BigtableDataClient client, String tableId){

        Query query = Query.create(tableId).limit(26);

        for (Row row : client.readRows(query)) {
        printRow(row);
        System.out.println("\n");

        }
    }

    public static void clearTable(BigtableDataClient client, String tableId) {
        try {
            Query query = Query.create(tableId);
            for (Row row : client.readRows(query)) {
                RowMutation mutation = RowMutation.create(tableId, row.getKey().toStringUtf8())
                        .deleteRow();
                client.mutateRow(mutation);
            }
            System.out.println("All rows deleted from table: " + tableId);
        } catch (Exception e) {
            System.err.println("Exception encountered while clearing the table: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String getLongUrl1(BigtableDataClient client, String tableId, String shortUrl) {
        // Extract the ID from the short URL
        String[] parts = shortUrl.split("/");

        // The ID is the last part of the URL
        String id = parts[parts.length - 1];


        String longUrl = null;

        // Create a query to fetch the row with the corresponding ID
        Query query = Query.create(tableId).rowKey(id).filter(Filters.FILTERS.limit().cellsPerColumn(1));

        // Execute the query
        for (Row row : client.readRows(query)) {
            printRow(row);
            System.out.println("\n");

            // Assuming 'long_url' is the column qualifier for the long URL
            if (row != null && row.getCells("url_data", "long_url").size() > 0) {
                longUrl = row.getCells("url_data", "long_url").get(0).getValue().toStringUtf8();
                break; // Exit the loop once the longUrl is found
            }
        }

        if (longUrl == null) {
            System.out.println("No data found for the given short URL: " + shortUrl);
        }

        return longUrl;
    }






    public static void main(String[] args) throws IOException {

        String projectId = "rice-comp-539-spring-2022";
        String instanceId = "shared-539";
        String tableId = "spring24-team3-quiny";


        BigtableDataClient client = BigtableDataClient.create(projectId, instanceId);

        try {

            String shortu = null;
            shortu = insertUrlData(client,tableId,"https://maven.apache.org/ref/2.2.1/maven-model/men.html");
            System.out.println(shortu);
            String shortu1 = insertUrlData(client,tableId,"https://www.youtube.com/watch?v=GFM9-Mq2Fuw");
            getTable(client,tableId);
            String longu = getLongUrl1(client,tableId,"http://short.url/6184df79");
            System.out.println(longu);
            clearTable(client,tableId);



            //close client
            client.close();

        } catch (Exception e) {

        }





    }
}
