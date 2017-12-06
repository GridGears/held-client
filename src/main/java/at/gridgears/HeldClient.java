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
import java.util.List;

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
    private void start(String uri, String... customHeaders) throws IOException {
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
                    try {
                        keepRunning = executeCommand(input);
                        if (!input.equals("last")) {
                            lastCommand = input;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
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
                String identifier = input.split(" ")[1];
                held.findLocation(new FindLocationRequest(identifier), callback);
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
                break;
        }
        return keepRunning;
    }

    private static class Callback implements FindLocationCallback {
        @Override
        public void completed(FindLocationRequest request, FindLocationResult findLocationResult) {
            printResultHeader(request);
            FindLocationResult.Status status = findLocationResult.getStatus();
            switch (status) {
                case FOUND:
                    printFoundResult(findLocationResult);
                    break;
                case NOT_FOUND:
                    printNotFoundResult(findLocationResult);
                    break;
                case ERROR: {
                    printErrorResult(findLocationResult);
                    break;
                }
                default: {
                    printUnknownStatus(status);
                    break;
                }
            }
            printResultFooter();
        }

        private void printErrorResult(FindLocationResult findLocationResult) {
            System.out.println("Failure:");
            FindLocationError findLocationError = findLocationResult.getError().get();
            System.out.println("\t" + findLocationError.getCode() + ": " + findLocationError.getMessage());
        }

        private void printNotFoundResult(FindLocationResult findLocationResult) {
            System.out.println("Not found:");
            FindLocationError findLocationError = findLocationResult.getError().get();
            System.out.println("\t" + findLocationError.getCode() + ": " + findLocationError.getMessage());
        }

        private void printFoundResult(FindLocationResult findLocationResult) {
            System.out.println("Location:");
            List<Location> locations = findLocationResult.getLocations();
            locations.forEach(loc -> {
                System.out.println("\t\tlat: " + loc.getLatitude());
                System.out.println("\t\tlon: " + loc.getLongitude());
                System.out.println("\t\tmap: " + createGoogleMapsUri(loc));
                System.out.println();
            });
        }

        private void printUnknownStatus(FindLocationResult.Status status) {
            System.out.println("Unknown result status: "+status.name());
        }

        @Override
        public void failed(FindLocationRequest request, Exception exception) {
            printResultHeader(request);
            System.out.println("Error occurred:");
            System.out.println("\t" + exception.getMessage());
            printResultFooter();
        }

        private void printResultFooter() {
            System.out.println("*********************************************************");
            System.out.println();
            System.out.print("> ");
        }

        private void printResultHeader(FindLocationRequest request) {
            System.out.println();
            System.out.println();
            System.out.println("QUERY RESULT*********************************************");
            System.out.println("Query:");
            System.out.println("\tIdentifier:\t" + request.getIdentifier());
            System.out.println();
        }


        private static URI createGoogleMapsUri(Location location) {
            return URI.create("https://www.google.com/maps/?q=" + location.getLatitude() + ',' + location.getLongitude());
        }
    }

}
