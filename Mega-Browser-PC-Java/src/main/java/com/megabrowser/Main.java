package com.megabrowser;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main extends Application {

    private static final String HOME = "https://vikij462.github.io/neon-nova-karta/";

    private TabPane tabPane;
    private ListView<String> historyListView;

    private final Path TABS_FILE = Paths.get("tabs.txt");
    private final Path HISTORY_FILE = Paths.get("history.txt");

    private final Set<String> historySet = new LinkedHashSet<>();

    @Override
    public void start(Stage primaryStage) {
        System.setProperty("javafx.webprefs.persist", "true"); // persistent cookies at runtime

        primaryStage.setTitle("Mega Browser");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        // --- Top bar ---
        HBox topBar = new HBox();
        topBar.getStyleClass().add("top-bar");
        topBar.setPadding(new Insets(6, 8, 6, 8));
        topBar.setSpacing(8);

        Button btnBack = new Button("←");
        btnBack.getStyleClass().add("tab-button");

        Button btnNewTab = new Button("+");
        btnNewTab.getStyleClass().add("tab-button");

        TextField addressField = new TextField();
        addressField.setPromptText("Zadej URL nebo stiskni Enter");
        HBox.setHgrow(addressField, Priority.ALWAYS);

        topBar.getChildren().addAll(btnBack, btnNewTab, addressField);

        // --- Tabs ---
        tabPane = new TabPane();
        tabPane.getStyleClass().add("tab-pane");

        // Load tabs from file or create default tab
        List<String> savedTabs = loadTabs();
        if (savedTabs.isEmpty()) {
            createTab(HOME);
        } else {
            for (String url : savedTabs) {
                createTab(url);
            }
        }

        // --- History List + controls ---
        historyListView = new ListView<>();
        historyListView.setPrefWidth(250);
        historyListView.getStyleClass().add("history-list");

        Button btnToggleHistory = new Button("Hide History");
        Button btnClearHistory = new Button("Clear History");
        btnToggleHistory.setMaxWidth(Double.MAX_VALUE);
        btnClearHistory.setMaxWidth(Double.MAX_VALUE);

        VBox historyBox = new VBox(5, btnToggleHistory, btnClearHistory, historyListView);
        historyBox.setPadding(new Insets(5));

        btnToggleHistory.setOnAction(e -> {
            boolean visible = historyListView.isVisible();
            historyListView.setVisible(!visible);
            btnClearHistory.setVisible(!visible);
            btnToggleHistory.setText(visible ? "Show History" : "Hide History");
        });

        btnClearHistory.setOnAction(e -> {
            historySet.clear();
            updateHistoryView();
            try {
                Files.deleteIfExists(HISTORY_FILE);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        historyListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String url = historyListView.getSelectionModel().getSelectedItem();
                if (url != null && !url.isBlank()) {
                    Tab currentTab = tabPane.getSelectionModel().getSelectedItem();
                    if (currentTab != null) {
                        WebView view = (WebView) currentTab.getContent();
                        view.getEngine().load(url);
                    }
                }
            }
        });

        loadHistory();

        // --- Actions ---

        // New tab
        btnNewTab.setOnAction(e -> createTab(HOME));

        // Back button
        btnBack.setOnAction(e -> {
            Tab tab = tabPane.getSelectionModel().getSelectedItem();
            if (tab != null) {
                WebView view = (WebView) tab.getContent();
                WebEngine engine = view.getEngine();
                if (engine.getHistory().getCurrentIndex() > 0) {
                    Platform.runLater(() -> engine.getHistory().go(-1));
                }
            }
        });

        // Address bar Enter loads url in active tab
        addressField.setOnKeyPressed(k -> {
            if (k.getCode() == KeyCode.ENTER) {
                String url = addressField.getText().trim();
                if (!url.isEmpty()) {
                    Tab tab = tabPane.getSelectionModel().getSelectedItem();
                    if (tab != null) {
                        WebView view = (WebView) tab.getContent();
                        WebEngine engine = view.getEngine();
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "https://" + url;
                        }
                        engine.load(url);
                    }
                }
            }
        });

        // Update address bar on tab change
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                WebView view = (WebView) newTab.getContent();
                String loc = view.getEngine().getLocation();
                addressField.setText(loc != null ? loc : "");
            } else {
                addressField.setText("");
            }
        });

        root.setTop(topBar);
        root.setCenter(tabPane);
        root.setLeft(historyBox);

        Scene scene = new Scene(root, 1200, 780);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();

        // Save tabs & history on close
        primaryStage.setOnCloseRequest(e -> {
            saveTabs();
            saveHistory();
        });
    }

    private void createTab(String url) {
        WebView webView = new WebView();
        webView.setContextMenuEnabled(true);
        WebEngine engine = webView.getEngine();

        Tab tab = new Tab("Nová karta");
        tab.setContent(webView);
        tab.setClosable(true);

        engine.locationProperty().addListener((obs, oldLoc, newLoc) -> {
            if (newLoc != null && !newLoc.isEmpty()) {
                tab.setText(shortTitle(newLoc));
                addToHistory(newLoc);
            }
        });

        engine.titleProperty().addListener((obs, oldT, newT) -> {
            if (newT != null && !newT.isEmpty()) {
                tab.setText(newT.length() > 20 ? newT.substring(0, 20) + "…" : newT);
            }
        });

        engine.load(url);

        tab.setOnClosed(e -> {
            WebEngine we = webView.getEngine();
            we.load(null);
        });

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    private String shortTitle(String url) {
        try {
            String noProto = url.replaceFirst("^https?://", "");
            if (noProto.length() > 25) return noProto.substring(0, 22) + "…";
            return noProto;
        } catch (Exception ex) {
            return url;
        }
    }

    // ------------- historie ---------------------

    private void addToHistory(String url) {
        if (!historySet.contains(url)) {
            historySet.add(url);
            updateHistoryView();
        }
    }

    private void updateHistoryView() {
        Platform.runLater(() -> {
            historyListView.getItems().setAll(historySet);
        });
    }

    private void loadHistory() {
        if (!Files.exists(HISTORY_FILE)) return;
        try {
            List<String> lines = Files.readAllLines(HISTORY_FILE);
            historySet.clear();
            historySet.addAll(lines);
            updateHistoryView();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveHistory() {
        try {
            Files.write(HISTORY_FILE, historySet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ------------- záložky ----------------------

    private List<String> loadTabs() {
        if (!Files.exists(TABS_FILE)) return Collections.emptyList();
        try {
            return Files.readAllLines(TABS_FILE).stream().filter(l -> !l.isBlank()).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private void saveTabs() {
        try {
            List<String> urls = tabPane.getTabs().stream()
                    .map(t -> {
                        WebView wv = (WebView) t.getContent();
                        return wv.getEngine().getLocation();
                    })
                    .filter(u -> u != null && !u.isBlank())
                    .collect(Collectors.toList());
            Files.write(TABS_FILE, urls);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
