# Smart Parking Management System

This project focuses on automated parking facility management using Object-Oriented Software Design (OOSD), concurrent programming, and Java GUI frameworks. The system accurately tracks vehicle entries, exits, and revenue across distinct vehicle categories (Car, Bike, Truck), addressing concurrency, data persistence, and real-time state visualization challenges in desktop applications.

## Project Description

This project aims to build a structurally robust automated parking management system using a **Multithreaded Simulation Framework** with persistent storage. Multiple core OOP principles are combined to improve system reliability and maintainability. To address the critical need for thread safety and accurate data tracking, the project implements **Synchronized Gate Allocation**, **Generic Type Registries**, and **Thread-Safe Event Logging** to maintain system integrity and visualize live slot allocation.

## Key Steps

### 1) Core Object-Oriented Design

* Define the abstract base `Vehicle` class to enforce required behaviors
* Implement concrete subclasses (`Car`, `Bike`, `Truck`) utilizing inheritance
* Override fee calculation logic (Polymorphism) to handle specialized conditions like EV discounts and heavy-load surcharges
* Utilize Enums with encapsulated fields for standardizing `VehicleType` and `SlotStatus`

### 2) System Architecture & State Management

* Initialize a fixed-size grid of `ParkingSlot` objects matched to specific vehicle types
* Construct a generic `ParkingRegistry<T>` to strictly type-check and manage active vehicles
* Utilize Collections (`HashMap`, `ArrayList`) for O(1) active booking lookups and historical tracking
* Apply the Java Stream API for efficient filtering of available slots and revenue calculations

### 3) Concurrency Control & Simulation

* Implement `GateThread` to simulate concurrent vehicle arrivals across multiple physical entry points
* Apply `synchronized` access modifiers to the core `parkVehicle` and `releaseVehicle` methods
* Prevent race conditions where multiple threads attempt to claim the exact same available parking slot simultaneously
* Ensure thread-safe UI updates using `Platform.runLater`

### 4) Data Persistence & Logging

* Implement Java Serialization (`ObjectOutputStream` / `ObjectInputStream`) to save and load `BookingRecord` states between application launches
* Design a thread-safe `FileLogger` utilizing `FileWriter` and `BufferedWriter`
* Append chronological system events (entries, exits, errors) to a local `parking_log.txt` file

### 5) JavaFX GUI Development

* Construct a responsive dashboard utilizing `BorderPane`, `VBox`, and `FlowPane` layouts
* Bind observable lists to a `TableView` for dynamic, sortable booking records
* Implement a `Timeline` animation for automated, periodic data refreshes (every 5 seconds)
* Apply custom CSS styling via temporary file generation to bypass inline styling limitations for complex components

## Results

The project demonstrates a highly effective, robust desktop application with rigorous software engineering standards:

**Key Findings:**

* The multithreaded simulation handles concurrent requests flawlessly without double-booking slots
* Memory state successfully maps to persistent storage, preventing data loss between sessions
* The generic registry system eliminates runtime casting errors when managing diverse vehicle objects
* The dynamic UI accurately reflects real-time revenue and occupancy metrics with zero noticeable lag

## Tech Stack

**Language**: Java (JDK 11+)
**UI Framework**: JavaFX
**Architecture**: Singleton Pattern, Multithreading, Generics, Collections
**Storage**: Flat-file Binary Serialization (.dat), Text Logging (.txt)

## Dependencies

The project requires the following environment setup:

* Java Development Kit (JDK) 11 or higher
* JavaFX SDK (tested with v21.0.10)

## Installation

1. **Clone the repository**

```bash
git clone https://github.com/sakshammgarg/SmartParking_System.git
cd SmartParking_ManagementSystem
```

2. **Configure the Environment Variable**
* Download the JavaFX SDK for your operating system
* Set the path to the extracted `lib` directory

```bash
export PATH_TO_FX="/path/to/your/javafx-sdk/lib"
```

## Usage

Run the compiled JavaFX application to interact with the management system.

Execute the following commands in your terminal:

1. Compile the project:
```bash
javac --module-path "$PATH_TO_FX" --add-modules javafx.controls Main.java
```

2. Launch the dashboard:
```bash
java --module-path "$PATH_TO_FX" --add-modules javafx.controls Main
```

The application will:
1. Load any existing serialized booking data
2. Display the live dashboard of available, occupied, and total slots
3. Allow manual vehicle entry and exit processing via the respective tabs
4. Permit running concurrent traffic simulations via the header button
5. Generate real-time activity logs visible in the Event Logs tab

## Component Selection Guide

**For System Expansion:**

* **Data Management**: `ParkingService` Singleton (Centralizes all core logic and state)
* **Concurrency Handling**: `GateThread` (Manages randomized delay and asynchronous slot requests)
* **Type Safety**: `ParkingRegistry<T>` (Ensures strict domain boundaries for vehicle categories)
* **UI Updates**: `Platform.runLater` (Mandatory for modifying JavaFX nodes from background threads)

## Advanced Features

This comprehensive implementation includes:

* **Synchronized Method Locks**: Preventing thread interference during critical state mutations
* **Object Serialization**: Flattening complex object graphs into binary data for storage
* **Generic Bounded Types**: Restricting registry parameters specifically to subclasses of `Vehicle`
* **Custom CSS Injection**: Bypassing standard JavaFX limitations for deeper table and scrollbar styling
* **Automated Data Binding**: Utilizing `SimpleStringProperty` and `ObservableList` for reactive UI tables

## Applications

Practical use cases for this software architecture:

* **Commercial Parking Garages**: Managing high-throughput vehicle entry and dynamic billing
* **University Campuses**: Tracking zone-specific permits for students and faculty
* **Toll Booth Operations**: Simulating multi-lane processing queues
* **Event Management**: Handling temporary, high-volume parking capacity tracking

## Future Improvements

Potential enhancements for scaling the system:

1. **Database Integration**: Migrating from binary serialization to a relational database (PostgreSQL/MySQL) via JDBC
2. **Hardware Sensor Mockups**: Adding IoT endpoints to trigger vehicle arrivals automatically instead of relying solely on UI buttons
3. **Advanced Analytics**: Integrating JavaFX `LineChart` or `PieChart` to visualize peak occupancy hours
4. **License Plate Recognition (ANPR)**: Connecting a mock Python microservice to simulate camera-based automatic entries
5. **Role-Based Access Control**: Adding login screens for Admin vs. Attendant privileges
