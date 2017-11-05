package at.gridgears;

import at.gridgears.held.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.cli.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;

public class HeldClient {

    private Held held;
    private FindLocationCallback callback;
    private String lastCommand = "help";

    public static void main(String[] args) throws IOException {
        Options options = new Options();
        options.addOption("u", true, "uri used for connection");
        options.addOption("h", true, "headerName:headerValue");

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            String[] headers = cmd.getOptionValues("h");
            new HeldClient().start(cmd.getOptionValue("u"), (headers != null ? headers : new String[0]));
        } catch (ParseException e) {
            System.out.println("Unexpected exception:" + e.getMessage());
            System.exit(1);
        }
    }

    @SuppressFBWarnings("DM_EXIT")
    private void start(String uri, String[] customHeaders) throws IOException {
        HeldBuilder heldBuilder = new HeldBuilder().withURI(uri);

        Arrays.stream(customHeaders).forEach(header -> {
            String[] headerSplit = header.split(":");
            heldBuilder.withHeader(headerSplit[0], headerSplit.length > 0 ? headerSplit[1] : null);
        });

        held = heldBuilder.build();

        callback = new Callback();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()))) {
            System.out.println("");
            System.out.println("");
            System.out.println("*********************************");
            System.out.println("GridGears HELD Commandline Client");
            System.out.println("*********************************");
            printHelp();

            boolean keepRunning = true;
            while (keepRunning) {
                System.out.print("> ");
                String input = reader.readLine();
                if (input != null) {
                    keepRunning = executeCommand(input);
                    if (!input.equals("last")) {
                        lastCommand = input;
                    }
                } else {
                    break;
                }
            }
            System.exit(0);
        }
    }

    private void printHelp() {
        System.out.println("");
        System.out.println("held [IDENTIFIER]\tExecute HELD request for the given identifier");
        System.out.println("last\t\t\tRepeat the last request");
        System.out.println("help\t\t\tPrint help");
        System.out.println("quit\t\t\tQuit");

        System.out.println("");
    }

    private boolean executeCommand(String input) {
        boolean keepRunning = true;
        switch (input.split(" ")[0]) {
            case "held":
                held.findLocation(input.split(" ")[1], callback);
                break;
            case "quit":
                keepRunning = false;
                break;
            case "last":
                executeCommand(lastCommand);
                break;
            case "help":
                printHelp();
                break;
            default:
                System.out.println("Unknown command");
        }
        return keepRunning;
    }

    private static URI createGoogleMapsUri(Location location) {
        return URI.create("https://www.google.com/maps/?q=" + location.getLatitude() + ',' + location.getLongitude());
    }

    private static class Callback implements FindLocationCallback {
        @Override
        public void success(LocationResult locationResult) {
            if (locationResult.hasLocations()) {
                System.out.println();
                System.out.format("Received locations for %s%n", locationResult.getIdentifier());
                locationResult.getLocations().forEach(loc -> System.out.format("\t%s%n", formatLocation(loc)));
            } else {
                System.out.println();
                System.out.format("Received error response for %s%n\t%s: %s", locationResult.getIdentifier(), locationResult.getStatus().getStatusCode(), locationResult.getStatus().getMessage());
            }
            System.out.println();
            System.out.print("> ");
        }

        private String formatLocation(Location location) {
            return "latitude: " + location.getLatitude()
                    + "\t longitude: " + location.getLongitude()
                    + (location.getRadius() != 0.0 ? "\tradius: " + location.getRadius() : "")
                    + "\t" + createGoogleMapsUri(location).toString();
        }

        @Override
        public void failed(Exception exception) {
            System.out.format("exception occurred");
            exception.printStackTrace(System.out);
        }
    }
}
