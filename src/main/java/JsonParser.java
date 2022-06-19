import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import core.Line;
import core.Station;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonParser {
    private static StationIndex stationIndex;
    public static final String CSS_QUERY_LINE = "[data-depend]";
    public static final String CSS_QUERY_STATIONS_NUMBER = "span.num";
    public static final String CSS_QUERY_STATIONS_NAME = "span.name";
    public static final String REGEX_LINE_NUMBER = "data-line=\"(.+)\"";
    public static final String CSS_QUERY_CONNECTIONS = "span";
    public static final String REGEX_CONNECTIONS = "span class=\"t\\-icon\\-metroln ln\\-(.+?)(\" title=\"переход на станцию «)(.+?)»";
    private static List<String> linesNumbersList;
    private static List<String> linesNamesList;
    private static List<Integer> stationsNumbersList;
    private static List<String> stationNamesList;
    private static Elements connectionsListElements;
    private static List<String> connectionsListString;
    private static Map<String, Line> lines = new HashMap<>();
    private static TreeSet<Station> stations = new TreeSet<>();
    private static Map<Station, TreeSet<Station>> connections = new TreeMap<>();

    public static String getStationIndex() {
        getMetroData();
        stationIndex = new StationIndex(lines, stations, connections);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(stationIndex);
        return json;
    }

    public static void getMetroData() {
        linesNamesList = Main.webSite.select(CSS_QUERY_LINE).stream().map(Element::text).toList();

        Elements linesNumberElements = Main.webSite.select(CSS_QUERY_LINE);
        List<String> linesNumberTemp = new ArrayList<>();
        for (Element linesNumberElement : linesNumberElements) {
            Pattern pLineNumber = Pattern.compile(REGEX_LINE_NUMBER);
            Matcher mLineNumber = pLineNumber.matcher(linesNumberElement.toString());
            mLineNumber.find();
            linesNumberTemp.add(mLineNumber.group(1));
        }
        setLinesNumbersList(linesNumberTemp);

        stationsNumbersList = Main.webSite.select(CSS_QUERY_STATIONS_NUMBER).stream().map(Element::text).
                map(text -> text.replaceAll("\\.", "")).
                map(String::trim).mapToInt(Integer::parseInt).boxed().toList();

        stationNamesList = Main.webSite.select(CSS_QUERY_STATIONS_NAME).stream().map(Element::text).toList();

        connectionsListElements = Main.webSite.select(CSS_QUERY_CONNECTIONS);
        connectionsListString = connectionsListElements.stream().map(Node::toString).toList();

        getLines();
        getStations();
        getConnections();
    }

    private static void getLines() {
        for (int i = 0; i < linesNamesList.size(); i++) {
            lines.put(linesNumbersList.get(i), new Line(linesNumbersList.get(i), linesNamesList.get(i)));
        }
    }

    private static void getStations() {
        int j = 0;
        for (int i = 0; i < stationsNumbersList.size(); i++) {
            if (stationsNumbersList.get(i) >= stationsNumbersList.get(i == 0 ? i : i - 1)) {
                stations.add(new Station(stationNamesList.get(i), new Line(linesNumbersList.get(j), linesNamesList.get(j))));
            } else if (stationsNumbersList.get(i) < stationsNumbersList.get(i - 1)) {
                j++;
                stations.add(new Station(stationNamesList.get(i), new Line(linesNumbersList.get(j), linesNamesList.get(j))));
            }
        }
    }

    private static void getConnections() {
        List<Station> connectedStations = new ArrayList<>();
        String currentLineName = "";
        String currentLineNumber = "";
        String currentStationName = "";
        for (int i = 0; i < connectionsListElements.size(); i++) {
            if (linesNamesList.contains(connectionsListElements.get(i).text())) {
                currentLineName = connectionsListElements.get(i).text();
                currentLineNumber = linesNumbersList.get(linesNamesList.indexOf(currentLineName));
            }
            if (stationNamesList.contains(connectionsListElements.get(i).text())) {
                currentStationName = connectionsListElements.get(i).text();
            }
            Pattern p = Pattern.compile(REGEX_CONNECTIONS);
            Matcher mCurrent = p.matcher(connectionsListString.get(i));
            Matcher mPrevious = p.matcher(connectionsListString.get(i == 0 ? i : i - 1));
            boolean previous = mPrevious.find();
            boolean current = mCurrent.find();
            if (current && !previous) {
                connectedStations.add(new Station(currentStationName, lines.get(currentLineNumber)));
                connectedStations.add(new Station(mCurrent.group(3), lines.get(mCurrent.group(1))));
            } else if (current && previous) {
                connectedStations.add(new Station(mCurrent.group(3), lines.get(mCurrent.group(1))));
            } else if (!current && previous) {
                addConnection(connectedStations);
                connectedStations.clear();
            }
        }
    }

    private static void addConnection(List<Station> stations) {
        for (Station station : stations) {
            if (!connections.containsKey(station)) {
                connections.put(station, new TreeSet<>());
            }
            TreeSet<Station> connectedStations = connections.get(station);
            connectedStations.addAll(stations.stream()
                    .filter(s -> !s.equals(station)).toList());
        }
    }

    public static void getMoscowMetroInfo() {
        System.out.println("Количество станций метро по линиям:");
        for (int i = 0; i < linesNamesList.size(); i++) {
            System.out.println("Линия " + linesNamesList.get(i) + " : "
                    + stationIndex.getLine(linesNumbersList.get(i)).getStations().size() + " станций");
        }
    }

    public static void getMoscowMetroConnectionsInfo() {
        int connectionsCount = 0;
        for (Station station : stationIndex.getStations()) {
            if (stationIndex.getConnections().containsKey(station)) {
                connectionsCount++;
            }
        }
        System.out.println("Количество переходов в Московском метро: " + connectionsCount);
    }

    public static void createStationIndex() {
        stationIndex = new StationIndex();
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonData = (JSONObject) parser.parse(getJsonFile());

            JSONArray linesArray = (JSONArray) jsonData.get("lines");
            parseLines(linesArray);

            JSONObject stationsObject = (JSONObject) jsonData.get("stations");
            parseStations(stationsObject);

            JSONArray connectionsArray = (JSONArray) jsonData.get("connections");
            parseConnections(connectionsArray);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static String getJsonFile() {
        StringBuilder builder = new StringBuilder();
        try {
            List<String> lines = Files.readAllLines(Paths.get(Main.MOSCOW_METRO), StandardCharsets.UTF_8);
            lines.forEach(builder::append);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return builder.toString();
    }

    private static void parseLines(JSONArray linesArray) {
        linesArray.forEach(lineObject -> {
            JSONObject lineJsonObject = (JSONObject) lineObject;
            Line line = new Line(
                    (String) lineJsonObject.get("number"),
                    (String) lineJsonObject.get("name")
            );
            stationIndex.addLine(line);
        });
    }

    private static void parseStations(JSONObject stationsObject) {
        stationsObject.keySet().forEach(lineNumberObject ->
        {
            String lineNumber = (String) lineNumberObject;
            Line line = stationIndex.getLine(lineNumber);
            JSONArray stationsArray = (JSONArray) stationsObject.get(lineNumberObject);
            stationsArray.forEach(stationObject ->
            {
                Station station = new Station((String) stationObject, line);
                stationIndex.addStation(station);
                line.addStation(station);
            });
        });
    }

    private static void parseConnections(JSONArray connectionsArray) {
        connectionsArray.forEach(connectionObject ->
        {
            JSONArray connection = (JSONArray) connectionObject;
            List<Station> connectionStations = new ArrayList<>();
            connection.forEach(item ->
            {
                JSONObject itemObject = (JSONObject) item;
                String lineNumber = (String) itemObject.get("line");
                String stationName = (String) itemObject.get("station");

                Station station = stationIndex.getStation(stationName, lineNumber);
                if (station == null) {
                    throw new IllegalArgumentException("core.Station " +
                            stationName + " on line " + lineNumber + " not found");
                }
                connectionStations.add(station);
            });
            stationIndex.addConnection(connectionStations);
        });
    }

    public static void setLinesNumbersList(List<String> list) {
        linesNumbersList = list;
    }
}
