package com.google.mapsengine.tutorials;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.mapsengine.MapsEngine;
import com.google.api.services.mapsengine.MapsEngineScopes;
import com.google.api.services.mapsengine.model.Border;
import com.google.api.services.mapsengine.model.Color;
import com.google.api.services.mapsengine.model.Datasource;
import com.google.api.services.mapsengine.model.DisplayRule;
import com.google.api.services.mapsengine.model.Filter;
import com.google.api.services.mapsengine.model.IconStyle;
import com.google.api.services.mapsengine.model.Layer;
import com.google.api.services.mapsengine.model.Map;
import com.google.api.services.mapsengine.model.MapItem;
import com.google.api.services.mapsengine.model.MapLayer;
import com.google.api.services.mapsengine.model.Permission;
import com.google.api.services.mapsengine.model.PermissionsBatchInsertRequest;
import com.google.api.services.mapsengine.model.PermissionsBatchInsertResponse;
import com.google.api.services.mapsengine.model.PointStyle;
import com.google.api.services.mapsengine.model.PublishResponse;
import com.google.api.services.mapsengine.model.ScaledShape;
import com.google.api.services.mapsengine.model.ScalingFunction;
import com.google.api.services.mapsengine.model.SizeRange;
import com.google.api.services.mapsengine.model.Table;
import com.google.api.services.mapsengine.model.ValueRange;
import com.google.api.services.mapsengine.model.VectorStyle;
import com.google.api.services.mapsengine.model.ZoomLevels;
import com.google.maps.clients.BackOffWhenRateLimitedRequestInitializer;
import com.google.maps.clients.HttpRequestInitializerPipeline;
import com.google.mapsengine.samples.auth.Utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Demonstrate uploading local CSV and VRT files into a new Maps Engine table,
 * creating a layer and map in the process.
 *
 * To get started, follow the instructions at
 * https://developers.google.com/maps-engine/documentation/oauth/serviceaccount#creating_a_service_account
 * to create a client ID and key. Generate and save a JSON key and save it in res/service_key.json.
 */
public class CsvUpload {

  private static final String APPLICATION_NAME = "Google/MapsEngineCsvUpload-1.0";
  private static final Collection<String> SCOPES = Arrays.asList(MapsEngineScopes.MAPSENGINE);

  private MapsEngine engine;

  private final HttpTransport httpTransport = new NetHttpTransport();
  private final JsonFactory jsonFactory = new GsonFactory();

  public static void main(String[] args) {
    try {
      new CsvUpload().run(args);
    } catch (Exception ex) {
      System.err.println("An unexpected error occurred!");
      ex.printStackTrace(System.err);
      System.exit(1);
    }
  }

  public void run(String[] args) throws Exception {

    if (args.length < 3) {
      System.err.println("Usage: java ...CsvUpload projectId myfile.csv myfile.vrt");
      System.err.println(" projectId is the numerical ID of the project in which to create the "
          + "new table");
      System.err.println(" myfile.csv is the path to the CSV file to upload");
      System.err.println(" myfile.vrt is the path to the VRT sidecar file to upload");
      System.err.println("  Check https://support.google.com/mapsengine/answer/3067502?hl=en for "
          + "more information on VRT files in Maps Engine.");
      System.exit(1);
    }
    String projectId = args[0];
    String csvFileName = args[1];
    String vrtFileName = args[2];

    System.out.println("Authorizing.");
    Credential credential = Utils.authorizeService(httpTransport, jsonFactory, SCOPES);
    System.out.println("Authorization successful!");

    // Set up the required initializers to 1) authenticate the request and 2) back off if we
    // start hitting the server too quickly.
    HttpRequestInitializer requestInitializers = new HttpRequestInitializerPipeline(
        Arrays.asList(credential, new BackOffWhenRateLimitedRequestInitializer()));

    // The MapsEngine object will be used to perform the requests.
    engine = new MapsEngine.Builder(httpTransport, jsonFactory, requestInitializers)
        .setApplicationName(APPLICATION_NAME)
        .build();

    System.out.println("Creating an empty table in Maps Engine, under project ID " + projectId);
    Table table = createTable(projectId, Arrays.asList(csvFileName, vrtFileName));
    System.out.println("Table created, ID is: " + table.getId());

    System.out.println("Uploading the data files.");
    uploadFile(table, csvFileName, "text/csv");
    uploadFile(table, vrtFileName, "text/plain"); // This mime type doesn't matter
    System.out.println("Done.");

    System.out.println("Creating a new layer.");
    Layer layer = createLayer(table);
    System.out.println("Layer created, ID is: " + layer.getId());

    System.out.println("Publishing layer.");
    publishLayer(layer);
    System.out.println("Done.");

    System.out.println("Creating a new map.");
    Map map = createMap(layer);
    System.out.println("Map created, ID is: " + map.getId());

    System.out.println("Publishing map.");
    publishMap(map);
    System.out.println("Done.");

    System.out.println("Setting permissions.");
    setPermissions(map);
    System.out.println("Done.");

    System.out.println("Publishing complete. You can view the map here: "
        + String.format("https://mapsengine.google.com/%s-4/mapview/?authuser=0", map.getId()));

  }

  /** Creates an empty table in your maps engine account. */
  private Table createTable(String projectId, List<String> fileNames) throws IOException {
    // Note that we need a com.google.api.services.mapsengine.model.File, not a java.io.File
    List<com.google.api.services.mapsengine.model.File> files = new ArrayList<>(fileNames.size());
    for (String fileName : fileNames) {
      files.add(new com.google.api.services.mapsengine.model.File().setFilename(fileName));
    }

    Table newTable = new Table()
        .setName("Population change")
        .setDescription("World population change")
        .setProjectId(projectId)
        .setFiles(files)
        .setTags(Arrays.asList("population growth", "population change"));

    return engine.tables().upload(newTable).execute();
  }

  /** Uploads the file data to the empty table. */
  private void uploadFile(Table table, String fileName, String contentType) throws IOException {
    // Load the file into a stream that we can send to the API
    File file = new File(fileName);
    InputStream fileInputStream = new BufferedInputStream(new FileInputStream(file));
    InputStreamContent contentStream = new InputStreamContent(contentType, fileInputStream);

    // Upload!
    engine.tables().files().insert(table.getId(), fileName, contentStream).execute();
  }

  /** Creates a layer using the table provided. */
  private Layer createLayer(Table table) throws IOException {
    ZoomLevels allZoomLevels = new ZoomLevels().setMin(0).setMax(24);

    DisplayRule positiveGrowth = new DisplayRule()
        .setZoomLevels(allZoomLevels)
        .setPointOptions(new PointStyle()
            .setIcon(new IconStyle()
                .setScaledShape(new ScaledShape()
                    .setShape("circle")
                    .setFill(new Color().setColor("blue").setOpacity(0.5))
                    .setBorder(new Border().setColor("blue").setWidth(1.0)))
                .setScalingFunction(new ScalingFunction()
                    .setColumn("POP_GROWTH")
                    .setSizeRange(new SizeRange().setMin(1.0).setMax(100.0))
                    .setValueRange(new ValueRange().setMin(0.0).setMax(9.6022000624)))))
            .setFilters(Arrays.asList(new Filter()
                .setColumn("POP_GROWTH")
                .setOperator(">")
                .setValue(0)));

    DisplayRule negativeGrowth = new DisplayRule()
        .setZoomLevels(allZoomLevels)
        .setPointOptions(new PointStyle()
            .setIcon(new IconStyle()
                .setScaledShape(new ScaledShape()
                    .setShape("circle")
                    .setFill(new Color().setColor("red").setOpacity(0.5))
                    .setBorder(new Border().setColor("red").setWidth(1.0)))
                .setScalingFunction(new ScalingFunction()
                    .setColumn("POP_GROWTH")
                    .setSizeRange(new SizeRange().setMin(1.0).setMax(100.0))
                    .setValueRange(new ValueRange().setMin(0.0).setMax(-9.6022000624)))))
    .setFilters(Arrays.asList(new Filter()
            .setColumn("POP_GROWTH")
            .setOperator("<")
            .setValue(0)));

    VectorStyle style = new VectorStyle()
        .setType("displayRule")
        .setDisplayRules(Arrays.asList(positiveGrowth, negativeGrowth));

    Layer newLayer = new Layer()
        .setLayerType("vector")
        .setName("Population Growth 2010")
        .setProjectId(table.getProjectId())
        .setDatasources(Arrays.asList(new Datasource().setId(table.getId())))
        .setStyle(style);

    return engine.layers().create(newLayer)
        .setProcess(true) // flag that this layer should be processed immediately
        .execute();
  }

  /** Publishes the given Layer */
  private PublishResponse publishLayer(Layer layer) throws IOException {
    return engine.layers().publish(layer.getId()).execute();
  }

  /** Creates a map using the layer provided */
  private Map createMap(Layer layer) throws IOException {
    Map newMap = new Map()
        .setName("Population growth (map1)")
        .setProjectId(layer.getProjectId());

    MapLayer mapLayer = new MapLayer()
        .setId(layer.getId())
        .setKey("map1-layer1");

    List<MapItem> layers = new ArrayList<>();
    layers.add(mapLayer);

    newMap.setContents(layers);

    // Map processing is triggered automatically, so no need to set a flag during creation.
    return engine.maps().create(newMap).execute();
  }

  /** Marks the provided map as "published", making it visible. */
  private PublishResponse publishMap(Map map) throws IOException {
    while (true) {
      try {
        // Initially the map will be in a 'processing' state and will return '400 Bad Request'
        // while processing is happening. Keep trying until it works.
        return engine.maps().publish(map.getId()).execute();
      } catch (GoogleJsonResponseException ex) {
        // Silently ignore the expected error.
      }
    }
  }

  /** Makes the map publicly visible. */
  private PermissionsBatchInsertResponse setPermissions(Map map) throws IOException {
    PermissionsBatchInsertRequest request = new PermissionsBatchInsertRequest()
        .setPermissions(Arrays.asList(new Permission()
            .setId("anyone")
            .setRole("viewer")));

    return engine.maps().permissions().batchInsert(map.getId(), request).execute();
  }
}