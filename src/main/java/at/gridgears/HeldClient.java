package at.gridgears;

import at.gridgears.held.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.*;

@SuppressWarnings("PMD.SingularField")
public class HeldClient {
    private final List<FindLocationRequest.LocationType> locationTypes = new LinkedList<>();
    private final Map<FindLocationRequest, Long> requestTimestamps = new HashMap<>();

    private Held held;
    private FindLocationCallback callback;
    private String lastCommand = "help";
    private URI lastReference;
    private boolean verbose;
    private boolean exact;

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
            heldBuilder.withHeader(headerSplit[0], headerSplit.length > 1 ? headerSplit[1] : null);
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
        System.out.println();
        System.out.println("exact [true/false]\tSet the exact parameter");
        System.out.println("types [geo,civ,ref]\tSet the types, space separated");
        System.out.println("held [IDENTIFIER]\tExecute HELD request for the given identifier");
        System.out.println("last\t\t\t\tRepeat the last request");
        System.out.println("deref\t\t\t\tDerefenerce the last received location URI");
        System.out.println("deref [uri]\t\t\tDerefenerce the location URI");
        System.out.println("verbose [on/off]\tPrint raw response");
        System.out.println("help\t\t\t\tPrint help");
        System.out.println("quit\t\t\t\tQuit");

        System.out.println();
    }

    private boolean executeCommand(String input) {
        boolean keepRunning = true;
        String[] split = input.split(" ");
        String[] parameters = split.length > 1 ? Arrays.copyOfRange(split, 1, split.length) : new String[0];
        switch (split[0]) {
            case "held":
                lastCommand = input;
                String identifier = parameters[0];

                FindLocationRequest request = new FindLocationRequest(identifier, locationTypes, exact);
                requestTimestamps.put(request, System.nanoTime());
                held.findLocation(request, callback);
                break;
            case "verbose": {
                verbose = parameters.length == 0 || (parameters[0].equals("on"));
                System.out.println("verbose is " + verbose);
                break;
            }
            case "exact": {
                exact = parameters.length == 0 || (parameters[0].equals("true"));
                break;
            }
            case "types": {
                locationTypes.clear();
                for (int i = 0; i < parameters.length; i++) {
                    switch (parameters[i]) {
                        case "geo":
                            locationTypes.add(FindLocationRequest.LocationType.GEODETIC);
                            break;
                        case "civ":
                            locationTypes.add(FindLocationRequest.LocationType.CIVIC);
                            break;
                        case "ref":
                            locationTypes.add(FindLocationRequest.LocationType.LOCATION_URI);
                            break;
                        default:
                            System.err.println("Unknown location type " + parameters[i]);
                            break;
                    }
                }
                break;
            }
            case "deref":
                if (parameters.length == 0) {
                    if (lastReference != null) {
                        dereference(lastReference);
                    }
                } else {
                    dereference(URI.create(parameters[0]));
                }
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

    private void dereference(URI lastReference) {
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        HttpUriRequest getRequest = new HttpGet(lastReference);
        getRequest.addHeader(HttpHeaders.ACCEPT, "application/pidf+xml");

        try (CloseableHttpResponse httpResponse = httpClient.execute(getRequest)) {
            String content = EntityUtils.toString(httpResponse.getEntity());

            int statusCode = httpResponse.getStatusLine().getStatusCode();
            System.out.println("Dereferenced\t" + lastReference);
            System.out.println("\tstatusCode:\t" + statusCode);
            System.out.println("\tcontent");
            System.out.println("-------");
            System.out.println(content);
            System.out.println("-------");
        } catch (IOException e) {
            System.err.println("Could not dereference " + lastReference);
            e.printStackTrace();
        }
    }

    private class Callback implements FindLocationCallback {
        @Override
        public void completed(FindLocationRequest request, FindLocationResult findLocationResult) {
            printResultHeader(request, findLocationResult);
            FindLocationResult.Status status = findLocationResult.getStatus();
            switch (status) {
                case FOUND:
                    printFoundResult(findLocationResult);
                    if (!findLocationResult.getLocationReferences().isEmpty()) {
                        lastReference = findLocationResult.getLocationReferences().get(findLocationResult.getLocationReferences().size() - 1).getUri();
                    }
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
            if (!findLocationResult.getLocationReferences().isEmpty()) {
                System.out.println("Location References:");
                findLocationResult.getLocationReferences().forEach(ref -> {
                    System.out.println("\t\tURI:\t\t" + ref.getUri().toASCIIString());
                    System.out.println("\t\tExpires:\t" + ref.getExpiration().toString());
                    System.out.println();
                });
            }

            if (!findLocationResult.getLocations().isEmpty()) {
                System.out.println("Locations:");
                findLocationResult.getLocations().forEach(loc -> {
                    System.out.println("\t\tlat: " + loc.getLatitude());
                    System.out.println("\t\tlon: " + loc.getLongitude());
                    if (loc.getRadius() != 0) {
                        System.out.println("\t\trad: " + loc.getRadius());
                    }
                    System.out.println("\t\tmap: " + createGoogleMapsUri(loc));
                    System.out.println();
                });
            }
        }

        private void printUnknownStatus(FindLocationResult.Status status) {
            System.out.println("Unknown result status: " + status.name());
        }

        @Override
        public void failed(FindLocationRequest request, Exception exception) {
            printResultHeader(request, null);
            System.out.println("Error occurred:");
            System.out.println("\t" + exception.getMessage());
            printResultFooter();
        }

        private void printResultFooter() {
            System.out.println("*********************************************************");
            System.out.println();
            System.out.print("> ");
        }

        private void printResultHeader(final FindLocationRequest request, @Nullable final FindLocationResult findLocationResult) {
            long executionTime = Duration.ofNanos(System.nanoTime() - requestTimestamps.remove(request)).toMillis();

            System.out.println();
            System.out.println();
            System.out.println("QUERY RESULT*********************************************");
            System.out.println();
            if (findLocationResult != null && verbose) {
                if (StringUtils.isNotEmpty(findLocationResult.getRawRequest())) {
                    System.out.println("Request");
                    System.out.println("----------------");
                    System.out.println(findLocationResult.getRawRequest());
                    System.out.println("----------------");
                    System.out.println();
                }
                if (StringUtils.isNotEmpty(findLocationResult.getRawResponse())) {
                    System.out.println("Response");
                    System.out.println("----------------");
                    System.out.println(findLocationResult.getRawResponse());
                    System.out.println("----------------");
                    System.out.println();
                }
                if (!findLocationResult.getLocationReferences().isEmpty()) {
                    System.out.println("References");
                    System.out.println("----------------");
                    findLocationResult.getLocationReferences().forEach(ref -> dereference(ref.getUri()));
                    System.out.println("----------------");
                }
            }

            System.out.println("Query:");
            System.out.println("\tIdentifier:\t\t" + request.getIdentifier());
            System.out.println("\tExecution Time:\t" + executionTime + "ms");
            System.out.println();
        }

        private URI createGoogleMapsUri(Location location) {
            return URI.create("https://www.google.com/maps/?q=" + location.getLatitude() + ',' + location.getLongitude());
        }
    }
}