# Distributed Online Auction System

A Java-based **Distributed Online Auction System** developed to demonstrate core concepts of Distributed Computing through a working auction application. The project combines **Java RMI, multithreading, PostgreSQL, clock synchronization, Lamport logical clocks, Bully election, replication, consistency models, load balancing, and primary-backup fault tolerance** with a browser-based frontend for visualization and testing.

## Overview

The system allows registered users to place bids on an auction while demonstrating how distributed-system mechanisms operate behind the scenes. A Java RMI server handles the auction logic, PostgreSQL stores application and experiment data, and an HTTP API exposes the backend to the frontend dashboard.

The frontend provides separate interactive sections for testing remote calls, concurrent bidding, clock synchronization, leader election, replication, fault tolerance, and load-balancing strategies.

## Distributed Computing Concepts Demonstrated

### 1. Java RMI and Client-Server Communication
- `AuctionServer` exposes auction operations as remote methods using Java RMI.
- `AuctionClient` connects to the RMI registry and invokes the remote auction service.
- The HTTP API acts as a bridge between the browser frontend and the RMI backend.

### 2. Multithreading and Concurrent Bidding
- Multiple bidders can send requests concurrently.
- Bid validation and updates are synchronized to avoid inconsistent auction state.
- Concurrent bid tests can be triggered directly from the frontend.

### 3. Physical Clock Synchronization
- Implements **Cristian's Clock Synchronization Algorithm**.
- Simulated client clock drift is used to demonstrate synchronization.
- Round-trip time, estimated server time, clock offset, and corrected client time are calculated and stored.

### 4. Lamport Logical Clocks
- Each bidding event is assigned a Lamport timestamp.
- Logical timestamps help establish event ordering even when physical client clocks differ.
- Bid records store both physical time and Lamport time.

### 5. Bully Election Algorithm
- Multiple election servers participate in coordinator election.
- Any active server can initiate an election.
- The server with the highest active ID becomes the coordinator.
- Election state is displayed through the frontend.

### 6. Replication and Consistency Models
The project supports two replication modes:

- **Strong Consistency** – replica updates are completed before the operation finishes.
- **Eventual Consistency** – replica updates occur asynchronously after a simulated delay.

A replication log records operations and their replication status.

### 7. Primary-Backup Fault Tolerance
- A primary server maintains the active auction state.
- Data is replicated to a backup server.
- Primary failure can be simulated from the frontend.
- The backup can be promoted during failover.
- The experiment demonstrates service continuity using replicated state.

### 8. Load Balancing
Two request-distribution strategies are demonstrated:

- **Round Robin**
- **Least Connections**

The frontend shows the selected server, active load, request count, and simulated request latency.

## Tech Stack

| Component | Technology |
|---|---|
| Backend | Java |
| Distributed Communication | Java RMI |
| API Layer | Java `HttpServer` |
| Database | PostgreSQL |
| Database Connectivity | JDBC |
| Frontend | HTML, CSS, JavaScript |
| JDBC Driver | PostgreSQL JDBC 42.7.13 |

## Project Structure

```text
Online_Auction_System-main/
│
├── AuctionInterface.java        # RMI interface for auction operations
├── AuctionServer.java           # Main RMI auction server
├── AuctionClient.java           # Command-line RMI auction client
├── AuctionApiServer.java        # HTTP API used by the frontend
│
├── ElectionInterface.java       # Remote interface for election servers
├── ElectionServer.java          # Bully election implementation
├── ElectionClient.java          # CLI client for starting an election
│
├── ReplicationService.java      # Strong/eventual database replication
├── FaultToleranceService.java   # Primary-backup failure and failover logic
├── LoadBalancerService.java     # Round Robin and Least Connections
├── UserService.java             # User registration/update/delete logic
├── DBConnection.java            # PostgreSQL connection configuration
│
├── postgresql-42.7.13.jar       # PostgreSQL JDBC driver
│
└── frontend/
    └── distributed_auction_lab_users_fixed.html
```

## Prerequisites

Install the following before running the project:

- **Java JDK 17+**
- **PostgreSQL**
- A modern web browser

Verify Java using:

```bash
java -version
javac -version
```

## Database Configuration

The project currently connects to PostgreSQL using the values defined in `DBConnection.java`:

```java
private static final String URL =
        "jdbc:postgresql://localhost:5433/auction_system";

private static final String USER = "postgres";
private static final String PASSWORD = "your_password";
```

Update these values according to your local PostgreSQL configuration before running the project.

The implementation expects database objects for the auction system, including:

```text
public.users
public.auctions
public.bids
public.clock_sync
replica.users
replica.auctions
replica.bids
replication_log
election.server_nodes
replica_servers
```

The primary and replica auction records should be initialized before testing bidding, replication, and fault-tolerance experiments.

> **Important:** Do not commit real database passwords to a public repository. Prefer environment variables or a local configuration file for credentials.

## Compilation

Open a terminal in the project root.

### Windows

```powershell
javac -cp ".;postgresql-42.7.13.jar" *.java
```

### Linux / macOS

```bash
javac -cp ".:postgresql-42.7.13.jar" *.java
```

## Running the Project

The main application requires the RMI auction server to start before the HTTP API.

### Step 1: Start the Auction RMI Server

#### Windows

```powershell
java -cp ".;postgresql-42.7.13.jar" AuctionServer
```

#### Linux / macOS

```bash
java -cp ".:postgresql-42.7.13.jar" AuctionServer
```

The server creates an RMI registry on:

```text
localhost:1099
```

and binds the service as:

```text
AuctionService
```

### Step 2: Start the HTTP API Server

Open another terminal.

#### Windows

```powershell
java -cp ".;postgresql-42.7.13.jar" AuctionApiServer
```

#### Linux / macOS

```bash
java -cp ".:postgresql-42.7.13.jar" AuctionApiServer
```

The API will be available at:

```text
http://localhost:8080
```

### Step 3: Start the Bully Election Servers

Open three separate terminals and start three election nodes:

```bash
java -cp ".;postgresql-42.7.13.jar" ElectionServer 1 1101
java -cp ".;postgresql-42.7.13.jar" ElectionServer 2 1102
java -cp ".;postgresql-42.7.13.jar" ElectionServer 3 1103
```

On Linux/macOS, replace `;` with `:` in the classpath.

### Step 4: Open the Frontend

Open:

```text
frontend/distributed_auction_lab_users_fixed.html
```

in a browser.

The frontend communicates with the backend at:

```text
http://localhost:8080
```

## Optional Command-Line Clients

### Auction Client

```bash
java -cp ".;postgresql-42.7.13.jar" AuctionClient
```

The client demonstrates RMI bidding and clock synchronization directly from the terminal.

### Election Client

```bash
java -cp ".;postgresql-42.7.13.jar" ElectionClient
```

Enter the server ID (`1`, `2`, or `3`) to initiate a Bully election.

## Main API Endpoints

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/auction` | GET | Get primary/replica auction state |
| `/api/bid` | POST | Place a bid |
| `/api/users` | GET / POST | View or register bidders |
| `/api/bids` | GET | View stored bid events |
| `/api/clock-sync` | POST | Run clock synchronization |
| `/api/consistency` | POST | Change consistency mode |
| `/api/replication-log` | GET | View replication history |
| `/api/election/nodes` | GET | View election nodes |
| `/api/election/start` | POST | Start a Bully election |
| `/api/fault-tolerance/state` | GET | View primary-backup state |
| `/api/fault-tolerance/replicate` | POST | Replicate a bid to backup |
| `/api/fault-tolerance/fail-primary` | POST | Simulate primary failure |
| `/api/fault-tolerance/reset` | POST | Reset the fault-tolerance demo |
| `/api/load-balancing/state` | GET | View load-balancer state |
| `/api/load-balancing/strategy` | POST | Select balancing strategy |
| `/api/load-balancing/route` | POST | Simulate request routing |
| `/api/load-balancing/reset` | POST | Reset load-balancer statistics |

## Typical Demonstration Flow

1. Start PostgreSQL.
2. Start `AuctionServer`.
3. Start `AuctionApiServer`.
4. Start Election Servers 1, 2, and 3.
5. Open the frontend.
6. Register or select bidders.
7. Place normal and simultaneous bids.
8. Observe physical and Lamport timestamps.
9. Run Cristian clock synchronization.
10. Trigger the Bully election.
11. Compare strong and eventual consistency.
12. Inspect the replication log.
13. Test primary-backup replication and failover.
14. Compare Round Robin and Least Connections load balancing.

## Learning Outcomes

This project demonstrates how distributed-system concepts can be integrated into one practical application rather than studied independently. It covers remote communication, concurrency control, synchronization, logical ordering, coordination, consistency, replication, fault recovery, and request distribution within an online auction scenario.

## Future Improvements

Possible improvements include:

- Move database credentials to environment variables.
- Add SQL schema/setup scripts to automate database initialization.
- Add authentication and bidder sessions.
- Support multiple simultaneous auctions.
- Add automatic health checks for distributed nodes.
- Implement fully automatic replica promotion and recovery.
- Add persistent load-balancer metrics.
- Containerize the services using Docker.
- Add automated unit and integration tests.

## Conclusion

The **Distributed Online Auction System** provides a practical implementation of major Distributed Computing concepts using Java and PostgreSQL. By combining auction functionality with RMI, multithreading, clock synchronization, Lamport clocks, election algorithms, replication, fault tolerance, and load balancing, the project demonstrates how distributed components coordinate to maintain reliable and consistent system behavior.
