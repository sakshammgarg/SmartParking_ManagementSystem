/*
 *  Smart Parking Management System — Main.java
 *  Single-file JavaFX Application | OOSD Lab Project
 *  Covers: OOP (Wk1), Enums (Wk2), Threads+Sync (Wk3-4),
 *          File I/O (Wk5), Serialization (Wk6),
 *          Generics (Wk7), Collections (Wk8), JavaFX (Wk9-10)
 */

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class Main extends Application {

    // ── Color palette ─────────────────────────────────────────────
    private static final String BG       = "#0D1117";
    private static final String CARD     = "#161B22";
    private static final String CARD2    = "#1C2128";
    private static final String BORDER   = "#30363D";
    private static final String ACCENT   = "#58A6FF";
    private static final String GREEN    = "#3FB950";
    private static final String RED      = "#F85149";
    private static final String YELLOW   = "#D29922";
    private static final String PURPLE   = "#BC8CFF";
    private static final String TEXT     = "#C9D1D9";
    private static final String TEXT_SEC = "#8B949E";

    // ── Instance references ────────────────────────────────────────
    private ParkingService service;

    // Dashboard
    private VBox  slotContainer;
    private Label totalValue, availableValue, occupiedValue, revenueValue;
    private final Map<VehicleType, Label> zoneSubLabels   = new EnumMap<>(VehicleType.class);
    private final Map<VehicleType, Label> zoneCountLabels = new EnumMap<>(VehicleType.class);

    // Entry tab
    private TextField        entryPlate, entryOwner;
    private ComboBox<String> entryTypeBox, entrySlotBox;
    private CheckBox         electricCheck;
    private Label            entryMsg;

    // Exit tab
    private TextField     exitPlate;
    private VBox          vehicleInfoBox;
    private Label         exitMsg, feeLabel, durationLabel;
    private Label         exitPlateLbl, exitOwnerLbl, exitTypeLbl, exitSlotLbl;
    private Button        confirmExitBtn;
    private BookingRecord currentExitRecord;

    // Records tab
    private TableView<BookingRecord>        recordsTable;
    private ObservableList<BookingRecord>   recordsData;

    // Logs tab
    private TextArea logArea;

    // ══════════════════════════════════════════════════════════════
    //  APPLICATION ENTRY
    // ══════════════════════════════════════════════════════════════

    @Override
    public void start(Stage stage) {
        service = ParkingService.getInstance();
        addDemoData();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG + ";");
        root.setTop(buildHeader());
        root.setCenter(buildTabPane());

        Scene scene = new Scene(root, 1280, 820);
        loadCSS(scene);

        stage.setTitle("Smart Parking Management System");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(650);
        stage.show();

        // Auto-refresh every 5 seconds
        Timeline timer = new Timeline(new KeyFrame(Duration.seconds(5), e -> refreshAll()));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();
    }

    // ══════════════════════════════════════════════════════════════
    //  HEADER
    // ══════════════════════════════════════════════════════════════

    private HBox buildHeader() {
        HBox header = new HBox(16);
        header.setStyle(
            "-fx-background-color:" + CARD + ";" +
            "-fx-border-color: transparent transparent " + BORDER + " transparent;" +
            "-fx-border-width: 0 0 1 0;" +
            "-fx-padding: 14 24 14 24;"
        );
        header.setAlignment(Pos.CENTER_LEFT);

        // Logo
        Circle logoCircle = new Circle(20, Color.web(ACCENT));
        Label  logoText   = new Label("P");
        logoText.setStyle("-fx-text-fill: white; -fx-font-size: 17px; -fx-font-weight: bold;");
        StackPane logo = new StackPane(logoCircle, logoText);

        // Title
        Label appName = new Label("SMART PARKING");
        appName.setStyle("-fx-text-fill:" + TEXT + "; -fx-font-size:17px; -fx-font-weight:bold; -fx-font-family:'Courier New';");
        Label appSub = new Label("MANAGEMENT SYSTEM  v1.0");
        appSub.setStyle("-fx-text-fill:" + TEXT_SEC + "; -fx-font-size:10px; -fx-font-family:'Courier New';");
        VBox titleBox = new VBox(2, appName, appSub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Slot summary badges
        HBox badges = new HBox(10);
        badges.setAlignment(Pos.CENTER);
        for (VehicleType vt : VehicleType.values()) {
            Label badge = new Label();
            badge.setStyle(
                "-fx-text-fill:" + vt.getHexColor() + ";" +
                "-fx-background-color:" + vt.getHexColor() + "22;" +
                "-fx-padding:4 10 4 10; -fx-background-radius:20; -fx-font-size:11px; -fx-font-weight:bold;"
            );
            zoneCountLabels.put(vt, badge);
            badges.getChildren().add(badge);
        }
        updateHeaderBadges();

        Button simBtn  = makeButton("▶  Simulate Arrival", ACCENT, "white");
        Button saveBtn = makeButton("  Save Records",     CARD2,  TEXT);
        simBtn.setOnAction(e  -> runSimulation());
        saveBtn.setOnAction(e -> {
            DataPersistence.save(service.getHistory());
            showAlert("Records saved to booking_records.dat", Alert.AlertType.INFORMATION);
        });

        header.getChildren().addAll(logo, titleBox, spacer, badges, simBtn, saveBtn);
        return header;
    }

    private void updateHeaderBadges() {
        for (VehicleType vt : VehicleType.values()) {
            Label badge = zoneCountLabels.get(vt);
            if (badge == null) continue;
            int avail = service.getAvailableSlots(vt).size();
            int total = (int) service.getAllSlots().stream().filter(s -> s.getAcceptedType() == vt).count();
            badge.setText(vt.getLabel() + "  " + avail + "/" + total);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  TAB PANE
    // ══════════════════════════════════════════════════════════════

    private TabPane buildTabPane() {
        TabPane tp = new TabPane();
        tp.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tp.setStyle("-fx-background-color:" + BG + "; -fx-tab-min-height:40px;");

        tp.getTabs().addAll(
            makeTab("  Dashboard  ",     buildDashboard()),
            makeTab("  Vehicle Entry  ", buildEntryTab()),
            makeTab("  Vehicle Exit  ",  buildExitTab()),
            makeTab("  Records  ",       buildRecordsTab()),
            makeTab("  Event Logs  ",    buildLogsTab())
        );
        return tp;
    }

    private Tab makeTab(String title, javafx.scene.Node content) {
        return new Tab(title, content);
    }

    // ══════════════════════════════════════════════════════════════
    //  DASHBOARD TAB
    // ══════════════════════════════════════════════════════════════

    private ScrollPane buildDashboard() {
        slotContainer = new VBox(24);

        VBox dash = new VBox(20);
        dash.setPadding(new Insets(24));
        dash.setStyle("-fx-background-color:" + BG + ";");
        dash.getChildren().addAll(buildStatsRow(), buildLegend(), slotContainer);

        refreshSlotGrid();

        ScrollPane sp = new ScrollPane(dash);
        sp.setStyle("-fx-background-color:" + BG + "; -fx-background:" + BG + ";");
        sp.setFitToWidth(true);
        return sp;
    }

    private HBox buildStatsRow() {
        totalValue     = statNumLabel(String.valueOf(service.getTotalSlots()));
        availableValue = statNumLabel(String.valueOf(service.getAvailableCount()));
        occupiedValue  = statNumLabel(String.valueOf(service.getOccupiedCount()));
        revenueValue   = statNumLabel("Rs. 0");

        HBox row = new HBox(16,
            buildStatCard("TOTAL SLOTS", totalValue,     ACCENT,  "ALL ZONES"),
            buildStatCard("AVAILABLE",   availableValue,  GREEN,   "READY NOW"),
            buildStatCard("OCCUPIED",    occupiedValue,   RED,     "IN USE"),
            buildStatCard("REVENUE",     revenueValue,    PURPLE,  "TODAY EARNED")
        );
        return row;
    }

    private Label statNumLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:" + TEXT + "; -fx-font-size:32px; -fx-font-weight:bold; -fx-font-family:'Courier New';");
        return l;
    }

    private VBox buildStatCard(String title, Label valueLabel, String color, String sub) {
        Label subLbl   = new Label(sub);
        subLbl.setStyle("-fx-text-fill:" + color + "; -fx-font-size:10px; -fx-font-weight:bold;");
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-text-fill:" + TEXT_SEC + "; -fx-font-size:11px;");

        VBox card = new VBox(5, subLbl, valueLabel, titleLbl);
        card.setPadding(new Insets(20, 22, 20, 22));
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setStyle(
            "-fx-background-color:" + CARD + ";" +
            "-fx-background-radius:10;" +
            "-fx-border-color: transparent transparent transparent " + color + ";" +
            "-fx-border-width: 0 0 0 4;" +
            "-fx-border-radius:10;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 8, 0, 0, 3);"
        );

        // Pulse animation on the value label
        ScaleTransition pulse = new ScaleTransition(Duration.millis(300), valueLabel);
        pulse.setFromX(1.1); pulse.setFromY(1.1);
        pulse.setToX(1.0);   pulse.setToY(1.0);
        pulse.play();

        return card;
    }

    private HBox buildLegend() {
        HBox leg = new HBox(18);
        leg.setAlignment(Pos.CENTER_LEFT);
        leg.setPadding(new Insets(0, 0, 4, 2));

        addLegItem(leg, YELLOW, "Bike Zone");
        addLegItem(leg, ACCENT, "Car Zone");
        addLegItem(leg, PURPLE, "Truck Zone");

        Label div = new Label("  |");
        div.setStyle("-fx-text-fill:" + BORDER + "; -fx-font-size:14px;");
        leg.getChildren().add(div);

        addLegItem(leg, GREEN, "Available");
        addLegItem(leg, RED,   "Occupied");
        return leg;
    }

    private void addLegItem(HBox parent, String color, String label) {
        Circle dot = new Circle(5, Color.web(color));
        Label  lbl = new Label(label);
        lbl.setStyle("-fx-text-fill:" + TEXT_SEC + "; -fx-font-size:11px;");
        HBox item  = new HBox(6, dot, lbl);
        item.setAlignment(Pos.CENTER_LEFT);
        parent.getChildren().add(item);
    }

    // Rebuild the slot grid from scratch (called on every refresh)
    private void refreshSlotGrid() {
        slotContainer.getChildren().clear();
        for (VehicleType type : VehicleType.values()) {
            slotContainer.getChildren().add(buildZoneSection(type));
        }
    }

    private VBox buildZoneSection(VehicleType type) {
        List<ParkingSlot> slots = service.getAllSlots().stream()
            .filter(s -> s.getAcceptedType() == type)
            .collect(Collectors.toList());
        long free = slots.stream().filter(ParkingSlot::isAvailable).count();

        // Zone header
        Rectangle strip = new Rectangle(4, 22);
        strip.setFill(Color.web(type.getHexColor()));
        strip.setArcWidth(4); strip.setArcHeight(4);

        Label zoneLbl = new Label(type.getLabel() + " ZONE");
        zoneLbl.setStyle("-fx-text-fill:" + type.getHexColor() + "; -fx-font-size:13px; -fx-font-weight:bold;");

        Label badge = new Label(free + "/" + slots.size() + " free");
        badge.setStyle(
            "-fx-text-fill:" + TEXT_SEC + ";" +
            "-fx-background-color:" + CARD2 + ";" +
            "-fx-padding:3 10 3 10; -fx-background-radius:12; -fx-font-size:11px;"
        );

        Label rateLbl = new Label("Rs." + type.getHourlyRate() + "/hr");
        rateLbl.setStyle("-fx-text-fill:" + TEXT_SEC + "; -fx-font-size:10px;");

        HBox header = new HBox(10, strip, zoneLbl, badge, rateLbl);
        header.setAlignment(Pos.CENTER_LEFT);

        // Slot cards in a flow pane
        FlowPane fp = new FlowPane(8, 8);
        slots.forEach(s -> fp.getChildren().add(buildSlotCard(s)));

        VBox section = new VBox(10, header, fp);
        return section;
    }

    private HBox buildSlotCard(ParkingSlot slot) {
        boolean occ       = !slot.isAvailable();
        String  typeColor = slot.getAcceptedType().getHexColor();
        String  bgColor   = occ
            ? (slot.getAcceptedType() == VehicleType.BIKE ? "#1E1A00" : "#1E0A0A")
            : CARD;
        String  border    = occ ? RED : typeColor;

        // Left colored strip
        Rectangle strip = new Rectangle(3, 95);
        strip.setFill(Color.web(border));
        strip.setArcWidth(3); strip.setArcHeight(3);

        // Card content
        VBox content = new VBox(4);
        content.setPrefSize(126, 95);
        content.setPadding(new Insets(8, 10, 8, 10));
        String baseStyle = "-fx-background-color:" + bgColor + "; -fx-background-radius: 0 6 6 0;";
        content.setStyle(baseStyle);

        // Top row: slot ID + type badge
        Label idLbl = new Label(slot.getSlotId());
        idLbl.setStyle("-fx-text-fill:" + TEXT + "; -fx-font-size:12px; -fx-font-weight:bold; -fx-font-family:'Courier New';");

        Label typeBadge = new Label(slot.getAcceptedType().getLabel());
        typeBadge.setStyle(
            "-fx-text-fill:" + typeColor + ";" +
            "-fx-background-color:" + typeColor + "22;" +
            "-fx-font-size:9px; -fx-padding:2 4 2 4; -fx-background-radius:3;"
        );

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox topRow = new HBox(4, idLbl, sp, typeBadge);
        topRow.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().add(topRow);

        // Vehicle info if occupied
        if (occ && slot.getParkedVehicle() != null) {
            Label plateLbl = new Label(slot.getParkedVehicle().getLicensePlate());
            plateLbl.setStyle("-fx-text-fill:" + TEXT + "; -fx-font-size:11px; -fx-font-weight:bold;");

            String ownerStr = slot.getParkedVehicle().getOwnerName();
            if (ownerStr.length() > 14) ownerStr = ownerStr.substring(0, 14) + "…";
            Label ownerLbl = new Label(ownerStr);
            ownerLbl.setStyle("-fx-text-fill:" + TEXT_SEC + "; -fx-font-size:10px;");

            content.getChildren().addAll(plateLbl, ownerLbl);
        } else {
            Region mid = new Region();
            VBox.setVgrow(mid, Priority.ALWAYS);
            content.getChildren().add(mid);
        }

        // Status dot row
        Circle dot = new Circle(4, occ ? Color.web(RED) : Color.web(GREEN));
        Label  stLbl = new Label(occ ? "OCCUPIED" : "AVAILABLE");
        stLbl.setStyle("-fx-text-fill:" + (occ ? RED : GREEN) + "; -fx-font-size:9px; -fx-font-weight:bold;");
        HBox stRow = new HBox(5, dot, stLbl);
        stRow.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().add(stRow);

        HBox card = new HBox(0, strip, content);
        card.setStyle("-fx-background-radius:6; -fx-effect:dropshadow(gaussian, rgba(0,0,0,0.35), 5, 0, 0, 2);");
        card.setCursor(javafx.scene.Cursor.HAND);

        // Hover scale animation
        ScaleTransition st = new ScaleTransition(Duration.millis(130), card);
        card.setOnMouseEntered(e -> {
            content.setStyle("-fx-background-color:" + CARD2 + "; -fx-background-radius:0 6 6 0;");
            st.setToX(1.04); st.setToY(1.04); st.play();
        });
        card.setOnMouseExited(e -> {
            content.setStyle(baseStyle);
            st.setToX(1.0); st.setToY(1.0); st.play();
        });

        return card;
    }

    // ══════════════════════════════════════════════════════════════
    //  VEHICLE ENTRY TAB
    // ══════════════════════════════════════════════════════════════

    private ScrollPane buildEntryTab() {
        HBox root = new HBox(24);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color:" + BG + ";");

        // ─── Left: form ───────────────────────────────────────────
        VBox left = new VBox(16);
        left.setMinWidth(420);

        Label title = sectionTitle("VEHICLE ENTRY");

        VBox form = new VBox(14);
        form.setStyle("-fx-background-color:" + CARD + "; -fx-background-radius:10; -fx-padding:24;" +
                      "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.4),10,0,0,3);");

        entryPlate = new TextField();
        entryPlate.setPromptText("e.g. KA01AB1234");
        styleTextField(entryPlate);

        entryOwner = new TextField();
        entryOwner.setPromptText("Owner full name");
        styleTextField(entryOwner);

        entryTypeBox = new ComboBox<>(FXCollections.observableArrayList("CAR", "BIKE", "TRUCK"));
        entryTypeBox.setPromptText("Select Vehicle Type");
        entryTypeBox.setMaxWidth(Double.MAX_VALUE);
        styleComboBox(entryTypeBox);

        entrySlotBox = new ComboBox<>();
        entrySlotBox.setPromptText("Select Parking Slot");
        entrySlotBox.setMaxWidth(Double.MAX_VALUE);
        styleComboBox(entrySlotBox);

        electricCheck = new CheckBox("Electric Vehicle  (15% fee discount)");
        electricCheck.setStyle("-fx-text-fill:" + TEXT + "; -fx-font-size:12px;");
        electricCheck.setVisible(false);
        electricCheck.setManaged(false);

        // Update slots when type changes
        entryTypeBox.setOnAction(e -> {
            String sel = entryTypeBox.getValue();
            if (sel == null) return;
            VehicleType vt = VehicleType.valueOf(sel);
            entrySlotBox.getItems().clear();
            service.getAvailableSlots(vt).forEach(s -> entrySlotBox.getItems().add(s.getSlotId()));
            if (!entrySlotBox.getItems().isEmpty()) entrySlotBox.setValue(entrySlotBox.getItems().get(0));
            electricCheck.setVisible("CAR".equals(sel));
            electricCheck.setManaged("CAR".equals(sel));
        });

        Button parkBtn = makeButton("  PARK VEHICLE", ACCENT, "white");
        parkBtn.setMaxWidth(Double.MAX_VALUE);
        parkBtn.setPrefHeight(42);
        parkBtn.setOnAction(e -> handleEntry());

        entryMsg = new Label();
        entryMsg.setWrapText(true);
        entryMsg.setStyle("-fx-font-size:12px;");

        form.getChildren().addAll(
            fieldGroup("License Plate",  entryPlate),
            fieldGroup("Owner Name",     entryOwner),
            fieldGroup("Vehicle Type",   entryTypeBox),
            fieldGroup("Parking Slot",   entrySlotBox),
            electricCheck,
            new Separator(),
            parkBtn,
            entryMsg
        );

        left.getChildren().addAll(title, form);

        // ─── Right: zone availability cards ───────────────────────
        VBox right = new VBox(12);
        right.setMinWidth(270);

        Label availTitle = new Label("ZONE AVAILABILITY");
        availTitle.setStyle("-fx-text-fill:" + TEXT + "; -fx-font-size:13px; -fx-font-weight:bold;");
        right.getChildren().add(availTitle);

        for (VehicleType vt : VehicleType.values()) {
            right.getChildren().add(buildZoneAvailCard(vt));
        }

        // Rate card
        VBox rateCard = new VBox(8);
        rateCard.setStyle("-fx-background-color:" + CARD + "; -fx-background-radius:8; -fx-padding:16;");
        Label rateTitle = new Label("HOURLY RATES");
        rateTitle.setStyle("-fx-text-fill:" + TEXT_SEC + "; -fx-font-size:11px; -fx-font-weight:bold;");
        rateCard.getChildren().add(rateTitle);
        for (VehicleType vt : VehicleType.values()) {
            Label rl = new Label(vt.getLabel() + "   →   Rs." + vt.getHourlyRate() + "/hr");
            rl.setStyle("-fx-text-fill:" + vt.getHexColor() + "; -fx-font-size:12px; -fx-font-family:'Courier New';");
            rateCard.getChildren().add(rl);
        }
        Label evNote = new Label("* EV Cars get 15% discount");
        evNote.setStyle("-fx-text-fill:" + TEXT_SEC + "; -fx-font-size:10px;");
        rateCard.getChildren().add(evNote);
        right.getChildren().add(rateCard);

        HBox.setHgrow(left, Priority.ALWAYS);
        root.getChildren().addAll(left, right);

        ScrollPane sp = new ScrollPane(root);
        sp.setStyle("-fx-background-color:" + BG + "; -fx-background:" + BG + ";");
        sp.setFitToWidth(true);
        return sp;
    }

    private HBox buildZoneAvailCard(VehicleType vt) {
        int total = (int) service.getAllSlots().stream().filter(s -> s.getAcceptedType() == vt).count();
        int avail = service.getAvailableSlots(vt).size();

        Rectangle strip = new Rectangle(4, 46);
        strip.setFill(Color.web(vt.getHexColor()));
        strip.setArcWidth(4); strip.setArcHeight(4);

        Label typeLbl = new Label(vt.getLabel());
        typeLbl.setStyle("-fx-text-fill:" + vt.getHexColor() + "; -fx-font-size:12px; -fx-font-weight:bold;");

        Label subLbl = new Label(avail + " / " + total + " slots free");
        subLbl.setStyle("-fx-text-fill:" + TEXT_SEC + "; -fx-font-size:11px;");
        zoneSubLabels.put(vt, subLbl);   // stored for refresh

        Label bigCount = new Label(String.valueOf(avail));
        bigCount.setStyle("-fx-text-fill:" + TEXT + "; -fx-font-size:26px; -fx-font-weight:bold; -fx-font-family:'Courier New';");
        zoneCountLabels.put(vt, bigCount);  // reuse map for big count

        VBox info = new VBox(3, typeLbl, subLbl);
        Region sp  = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        HBox inner = new HBox(10, info, sp, bigCount);
        inner.setAlignment(Pos.CENTER_LEFT);
        inner.setPadding(new Insets(10, 14, 10, 10));
        inner.setStyle("-fx-background-color:" + CARD + "; -fx-background-radius:0 8 8 0;");

        HBox card = new HBox(0, strip, inner);
        card.setStyle("-fx-background-radius:8;");
        return card;
    }

    private void handleEntry() {
        String plate = entryPlate.getText().trim();
        String owner = entryOwner.getText().trim();
        String type  = entryTypeBox.getValue();
        String slot  = entrySlotBox.getValue();

        if (plate.isEmpty() || owner.isEmpty() || type == null || slot == null) {
            setMsg(entryMsg, "Please fill in all fields.", RED);
            return;
        }

        Vehicle v;
        switch (type) {
            case "BIKE":  v = new Bike(plate, owner);  break;
            case "TRUCK": v = new Truck(plate, owner); break;
            default:      v = new Car(plate, owner, electricCheck.isSelected());
        }

        String result = service.parkVehicle(v, slot);
        if (result.startsWith("SUCCESS")) {
            setMsg(entryMsg, "✓  " + result.substring(9), GREEN);
            entryPlate.clear(); entryOwner.clear();
            entryTypeBox.setValue(null);
            entrySlotBox.getItems().clear();
            electricCheck.setSelected(false);
            electricCheck.setVisible(false); electricCheck.setManaged(false);
        } else {
            setMsg(entryMsg, "✗  " + result.substring(7), RED);
        }
        refreshAll();
    }

    // ══════════════════════════════════════════════════════════════
    //  VEHICLE EXIT TAB
    // ══════════════════════════════════════════════════════════════

    private ScrollPane buildExitTab() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color:" + BG + ";");

        Label title = sectionTitle("VEHICLE EXIT");

        exitPlate = new TextField();
        exitPlate.setPromptText("Enter License Plate  (e.g. KA01AB1234)");
        styleTextField(exitPlate);
        exitPlate.setMaxWidth(380);

        Button searchBtn = makeButton("  SEARCH", ACCENT, "white");

        HBox searchRow = new HBox(10, exitPlate, searchBtn);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        exitMsg = new Label();
        exitMsg.setWrapText(true);
        exitMsg.setStyle("-fx-font-size:12px; -fx-text-fill:" + RED + ";");

        vehicleInfoBox = buildVehicleInfoBox();
        vehicleInfoBox.setVisible(false);
        vehicleInfoBox.setManaged(false);

        searchBtn.setOnAction(e -> handleExitSearch());
        exitPlate.setOnAction(e -> handleExitSearch());

        root.getChildren().addAll(title, searchRow, exitMsg, vehicleInfoBox);

        ScrollPane sp = new ScrollPane(root);
        sp.setStyle("-fx-background-color:" + BG + "; -fx-background:" + BG + ";");
        sp.setFitToWidth(true);
        return sp;
    }

    private VBox buildVehicleInfoBox() {
        VBox box = new VBox(16);
        box.setMaxWidth(620);
        box.setStyle(
            "-fx-background-color:" + CARD + ";" +
            "-fx-background-radius:10;" +
            "-fx-padding:28;" +
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.5),12,0,0,4);"
        );

        Label foundLbl = new Label("  Vehicle Found");
        foundLbl.setStyle("-fx-text-fill:" + GREEN + "; -fx-font-size:14px; -fx-font-weight:bold;");

        Separator sep1 = new Separator();

        // Info grid
        GridPane grid = new GridPane();
        grid.setHgap(24); grid.setVgap(10);

        exitPlateLbl = infoVal(); exitOwnerLbl = infoVal();
        exitTypeLbl  = infoVal(); exitSlotLbl  = infoVal();

        grid.add(infoKey("License Plate :"), 0, 0); grid.add(exitPlateLbl, 1, 0);
        grid.add(infoKey("Owner         :"), 0, 1); grid.add(exitOwnerLbl, 1, 1);
        grid.add(infoKey("Vehicle Type  :"), 0, 2); grid.add(exitTypeLbl,  1, 2);
        grid.add(infoKey("Slot Number   :"), 0, 3); grid.add(exitSlotLbl,  1, 3);

        Separator sep2 = new Separator();

        // Duration
        durationLabel = new Label();
        durationLabel.setStyle("-fx-text-fill:" + YELLOW + "; -fx-font-size:18px; -fx-font-weight:bold; -fx-font-family:'Courier New';");
        HBox durRow = new HBox(16, infoKey("Duration  :"), durationLabel);
        durRow.setAlignment(Pos.CENTER_LEFT);

        // Fee
        feeLabel = new Label();
        feeLabel.setStyle("-fx-text-fill:" + GREEN + "; -fx-font-size:24px; -fx-font-weight:bold; -fx-font-family:'Courier New';");
        HBox feeRow = new HBox(16, infoKey("Total Fee :"), feeLabel);
        feeRow.setAlignment(Pos.CENTER_LEFT);

        confirmExitBtn = makeButton("  CONFIRM EXIT & PRINT RECEIPT", GREEN, "#0D1117");
        confirmExitBtn.setMaxWidth(Double.MAX_VALUE);
        confirmExitBtn.setPrefHeight(46);
        confirmExitBtn.setOnAction(e -> handleConfirmExit());

        box.getChildren().addAll(foundLbl, sep1, grid, sep2, durRow, feeRow, confirmExitBtn);
        return box;
    }

    private void handleExitSearch() {
        String plate = exitPlate.getText().trim().toUpperCase();
        if (plate.isEmpty()) { setMsg(exitMsg, "Please enter a license plate.", RED); return; }

        if (!service.isVehicleParked(plate)) {
            setMsg(exitMsg, "No active booking found for plate: " + plate, RED);
            vehicleInfoBox.setVisible(false); vehicleInfoBox.setManaged(false);
            return;
        }

        currentExitRecord = service.getActiveBooking(plate);
        ParkingSlot slot  = service.findSlotById(currentExitRecord.getSlotId());

        exitPlateLbl.setText(currentExitRecord.getLicensePlate());
        exitOwnerLbl.setText(currentExitRecord.getOwnerName());
        exitTypeLbl.setText(currentExitRecord.getVehicleType());
        exitSlotLbl.setText(currentExitRecord.getSlotId());
        durationLabel.setText(currentExitRecord.getDurationStr());

        double estFee = 0;
        if (slot != null && slot.getParkedVehicle() != null)
            estFee = slot.getParkedVehicle().calculateFee(currentExitRecord.getDurationHours());
        feeLabel.setText(String.format("Rs. %.0f", estFee));

        exitMsg.setText("");
        vehicleInfoBox.setVisible(true);
        vehicleInfoBox.setManaged(true);

        // Animate in
        FadeTransition ft = new FadeTransition(Duration.millis(300), vehicleInfoBox);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    private void handleConfirmExit() {
        if (currentExitRecord == null) return;
        String plate = currentExitRecord.getLicensePlate();
        Map<String, Object> res = service.releaseVehicle(plate);

        if (Boolean.TRUE.equals(res.get("success"))) {
            BookingRecord rec = (BookingRecord) res.get("record");
            double fee  = (Double) res.get("fee");
            long   dur  = (Long)   res.get("duration");

            showAlert(
                "EXIT RECEIPT\n" +
                "─────────────────────────────\n" +
                "Plate    :  " + plate + "\n" +
                "Owner    :  " + rec.getOwnerName() + "\n" +
                "Slot     :  " + rec.getSlotId() + "\n" +
                "Type     :  " + rec.getVehicleType() + "\n" +
                "Entry    :  " + rec.getEntryTimeStr() + "\n" +
                "Exit     :  " + rec.getExitTimeStr()  + "\n" +
                "Duration :  " + (dur/60) + "h " + (dur%60) + "m\n" +
                "─────────────────────────────\n" +
                "FEE PAID :  Rs. " + String.format("%.0f", fee) + "\n\n" +
                "Thank you! Drive safe.",
                Alert.AlertType.INFORMATION
            );

            vehicleInfoBox.setVisible(false); vehicleInfoBox.setManaged(false);
            exitPlate.clear();
            currentExitRecord = null;
            refreshAll();
        } else {
            setMsg(exitMsg, (String) res.get("message"), RED);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  RECORDS TAB
    // ══════════════════════════════════════════════════════════════

    private VBox buildRecordsTab() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color:" + BG + ";");

        Label title = sectionTitle("BOOKING RECORDS");

        // Filter buttons
        ToggleGroup tg = new ToggleGroup();
        ToggleButton allBtn    = filterBtn("All",       tg, true);
        ToggleButton activeBtn = filterBtn("Active",    tg, false);
        ToggleButton doneBtn   = filterBtn("Completed", tg, false);
        HBox filters = new HBox(8, allBtn, activeBtn, doneBtn);

        recordsData  = FXCollections.observableArrayList();
        recordsTable = buildRecordsTable();
        VBox.setVgrow(recordsTable, Priority.ALWAYS);

        tg.selectedToggleProperty().addListener((obs, old, nv) -> {
            if (nv == null) { allBtn.setSelected(true); return; }
            String sel = ((ToggleButton) nv).getText();
            List<BookingRecord> all = service.getHistory();
            recordsData.setAll(
                "Active".equals(sel)    ? all.stream().filter(BookingRecord::isActive).collect(Collectors.toList()) :
                "Completed".equals(sel) ? all.stream().filter(r -> !r.isActive()).collect(Collectors.toList()) :
                all
            );
        });

        refreshRecordsTable();
        root.getChildren().addAll(title, filters, recordsTable);
        return root;
    }

    private ToggleButton filterBtn(String text, ToggleGroup tg, boolean selected) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(tg);
        btn.setSelected(selected);
        String base = "-fx-padding:6 18 6 18; -fx-background-radius:20; -fx-border-radius:20;" +
                      "-fx-border-width:1; -fx-cursor:hand; -fx-font-size:12px;";
        Runnable updateStyle = () -> {
            boolean sel = btn.isSelected();
            btn.setStyle(base +
                "-fx-background-color:" + (sel ? ACCENT  : CARD2) + ";" +
                "-fx-text-fill:"        + (sel ? "white" : TEXT_SEC) + ";" +
                "-fx-border-color:"     + (sel ? ACCENT  : BORDER) + ";"
            );
        };
        updateStyle.run();
        btn.selectedProperty().addListener((obs, o, n) -> updateStyle.run());
        return btn;
    }

    @SuppressWarnings("unchecked")
    private TableView<BookingRecord> buildRecordsTable() {
        TableView<BookingRecord> table = new TableView<>(recordsData);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No records yet."));

        table.getColumns().addAll(
            tcol("Slot",         65,  BookingRecord::getSlotId),
            tcol("License Plate",115, BookingRecord::getLicensePlate),
            tcol("Owner",        145, BookingRecord::getOwnerName),
            tcol("Type",         70,  BookingRecord::getVehicleType),
            tcol("Entry Time",   135, BookingRecord::getEntryTimeStr),
            tcol("Exit Time",    135, BookingRecord::getExitTimeStr),
            tcol("Duration",     80,  BookingRecord::getDurationStr),
            tcol("Fee",          90,  BookingRecord::getFeeStr),
            statusTableCol()
        );
        return table;
    }

    private TableColumn<BookingRecord, String> tcol(String title, int w,
            java.util.function.Function<BookingRecord, String> fn) {
        TableColumn<BookingRecord, String> c = new TableColumn<>(title);
        c.setPrefWidth(w);
        c.setCellValueFactory(d -> new SimpleStringProperty(fn.apply(d.getValue())));
        c.setSortable(false);
        return c;
    }

    private TableColumn<BookingRecord, String> statusTableCol() {
        TableColumn<BookingRecord, String> c = new TableColumn<>("Status");
        c.setPrefWidth(80);
        c.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatusStr()));
        c.setCellFactory(col -> new TableCell<BookingRecord, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle("-fx-text-fill:" + ("ACTIVE".equals(item) ? GREEN : TEXT_SEC) +
                         "; -fx-font-weight:bold; -fx-alignment:CENTER;");
            }
        });
        c.setSortable(false);
        return c;
    }

    private void refreshRecordsTable() {
        if (recordsData != null) recordsData.setAll(service.getHistory());
    }

    // ══════════════════════════════════════════════════════════════
    //  EVENT LOGS TAB
    // ══════════════════════════════════════════════════════════════

    private VBox buildLogsTab() {
        VBox root = new VBox(12);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color:" + BG + ";");

        Label title = sectionTitle("EVENT LOGS");

        Button refreshBtn = makeButton("  Refresh", CARD2, TEXT);
        refreshBtn.setOnAction(e -> refreshLogs());

        Label hint = new Label("Logs are also written to  parking_log.txt");
        hint.setStyle("-fx-text-fill:" + TEXT_SEC + "; -fx-font-size:11px;");

        HBox topRow = new HBox(14, title, new Region() {{ HBox.setHgrow(this, Priority.ALWAYS); }}, hint, refreshBtn);
        topRow.setAlignment(Pos.CENTER_LEFT);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setStyle(
            "-fx-background-color:#0A0E1A;" +
            "-fx-control-inner-background:#0A0E1A;" +
            "-fx-text-fill:" + GREEN + ";" +
            "-fx-font-family:'Courier New';" +
            "-fx-font-size:12px;"
        );
        VBox.setVgrow(logArea, Priority.ALWAYS);

        refreshLogs();
        root.getChildren().addAll(topRow, logArea);
        return root;
    }

    // ══════════════════════════════════════════════════════════════
    //  SIMULATION + DEMO DATA
    // ══════════════════════════════════════════════════════════════

    private void runSimulation() {
        Object[][] data = {
            {"SIM_C1", "CAR",   "Rohit Kumar",  false},
            {"SIM_C2", "CAR",   "Priya Singh",  true },   // EV
            {"SIM_B1", "BIKE",  "Ravi Verma",   false},
            {"SIM_B2", "BIKE",  "Anjali Das",   false},
            {"SIM_T1", "TRUCK", "Vijay Reddy",  false},
            {"SIM_T2", "TRUCK", "Suresh Nair",  false}
        };

        for (int i = 0; i < data.length; i++) {
            String plate = (String) data[i][0] + System.currentTimeMillis() % 10000;
            String owner = (String) data[i][2];
            boolean elec = (Boolean) data[i][3];
            Vehicle v;
            switch ((String) data[i][1]) {
                case "BIKE":  v = new Bike(plate, owner);         break;
                case "TRUCK": v = new Truck(plate, owner);        break;
                default:      v = new Car(plate, owner, elec);
            }
            new GateThread("Gate-" + (i + 1), v, service, this::refreshAll).start();
        }

        showAlert(
            "Simulation launched!\n\n" +
            "6 vehicles are arriving concurrently at all gates.\n" +
            "GateThreads compete for slots — synchronized parkVehicle()\n" +
            "ensures only one vehicle claims each slot.\n\n" +
            "Watch the slot grid update live.",
            Alert.AlertType.INFORMATION
        );
    }

    private void addDemoData() {
        Vehicle[] demo = {
            new Car("KA01AB1234",  "Rohit Kumar"),
            new Car("MH02CD5678",  "Priya Singh", true),
            new Bike("DL03EF9012", "Ravi Verma"),
            new Truck("TN04GH345", "Vijay Reddy")
        };
        String[] slots = {"C01", "C02", "B01", "T01"};
        for (int i = 0; i < demo.length; i++) service.parkVehicle(demo[i], slots[i]);
    }

    // ══════════════════════════════════════════════════════════════
    //  REFRESH METHODS
    // ══════════════════════════════════════════════════════════════

    private void refreshAll() {
        totalValue.setText(String.valueOf(service.getTotalSlots()));
        availableValue.setText(String.valueOf(service.getAvailableCount()));
        occupiedValue.setText(String.valueOf(service.getOccupiedCount()));
        revenueValue.setText(String.format("Rs. %.0f", service.getTotalRevenue()));

        refreshSlotGrid();
        refreshRecordsTable();
        refreshZoneAvailCards();
        updateHeaderBadges();
        refreshLogs();
    }

    private void refreshZoneAvailCards() {
        for (VehicleType vt : VehicleType.values()) {
            int total = (int) service.getAllSlots().stream().filter(s -> s.getAcceptedType() == vt).count();
            int avail = service.getAvailableSlots(vt).size();

            Label subLbl  = zoneSubLabels.get(vt);
            if (subLbl  != null) subLbl.setText(avail + " / " + total + " slots free");

            Label bigLbl  = zoneCountLabels.get(vt);
            if (bigLbl  != null) bigLbl.setText(String.valueOf(avail));
        }
    }

    private void refreshLogs() {
        if (logArea == null) return;
        List<String> logs = service.getLogs();
        logArea.setText(String.join("\n", logs));
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    // ══════════════════════════════════════════════════════════════
    //  STYLING HELPERS
    // ══════════════════════════════════════════════════════════════

    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:" + ACCENT + "; -fx-font-size:18px; -fx-font-weight:bold; -fx-font-family:'Courier New';");
        return l;
    }

    private VBox fieldGroup(String label, javafx.scene.Node ctrl) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill:" + TEXT_SEC + "; -fx-font-size:11px;");
        return new VBox(5, lbl, ctrl);
    }

    private Label infoKey(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:" + TEXT_SEC + "; -fx-font-size:12px; -fx-font-family:'Courier New';");
        return l;
    }
    private Label infoVal() {
        Label l = new Label();
        l.setStyle("-fx-text-fill:" + TEXT + "; -fx-font-size:13px; -fx-font-weight:bold;");
        return l;
    }

    private void styleTextField(TextField tf) {
        tf.setStyle(
            "-fx-background-color:" + CARD2 + ";" +
            "-fx-text-fill:" + TEXT + ";" +
            "-fx-prompt-text-fill:" + TEXT_SEC + ";" +
            "-fx-border-color:" + BORDER + ";" +
            "-fx-border-radius:6; -fx-background-radius:6;" +
            "-fx-padding:9 12 9 12; -fx-font-size:13px;"
        );
    }

    private void styleComboBox(ComboBox<?> cb) {
        cb.setStyle(
            "-fx-background-color:" + CARD2 + ";" +
            "-fx-border-color:" + BORDER + ";" +
            "-fx-border-radius:6; -fx-background-radius:6;" +
            "-fx-font-size:13px; -fx-padding:2;"
        );
    }

    private Button makeButton(String text, String bg, String fg) {
        Button btn = new Button(text);
        String base =
            "-fx-background-color:" + bg + ";" +
            "-fx-text-fill:" + fg + ";" +
            "-fx-padding:10 20 10 20;" +
            "-fx-background-radius:8;" +
            "-fx-cursor:hand;" +
            "-fx-font-size:13px;" +
            "-fx-font-weight:bold;";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(base + "-fx-opacity:0.82;"));
        btn.setOnMouseExited(e  -> btn.setStyle(base));
        return btn;
    }

    private void setMsg(Label lbl, String text, String color) {
        lbl.setStyle("-fx-font-size:12px; -fx-text-fill:" + color + ";");
        lbl.setText(text);
    }

    private void showAlert(String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle("Smart Parking System");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setStyle(
            "-fx-background-color:" + CARD + ";" +
            "-fx-font-family:'Courier New';" +
            "-fx-font-size:12px;"
        );
        Label content = new Label(message);
        content.setStyle("-fx-text-fill:" + TEXT + "; -fx-font-family:'Courier New'; -fx-font-size:12px;");
        content.setWrapText(true);
        alert.getDialogPane().setContent(content);
        alert.showAndWait();
    }

    // Load external CSS via temp file for selectors that can't be set inline
    private void loadCSS(Scene scene) {
        String css =
            ".tab-pane > .tab-header-area { -fx-background-color:" + CARD + "; }" +
            ".tab-pane > .tab-header-area > .tab-header-background { -fx-background-color:" + CARD + "; }" +
            ".tab { -fx-background-color:" + CARD + "; -fx-focus-color:transparent; -fx-faint-focus-color:transparent; }" +
            ".tab .tab-label { -fx-text-fill:" + TEXT_SEC + "; -fx-font-size:12px; }" +
            ".tab:selected { -fx-background-color:" + BG + "; }" +
            ".tab:selected .tab-label { -fx-text-fill:" + TEXT + "; -fx-font-weight:bold; }" +
            ".table-view { -fx-background-color:" + CARD + "; -fx-border-color:" + BORDER + "; }" +
            ".table-view .column-header-background { -fx-background-color:" + CARD2 + "; }" +
            ".table-view .column-header { -fx-background-color:" + CARD2 + "; -fx-border-color:" + BORDER + "; }" +
            ".table-view .column-header .label { -fx-text-fill:" + TEXT_SEC + "; -fx-font-size:11px; -fx-font-weight:bold; }" +
            ".table-row-cell { -fx-background-color:" + CARD + "; -fx-border-color:" + BORDER + " transparent; }" +
            ".table-row-cell:odd { -fx-background-color:" + CARD2 + "; }" +
            ".table-row-cell:selected { -fx-background-color:#21262D; }" +
            ".table-cell { -fx-text-fill:" + TEXT + "; -fx-font-size:12px; }" +
            ".text-area .content { -fx-background-color:#0A0E1A; }" +
            ".scroll-bar { -fx-background-color:" + CARD + "; }" +
            ".scroll-bar .thumb { -fx-background-color:" + BORDER + "; -fx-background-radius:4; }" +
            ".scroll-bar .track { -fx-background-color:" + CARD + "; }" +
            ".scroll-bar .increment-button, .scroll-bar .decrement-button { -fx-background-color:" + CARD + "; }" +
            ".combo-box-popup .list-view { -fx-background-color:" + CARD2 + "; -fx-border-color:" + BORDER + "; }" +
            ".combo-box-popup .list-cell { -fx-background-color:" + CARD2 + "; -fx-text-fill:" + TEXT + "; -fx-padding:6 12; }" +
            ".combo-box-popup .list-cell:hover { -fx-background-color:" + BORDER + "; }" +
            ".combo-box .list-cell { -fx-background-color:" + CARD2 + "; -fx-text-fill:" + TEXT + "; }" +
            ".separator .line { -fx-border-color:" + BORDER + "; }" +
            ".check-box .box { -fx-background-color:" + CARD2 + "; -fx-border-color:" + BORDER + "; -fx-border-radius:3; }" +
            ".check-box:selected .box { -fx-background-color:" + ACCENT + "; -fx-border-color:" + ACCENT + "; }" +
            ".check-box .mark { -fx-background-color:white; }" +
            ".toggle-button:focused { -fx-focus-color:transparent; -fx-faint-focus-color:transparent; }" +
            ".table-view:focused { -fx-background-color:" + CARD + "; }";

        try 
        {
            File tmp = File.createTempFile("sps_theme_", ".css");
            tmp.deleteOnExit();
            try (FileWriter fw = new FileWriter(tmp)) { fw.write(css); }
            scene.getStylesheets().add(tmp.toURI().toString());
        }
        catch (IOException e) 
        {
            System.err.println("CSS load error: " + e.getMessage());
        }
    }

    public static void main(String[] args) 
    { 
        launch(args); 
    }
}

// ════════════════════════════════════════════════════════════════
//  WEEK 2 — Enums with constructors, fields, and methods
// ════════════════════════════════════════════════════════════════

enum VehicleType {
    BIKE (15, "BIKE",  "#D29922", "B"),
    CAR  (30, "CAR",   "#58A6FF", "C"),
    TRUCK(50, "TRUCK", "#BC8CFF", "T");

    private final int    hourlyRate;
    private final String label, hexColor, slotPrefix;

    VehicleType(int hourlyRate, String label, String hexColor, String slotPrefix) {
        this.hourlyRate  = hourlyRate;
        this.label       = label;
        this.hexColor    = hexColor;
        this.slotPrefix  = slotPrefix;
    }

    public int    getHourlyRate()  { return hourlyRate; }
    public String getLabel()       { return label; }
    public String getHexColor()    { return hexColor; }
    public String getSlotPrefix()  { return slotPrefix; }
    public double calculateFee(double hours) { return Math.ceil(hours) * hourlyRate; }
}

enum SlotStatus {
    AVAILABLE("AVAILABLE", "#3FB950"),
    OCCUPIED ("OCCUPIED",  "#F85149");

    private final String label, hexColor;
    SlotStatus(String label, String hexColor) { this.label = label; this.hexColor = hexColor; }
    public String getLabel()    { return label; }
    public String getHexColor() { return hexColor; }
}

// ════════════════════════════════════════════════════════════════
//  WEEK 1 — OOP: Abstract class, Inheritance, Polymorphism
// ════════════════════════════════════════════════════════════════

abstract class Vehicle {
    private final String licensePlate, ownerName;
    private final VehicleType type;

    Vehicle(String licensePlate, String ownerName, VehicleType type) {
        if (licensePlate == null || licensePlate.trim().isEmpty())
            throw new IllegalArgumentException("License plate cannot be empty.");
        if (ownerName == null || ownerName.trim().isEmpty())
            throw new IllegalArgumentException("Owner name cannot be empty.");
        this.licensePlate = licensePlate.toUpperCase().trim();
        this.ownerName    = ownerName.trim();
        this.type         = type;
    }

    // Abstract methods — enforced on every concrete subclass (Week 1: Abstraction)
    public abstract double calculateFee(double hours);
    public abstract String getSymbol();

    // Encapsulated getters (Week 1: Encapsulation)
    public String      getLicensePlate() { return licensePlate; }
    public String      getOwnerName()    { return ownerName; }
    public VehicleType getType()         { return type; }

    @Override
    public String toString() {
        return "[" + type.getLabel() + "] " + licensePlate + " — " + ownerName;
    }
}

// Week 1: Inheritance + Method Overriding (Runtime Polymorphism)
class Car extends Vehicle {
    private final boolean isElectric;

    // Constructor overloading (Week 1)
    Car(String plate, String owner)                   { super(plate, owner, VehicleType.CAR); isElectric = false; }
    Car(String plate, String owner, boolean electric) { super(plate, owner, VehicleType.CAR); isElectric = electric; }

    @Override public double calculateFee(double h) {
        double base = Math.ceil(h) * VehicleType.CAR.getHourlyRate();
        return isElectric ? base * 0.85 : base;  // 15% EV discount
    }
    @Override public String getSymbol() { return isElectric ? "EV" : "CAR"; }
    public boolean isElectric() { return isElectric; }
}

class Bike extends Vehicle {
    Bike(String plate, String owner) { super(plate, owner, VehicleType.BIKE); }
    @Override public double calculateFee(double h) { return Math.ceil(h) * VehicleType.BIKE.getHourlyRate(); }
    @Override public String getSymbol() { return "BIKE"; }
}

class Truck extends Vehicle {
    private final double loadTonnes;
    Truck(String plate, String owner)              { super(plate, owner, VehicleType.TRUCK); loadTonnes = 5.0; }
    Truck(String plate, String owner, double load) { super(plate, owner, VehicleType.TRUCK); loadTonnes = load; }

    @Override public double calculateFee(double h) {
        double base = Math.ceil(h) * VehicleType.TRUCK.getHourlyRate();
        return loadTonnes > 10 ? base * 1.25 : base;  // Heavy-load surcharge
    }
    @Override public String getSymbol() { return "TRK"; }
    public double getLoadTonnes() { return loadTonnes; }
}

// ════════════════════════════════════════════════════════════════
//  PARKING SLOT — Model class
// ════════════════════════════════════════════════════════════════

class ParkingSlot {
    private final String      slotId;
    private final VehicleType acceptedType;
    private SlotStatus        status;
    private Vehicle           parkedVehicle;
    private LocalDateTime     entryTime;

    ParkingSlot(String slotId, VehicleType acceptedType) {
        this.slotId       = slotId;
        this.acceptedType = acceptedType;
        this.status       = SlotStatus.AVAILABLE;
    }

    public boolean isCompatible(VehicleType t) { return acceptedType == t; }
    public boolean isAvailable()               { return status == SlotStatus.AVAILABLE; }

    public long getParkedMinutes() {
        if (entryTime == null) return -1;
        return java.time.Duration.between(entryTime, LocalDateTime.now()).toMinutes();
    }

    public String        getSlotId()           { return slotId; }
    public VehicleType   getAcceptedType()     { return acceptedType; }
    public SlotStatus    getStatus()           { return status; }
    public void          setStatus(SlotStatus s)         { this.status = s; }
    public Vehicle       getParkedVehicle()    { return parkedVehicle; }
    public void          setParkedVehicle(Vehicle v)     { this.parkedVehicle = v; }
    public LocalDateTime getEntryTime()        { return entryTime; }
    public void          setEntryTime(LocalDateTime t)   { this.entryTime = t; }
}

// ════════════════════════════════════════════════════════════════
//  WEEK 6 — Serializable BookingRecord
// ════════════════════════════════════════════════════════════════

class BookingRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final String        recordId, slotId, licensePlate, ownerName, vehicleType;
    private final LocalDateTime entryTime;
    private LocalDateTime       exitTime;
    private double              fee;
    private boolean             active;

    BookingRecord(String slotId, Vehicle v) {
        this.recordId     = "BK" + System.currentTimeMillis();
        this.slotId       = slotId;
        this.licensePlate = v.getLicensePlate();
        this.ownerName    = v.getOwnerName();
        this.vehicleType  = v.getType().getLabel();
        this.entryTime    = LocalDateTime.now();
        this.active       = true;
    }

    void processExit(double fee) {
        this.exitTime = LocalDateTime.now();
        this.fee      = fee;
        this.active   = false;
    }

    public long getDurationMinutes() {
        LocalDateTime end = exitTime != null ? exitTime : LocalDateTime.now();
        return java.time.Duration.between(entryTime, end).toMinutes();
    }
    public double getDurationHours() { return getDurationMinutes() / 60.0; }

    // Display helpers for TableView
    public String getEntryTimeStr() { return entryTime.format(FMT); }
    public String getExitTimeStr()  { return exitTime != null ? exitTime.format(FMT) : "Active"; }
    public String getFeeStr()       { return active ? "Pending" : String.format("Rs.%.0f", fee); }
    public String getDurationStr()  { long m = getDurationMinutes(); return (m/60)+"h "+(m%60)+"m"; }
    public String getStatusStr()    { return active ? "ACTIVE" : "DONE"; }

    // Getters
    public String        getRecordId()     { return recordId; }
    public String        getSlotId()       { return slotId; }
    public String        getLicensePlate() { return licensePlate; }
    public String        getOwnerName()    { return ownerName; }
    public String        getVehicleType()  { return vehicleType; }
    public LocalDateTime getEntryTime()    { return entryTime; }
    public double        getFee()          { return fee; }
    public boolean       isActive()        { return active; }
}

// ════════════════════════════════════════════════════════════════
//  WEEK 7 — Generic class with bounded type parameter
// ════════════════════════════════════════════════════════════════

class ParkingRegistry<T extends Vehicle> {
    private final String  name;
    private final List<T> vehicles = new ArrayList<>();

    ParkingRegistry(String name) { this.name = name; }

    // Generic method (Week 7)
    public <U> void printInfo(U data) {
        System.out.println("[" + name + "] " + data);
    }

    public void register(T v) {
        if (!isRegistered(v.getLicensePlate())) {
            vehicles.add(v);
            printInfo("Registered: " + v.getLicensePlate());
        }
    }
    public boolean  deregister(String plate)  { return vehicles.removeIf(v -> v.getLicensePlate().equals(plate.toUpperCase())); }
    public Optional<T> find(String plate)     { return vehicles.stream().filter(v -> v.getLicensePlate().equals(plate.toUpperCase())).findFirst(); }
    public boolean  isRegistered(String plate){ return find(plate).isPresent(); }
    public List<T>  getAll()                  { return new ArrayList<>(vehicles); }
    public int      size()                    { return vehicles.size(); }
}

// ════════════════════════════════════════════════════════════════
//  WEEK 5 — FileLogger using FileWriter + BufferedWriter
// ════════════════════════════════════════════════════════════════

class FileLogger {
    private final String     filename;
    private final List<String> logs = new ArrayList<>();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    FileLogger(String filename) {
        this.filename = filename;
        log("=== Smart Parking System Initialized ===");
    }

    // Week 5: synchronized write using FileWriter (character stream) + BufferedWriter
    synchronized void log(String msg) {
        String entry = "[" + LocalDateTime.now().format(FMT) + "] " + msg;
        logs.add(entry);
        try (FileWriter fw = new FileWriter(filename, true);          // append mode
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(entry);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("Log error: " + e.getMessage());
        }
    }

    List<String> getLogs() { return new ArrayList<>(logs); }
}

// ════════════════════════════════════════════════════════════════
//  WEEK 6 — DataPersistence: Serialization + Deserialization
// ════════════════════════════════════════════════════════════════

class DataPersistence {
    private static final String FILE = "booking_records.dat";

    static void save(List<BookingRecord> records) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE))) {
            oos.writeObject(new ArrayList<>(records));
            System.out.println("Saved " + records.size() + " records to " + FILE);
        } catch (IOException e) {
            System.err.println("Save error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    static List<BookingRecord> load() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FILE))) {
            return (List<BookingRecord>) ois.readObject();
        } catch (FileNotFoundException e)                    { return new ArrayList<>();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Load error: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  WEEKS 4 & 8 — ParkingService: Singleton, Synchronized, Collections
// ════════════════════════════════════════════════════════════════

class ParkingService {
    private static ParkingService instance;

    // Week 8: Collections
    private final ArrayList<ParkingSlot>            allSlots  = new ArrayList<>();
    private final HashMap<String, BookingRecord>    active    = new HashMap<>();  // plate → record
    private final ArrayList<BookingRecord>          history   = new ArrayList<>();

    // Week 7: Typed registries (Generic)
    private final ParkingRegistry<Car>   carReg   = new ParkingRegistry<>("Cars");
    private final ParkingRegistry<Bike>  bikeReg  = new ParkingRegistry<>("Bikes");
    private final ParkingRegistry<Truck> truckReg = new ParkingRegistry<>("Trucks");

    private final FileLogger logger = new FileLogger("parking_log.txt");
    private double totalRevenue = 0;

    private ParkingService() { initSlots(); }

    // Week 4: Synchronized Singleton
    public static synchronized ParkingService getInstance() {
        if (instance == null) instance = new ParkingService();
        return instance;
    }

    private void initSlots() {
        for (int i = 1; i <= 8;  i++) allSlots.add(new ParkingSlot(String.format("B%02d", i), VehicleType.BIKE));
        for (int i = 1; i <= 20; i++) allSlots.add(new ParkingSlot(String.format("C%02d", i), VehicleType.CAR));
        for (int i = 1; i <= 6;  i++) allSlots.add(new ParkingSlot(String.format("T%02d", i), VehicleType.TRUCK));
    }

    // Week 4: synchronized method — prevents two threads grabbing the same slot
    public synchronized String parkVehicle(Vehicle v, String slotId) {
        if (active.containsKey(v.getLicensePlate()))
            return "ERROR: " + v.getLicensePlate() + " is already parked.";
        ParkingSlot slot = findSlotById(slotId);
        if (slot == null)        return "ERROR: Slot " + slotId + " not found.";
        if (!slot.isAvailable()) return "ERROR: Slot " + slotId + " is already occupied.";
        if (!slot.isCompatible(v.getType()))
            return "ERROR: Slot " + slotId + " accepts " + slot.getAcceptedType().getLabel() + " only.";

        slot.setStatus(SlotStatus.OCCUPIED);
        slot.setParkedVehicle(v);
        slot.setEntryTime(LocalDateTime.now());

        BookingRecord rec = new BookingRecord(slotId, v);
        active.put(v.getLicensePlate(), rec);
        history.add(rec);
        registerVehicle(v);
        logger.log("ENTRY | " + slotId + " | " + v.getLicensePlate() + " | " + v.getOwnerName() + " | " + v.getType().getLabel());
        return "SUCCESS: " + v.getLicensePlate() + " parked in slot " + slotId;
    }

    // Week 4: synchronized release
    public synchronized Map<String, Object> releaseVehicle(String plate) {
        Map<String, Object> res = new HashMap<>();
        plate = plate.toUpperCase().trim();
        BookingRecord rec = active.get(plate);
        if (rec == null) {
            res.put("success", false);
            res.put("message", "No active booking for: " + plate);
            return res;
        }
        ParkingSlot slot = findSlotById(rec.getSlotId());
        double fee = 0;
        if (slot != null && slot.getParkedVehicle() != null) {
            fee = slot.getParkedVehicle().calculateFee(rec.getDurationHours());
            rec.processExit(fee);
            totalRevenue += fee;
            slot.setStatus(SlotStatus.AVAILABLE);
            slot.setParkedVehicle(null);
            slot.setEntryTime(null);
        }
        active.remove(plate);
        logger.log("EXIT  | " + rec.getSlotId() + " | " + plate +
                   " | " + rec.getDurationMinutes() + "min | Rs." + String.format("%.0f", fee));

        res.put("success",  true);
        res.put("message",  "Exit processed. Fee: Rs." + String.format("%.0f", fee));
        res.put("record",   rec);
        res.put("fee",      fee);
        res.put("duration", rec.getDurationMinutes());
        return res;
    }

    @SuppressWarnings("unchecked")
    private void registerVehicle(Vehicle v) {
        if      (v instanceof Car)   carReg.register((Car)   v);
        else if (v instanceof Bike)  bikeReg.register((Bike)  v);
        else if (v instanceof Truck) truckReg.register((Truck) v);
    }

    // Week 8: Collection operations
    public ParkingSlot       findSlotById(String id) {
        return allSlots.stream().filter(s -> s.getSlotId().equals(id)).findFirst().orElse(null);
    }
    public List<ParkingSlot> getAvailableSlots(VehicleType t) {
        return allSlots.stream().filter(s -> s.isAvailable() && s.isCompatible(t)).collect(Collectors.toList());
    }
    public List<ParkingSlot>   getAllSlots()               { return new ArrayList<>(allSlots); }
    public List<BookingRecord> getHistory()                { return new ArrayList<>(history); }
    public BookingRecord       getActiveBooking(String p)  { return active.get(p.toUpperCase().trim()); }
    public boolean             isVehicleParked(String p)   { return active.containsKey(p.toUpperCase().trim()); }
    public int                 getTotalSlots()             { return allSlots.size(); }
    public int                 getAvailableCount()         { return (int) allSlots.stream().filter(ParkingSlot::isAvailable).count(); }
    public int                 getOccupiedCount()          { return getTotalSlots() - getAvailableCount(); }
    public double              getTotalRevenue()           { return totalRevenue; }
    public List<String>        getLogs()                   { return logger.getLogs(); }
}

// ════════════════════════════════════════════════════════════════
//  WEEK 3 — GateThread: concurrent vehicle arrivals
// ════════════════════════════════════════════════════════════════

class GateThread extends Thread {
    private final Vehicle        vehicle;
    private final ParkingService service;
    private final Runnable       onComplete;
    private String               result;

    GateThread(String gateName, Vehicle vehicle, ParkingService service, Runnable onComplete) {
        super(gateName);
        this.vehicle    = vehicle;
        this.service    = service;
        this.onComplete = onComplete;
    }

    @Override
    public void run() {
        try { Thread.sleep(new Random().nextInt(500) + 200); }  // simulate arrival delay
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }

        List<ParkingSlot> avail = service.getAvailableSlots(vehicle.getType());
        result = avail.isEmpty()
            ? "ERROR: No " + vehicle.getType().getLabel() + " slots available for " + vehicle.getLicensePlate()
            : service.parkVehicle(vehicle, avail.get(0).getSlotId());

        System.out.println("[" + getName() + "] " + result);
        if (onComplete != null) Platform.runLater(onComplete);  // safe UI update
    }

    public String getResult() { return result; }
}

// ════════════════════════════════════════════════════════════════
//  WEEKS 9 & 10 — JavaFX Application: Full GUI
// ════════════════════════════════════════════════════════════════