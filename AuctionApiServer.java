import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;

import java.nio.charset.StandardCharsets;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import java.sql.*;

import java.util.HashMap;
import java.util.Map;


public class AuctionApiServer {

    private static AuctionInterface auction;


    // =========================================================
    // MAIN
    // =========================================================

    public static void main(String[] args) {

        try {

            // Connect HTTP layer to existing RMI AuctionServer
            Registry registry =
                    LocateRegistry.getRegistry(
                            "localhost",
                            1099
                    );

            auction =
                    (AuctionInterface)
                            registry.lookup(
                                    "AuctionService"
                            );


            System.out.println(
                    "Connected to Auction RMI Server."
            );


            HttpServer server =
                    HttpServer.create(
                            new InetSocketAddress(8080),
                            0
                    );


            // =================================================
            // ROUTES
            // =================================================

            server.createContext(
                    "/api/auction",
                    AuctionApiServer::getAuction
            );

            server.createContext(
                    "/api/bid",
                    AuctionApiServer::placeBid
            );

            server.createContext(
                    "/api/consistency",
                    AuctionApiServer::setConsistency
            );

            server.createContext(
                    "/api/bids",
                    AuctionApiServer::getBids
            );

            server.createContext(
                    "/api/clock-sync",
                    AuctionApiServer::clockSync
            );

            server.createContext(
                    "/api/replication-log",
                    AuctionApiServer::getReplicationLog
            );

            server.createContext(
                    "/api/election/nodes",
                    AuctionApiServer::getElectionNodes
            );

            server.createContext(
                    "/api/election/start",
                    AuctionApiServer::startElection
            );

            server.createContext(
                    "/api/load-balancing/state",
                    AuctionApiServer::getLoadBalancingState
            );

            server.createContext(
                    "/api/load-balancing/strategy",
                    AuctionApiServer::setLoadBalancingStrategy
            );

            server.createContext(
                    "/api/load-balancing/route",
                    AuctionApiServer::routeLoadBalancingRequest
            );

            server.createContext(
                    "/api/load-balancing/reset",
                    AuctionApiServer::resetLoadBalancing
            );

            server.createContext(
                    "/api/users",
                    AuctionApiServer::users
            );

            // =================================================
            // EXP 5 - FAULT TOLERANCE ROUTES
            // =================================================

            server.createContext(
                    "/api/fault-tolerance/state",
                    AuctionApiServer::getFaultToleranceState
            );

            server.createContext(
                    "/api/fault-tolerance/replicate",
                    AuctionApiServer::replicateFaultToleranceBid
            );

            server.createContext(
                    "/api/fault-tolerance/fail-primary",
                    AuctionApiServer::failPrimaryServer
            );

            server.createContext(
                    "/api/fault-tolerance/reset",
                    AuctionApiServer::resetFaultTolerance
            );


            server.setExecutor(null);

            server.start();


            System.out.println(
                    "======================================"
            );

            System.out.println(
                    " DISTRIBUTED AUCTION HTTP API"
            );

            System.out.println(
                    " http://localhost:8080"
            );

            System.out.println(
                    "======================================"
            );

            System.out.println(
                    "Auction API       : READY"
            );

            System.out.println(
                    "Bidding API       : READY"
            );

            System.out.println(
                    "Clock Sync API    : READY"
            );

            System.out.println(
                    "Election API      : READY"
            );

            System.out.println(
                    "Replication API   : READY"
            );


        } catch (Exception e) {

            System.out.println(
                    "Could not start Auction API."
            );

            e.printStackTrace();
        }
    }


    // =========================================================
    // EXP 1 / GENERAL AUCTION STATE
    //
    // GET /api/auction
    // =========================================================

    private static void getAuction(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }


        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("GET")) {

            sendResponse(
                    exchange,
                    405,
                    message("GET required")
            );

            return;
        }


        try {

            double primary =
                    auction.getHighestBid();

            double replica =
                    auction.getReplicaHighestBid();

            long endTime =
                    auction.getAuctionEndTime();

            String mode =
                    auction.getConsistencyMode();


            String json =
                    "{"
                            + "\"primaryBid\":"
                            + primary + ","

                            + "\"replicaBid\":"
                            + replica + ","

                            + "\"auctionEndTime\":"
                            + endTime + ","

                            + "\"consistencyMode\":\""
                            + escape(mode)
                            + "\""
                            + "}";


            sendResponse(
                    exchange,
                    200,
                    json
            );


        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    message(
                            "Could not read auction"
                    )
            );
        }
    }


    // =========================================================
    // EXP 1 + EXP 2 + EXP 3
    //
    // POST /api/bid?bidderId=1&amount=65000
    //
    // Actual call:
    // Browser -> HTTP -> RMI -> AuctionServer -> PostgreSQL
    // =========================================================

    private static void placeBid(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }


        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("POST")) {

            sendResponse(
                    exchange,
                    405,
                    message("POST required")
            );

            return;
        }


        try {

            Map<String, String> params =
                    getQueryParameters(
                            exchange.getRequestURI()
                    );


            if (!params.containsKey("bidderId")
                    || !params.containsKey("amount")) {

                sendResponse(
                        exchange,
                        400,
                        message(
                                "bidderId and amount required"
                        )
                );

                return;
            }


            int bidderId =
                    Integer.parseInt(
                            params.get("bidderId")
                    );


            double amount =
                    Double.parseDouble(
                            params.get("amount")
                    );


            String result =
                    auction.placeBid(
                            bidderId,
                            amount
                    );


            double primary =
                    auction.getHighestBid();


            double replica =
                    auction.getReplicaHighestBid();


            String json =
                    "{"
                            + "\"message\":\""
                            + escape(result)
                            + "\","

                            + "\"primaryBid\":"
                            + primary + ","

                            + "\"replicaBid\":"
                            + replica
                            + "}";


            sendResponse(
                    exchange,
                    200,
                    json
            );


        } catch (NumberFormatException e) {

            sendResponse(
                    exchange,
                    400,
                    message(
                            "Invalid bidder ID or amount"
                    )
            );


        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    message(
                            "Could not place bid"
                    )
            );
        }
    }


    // =========================================================
    // VIEW REAL BIDS FROM POSTGRESQL
    //
    // GET /api/bids
    // =========================================================

    private static void getBids(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }


        try (
                Connection con =
                        DBConnection.getConnection();

                PreparedStatement ps =
                        con.prepareStatement(
                                "SELECT bid_id, auction_id, " +
                                "bidder_id, bid_amount, " +
                                "physical_time, lamport_time, " +
                                "result " +
                                "FROM public.bids " +
                                "ORDER BY bid_id DESC " +
                                "LIMIT 20"
                        )
        ) {

            ResultSet rs =
                    ps.executeQuery();


            StringBuilder json =
                    new StringBuilder("[");


            boolean first = true;


            while (rs.next()) {

                if (!first) {
                    json.append(",");
                }


                first = false;


                json.append("{")

                        .append("\"bidId\":")
                        .append(
                                rs.getInt("bid_id")
                        )
                        .append(",")

                        .append("\"auctionId\":")
                        .append(
                                rs.getInt("auction_id")
                        )
                        .append(",")

                        .append("\"bidderId\":")
                        .append(
                                rs.getInt("bidder_id")
                        )
                        .append(",")

                        .append("\"amount\":")
                        .append(
                                rs.getDouble("bid_amount")
                        )
                        .append(",")

                        .append("\"physicalTime\":\"")
                        .append(
                                escape(
                                        String.valueOf(
                                                rs.getTimestamp(
                                                        "physical_time"
                                                )
                                        )
                                )
                        )
                        .append("\",")

                        .append("\"lamportTime\":")
                        .append(
                                rs.getLong("lamport_time")
                        )
                        .append(",")

                        .append("\"result\":\"")
                        .append(
                                escape(
                                        rs.getString("result")
                                )
                        )
                        .append("\"")

                        .append("}");
            }


            json.append("]");


            sendResponse(
                    exchange,
                    200,
                    json.toString()
            );


        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    message(
                            "Could not read bids"
                    )
            );
        }
    }


    // =========================================================
    // EXP 3 - CRISTIAN CLOCK SYNCHRONIZATION
    //
    // POST /api/clock-sync?clientId=1&offset=-5000
    // =========================================================

    private static void clockSync(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }


        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("POST")) {

            sendResponse(
                    exchange,
                    405,
                    message("POST required")
            );

            return;
        }


        try {

            Map<String, String> params =
                    getQueryParameters(
                            exchange.getRequestURI()
                    );


            if (!params.containsKey("clientId")) {

                sendResponse(
                        exchange,
                        400,
                        message(
                                "clientId required"
                        )
                );

                return;
            }


            int clientId =
                    Integer.parseInt(
                            params.get("clientId")
                    );


            long simulatedOffset = 0;


            if (params.containsKey("offset")) {

                simulatedOffset =
                        Long.parseLong(
                                params.get("offset")
                        );
            }


            // =============================================
            // Cristian algorithm
            // =============================================

            long clientSendTime =
                    System.currentTimeMillis()
                            + simulatedOffset;


            long serverReportedTime =
                    auction.getServerTime();


            long clientReceiveTime =
                    System.currentTimeMillis()
                            + simulatedOffset;


            long rtt =
                    clientReceiveTime
                            - clientSendTime;


            long estimatedServerTime =
                    serverReportedTime
                            + (rtt / 2);


            long clockOffset =
                    estimatedServerTime
                            - clientReceiveTime;


            // Store REAL result in PostgreSQL
            auction.saveClockSync(
                    clientId,
                    clientSendTime,
                    serverReportedTime,
                    clientReceiveTime,
                    rtt,
                    estimatedServerTime,
                    clockOffset
            );


            String json =
                    "{"
                            + "\"clientId\":"
                            + clientId + ","

                            + "\"clientSendTime\":"
                            + clientSendTime + ","

                            + "\"serverTime\":"
                            + serverReportedTime + ","

                            + "\"clientReceiveTime\":"
                            + clientReceiveTime + ","

                            + "\"rtt\":"
                            + rtt + ","

                            + "\"estimatedServerTime\":"
                            + estimatedServerTime + ","

                            + "\"clockOffset\":"
                            + clockOffset + ","

                            + "\"correctedTime\":"
                            + (
                            clientReceiveTime
                                    + clockOffset
                    )
                            + "}";


            sendResponse(
                    exchange,
                    200,
                    json
            );


        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    message(
                            "Clock synchronization failed"
                    )
            );
        }
    }


    // =========================================================
    // EXP 5 - CONSISTENCY MODE
    //
    // POST /api/consistency?mode=STRONG
    // POST /api/consistency?mode=EVENTUAL
    // =========================================================

    private static void setConsistency(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }


        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("POST")) {

            sendResponse(
                    exchange,
                    405,
                    message("POST required")
            );

            return;
        }


        try {

            Map<String, String> params =
                    getQueryParameters(
                            exchange.getRequestURI()
                    );


            String mode =
                    params.get("mode");


            if (mode == null) {

                sendResponse(
                        exchange,
                        400,
                        message(
                                "mode required"
                        )
                );

                return;
            }


            if (!mode.equalsIgnoreCase("STRONG")
                    &&
                    !mode.equalsIgnoreCase("EVENTUAL")) {

                sendResponse(
                        exchange,
                        400,
                        message(
                                "Use STRONG or EVENTUAL"
                        )
                );

                return;
            }


            auction.setConsistencyMode(
                    mode
            );


            String current =
                    auction.getConsistencyMode();


            sendResponse(
                    exchange,
                    200,

                    "{"
                            + "\"consistencyMode\":\""
                            + escape(current)
                            + "\""
                            + "}"
            );


        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    message(
                            "Could not change mode"
                    )
            );
        }
    }


    // =========================================================
    // EXP 5 - REPLICATION LOG
    //
    // GET /api/replication-log
    // =========================================================

    private static void getReplicationLog(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }


        try (
                Connection con =
                        DBConnection.getConnection();

                PreparedStatement ps =
                        con.prepareStatement(
                                "SELECT log_id, table_name, " +
                                "operation, record_id, status, " +
                                "created_at, replicated_at " +
                                "FROM replication_log " +
                                "ORDER BY log_id DESC " +
                                "LIMIT 20"
                        )
        ) {

            ResultSet rs =
                    ps.executeQuery();


            StringBuilder json =
                    new StringBuilder("[");


            boolean first = true;


            while (rs.next()) {

                if (!first) {
                    json.append(",");
                }

                first = false;


                Timestamp replicated =
                        rs.getTimestamp(
                                "replicated_at"
                        );


                json.append("{")

                        .append("\"logId\":")
                        .append(
                                rs.getInt("log_id")
                        )
                        .append(",")

                        .append("\"table\":\"")
                        .append(
                                escape(
                                        rs.getString(
                                                "table_name"
                                        )
                                )
                        )
                        .append("\",")

                        .append("\"operation\":\"")
                        .append(
                                escape(
                                        rs.getString(
                                                "operation"
                                        )
                                )
                        )
                        .append("\",")

                        .append("\"recordId\":")
                        .append(
                                rs.getInt("record_id")
                        )
                        .append(",")

                        .append("\"status\":\"")
                        .append(
                                escape(
                                        rs.getString("status")
                                )
                        )
                        .append("\",")

                        .append("\"createdAt\":\"")
                        .append(
                                escape(
                                        String.valueOf(
                                                rs.getTimestamp(
                                                        "created_at"
                                                )
                                        )
                                )
                        )
                        .append("\",")

                        .append("\"replicatedAt\":");


                if (replicated == null) {

                    json.append("null");

                } else {

                    json.append("\"")
                            .append(
                                    escape(
                                            replicated.toString()
                                    )
                            )
                            .append("\"");
                }


                json.append("}");
            }


            json.append("]");


            sendResponse(
                    exchange,
                    200,
                    json.toString()
            );


        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    message(
                            "Could not read replication log"
                    )
            );
        }
    }


    // =========================================================
    // EXP 7 - LOAD BALANCING
    //
    // GET /api/load-balancing/state
    // POST /api/load-balancing/strategy?strategy=ROUND_ROBIN
    // POST /api/load-balancing/route?strategy=ROUND_ROBIN
    // POST /api/load-balancing/reset
    // =========================================================

    private static void getLoadBalancingState(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendResponse(exchange, 405, message("GET required"));
            return;
        }

        sendResponse(exchange, 200, LoadBalancerService.getState());
    }

    private static void setLoadBalancingStrategy(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendResponse(exchange, 405, message("POST required"));
            return;
        }

        Map<String, String> params = getQueryParameters(exchange.getRequestURI());
        String strategy = params.get("strategy");

        if (strategy == null || strategy.trim().isEmpty()) {
            sendResponse(exchange, 400, message("strategy required"));
            return;
        }

        String current = LoadBalancerService.setStrategy(strategy);
        sendResponse(exchange, 200, "{\"strategy\":\"" + escape(current) + "\"}");
    }

    private static void routeLoadBalancingRequest(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendResponse(exchange, 405, message("POST required"));
            return;
        }

        Map<String, String> params = getQueryParameters(exchange.getRequestURI());
        String strategy = params.getOrDefault("strategy", "ROUND_ROBIN");
        String result = LoadBalancerService.routeRequest(strategy);
        sendResponse(exchange, 200, result);
    }

    private static void resetLoadBalancing(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendResponse(exchange, 405, message("POST required"));
            return;
        }

        String result = LoadBalancerService.reset();
        sendResponse(exchange, 200, result);
    }


    // =========================================================
    // EXP 4 - READ ELECTION SERVERS FROM POSTGRESQL
    //
    // GET /api/election/nodes
    // =========================================================

    private static void getElectionNodes(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }


        try (
                Connection con =
                        DBConnection.getConnection();

                PreparedStatement ps =
                        con.prepareStatement(
                                "SELECT server_id, " +
                                "server_name, port_number, " +
                                "status, is_coordinator " +
                                "FROM election.server_nodes " +
                                "ORDER BY server_id"
                        )
        ) {

            ResultSet rs =
                    ps.executeQuery();


            StringBuilder json =
                    new StringBuilder("[");


            boolean first = true;


            while (rs.next()) {

                if (!first) {
                    json.append(",");
                }

                first = false;


                json.append("{")

                        .append("\"serverId\":")
                        .append(
                                rs.getInt("server_id")
                        )
                        .append(",")

                        .append("\"serverName\":\"")
                        .append(
                                escape(
                                        rs.getString(
                                                "server_name"
                                        )
                                )
                        )
                        .append("\",")

                        .append("\"port\":")
                        .append(
                                rs.getInt("port_number")
                        )
                        .append(",")

                        .append("\"status\":\"")
                        .append(
                                escape(
                                        rs.getString("status")
                                )
                        )
                        .append("\",")

                        .append("\"coordinator\":")
                        .append(
                                rs.getBoolean(
                                        "is_coordinator"
                                )
                        )

                        .append("}");
            }


            json.append("]");


            sendResponse(
                    exchange,
                    200,
                    json.toString()
            );


        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    message(
                            "Could not read election nodes"
                    )
            );
        }
    }


    // =========================================================
    // EXP 4 - START REAL BULLY ELECTION
    //
    // POST /api/election/start?serverId=1
    // =========================================================

    private static void startElection(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }


        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("POST")) {

            sendResponse(
                    exchange,
                    405,
                    message("POST required")
            );

            return;
        }


        try {

            Map<String, String> params =
                    getQueryParameters(
                            exchange.getRequestURI()
                    );


            if (!params.containsKey("serverId")) {

                sendResponse(
                        exchange,
                        400,
                        message(
                                "serverId required"
                        )
                );

                return;
            }


            int serverId =
                    Integer.parseInt(
                            params.get("serverId")
                    );


            if (serverId < 1
                    || serverId > 3) {

                sendResponse(
                        exchange,
                        400,
                        message(
                                "Server ID must be 1, 2 or 3"
                        )
                );

                return;
            }


            int port =
                    1100 + serverId;


            Registry registry =
                    LocateRegistry.getRegistry(
                            "localhost",
                            port
                    );


            ElectionInterface election =
                    (ElectionInterface)
                            registry.lookup(
                                    "ElectionService"
                            );


            election.startElection();


            sendResponse(
                    exchange,
                    200,
                    message(
                            "Election started from Server "
                                    + serverId
                    )
            );


        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    message(
                            "Could not start election. " +
                            "Check that the selected " +
                            "ElectionServer is running."
                    )
            );
        }
    }

    // =========================================================
// USERS
//
// GET  /api/users
// POST /api/users?name=Ashwita&email=ashwita@gmail.com
// =========================================================

private static void users(
        HttpExchange exchange
) throws IOException {

    if (handleOptions(exchange)) {
        return;
    }

    // -------------------------
    // GET ALL USERS
    // -------------------------

    if (exchange.getRequestMethod()
            .equalsIgnoreCase("GET")) {

        try (
                Connection con =
                        DBConnection.getConnection();

                PreparedStatement ps =
                        con.prepareStatement(
                                "SELECT user_id, name, email " +
                                "FROM public.users " +
                                "ORDER BY user_id"
                        )
        ) {

            ResultSet rs =
                    ps.executeQuery();

            StringBuilder json =
                    new StringBuilder("[");

            boolean first = true;

            while (rs.next()) {

                if (!first) {
                    json.append(",");
                }

                first = false;

                json.append("{")
                        .append("\"userId\":")
                        .append(rs.getInt("user_id"))
                        .append(",")

                        .append("\"name\":\"")
                        .append(
                                escape(
                                        rs.getString("name")
                                )
                        )
                        .append("\",")

                        .append("\"email\":\"")
                        .append(
                                escape(
                                        rs.getString("email")
                                )
                        )
                        .append("\"")

                        .append("}");
            }

            json.append("]");

            sendResponse(
                    exchange,
                    200,
                    json.toString()
            );

        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    message("Could not read users")
            );
        }

        return;
    }


    // -------------------------
    // REGISTER USER
    // -------------------------

    if (exchange.getRequestMethod()
            .equalsIgnoreCase("POST")) {

        try {

            Map<String, String> params =
                    getQueryParameters(
                            exchange.getRequestURI()
                    );

            String name =
                    params.get("name");

            String email =
                    params.get("email");


            if (name == null ||
                    name.trim().isEmpty() ||
                    email == null ||
                    email.trim().isEmpty()) {

                sendResponse(
                        exchange,
                        400,
                        message(
                                "Name and email are required"
                        )
                );

                return;
            }


            UserService service =
                    new UserService();

            int userId =
                    service.registerUser(
                            name.trim(),
                            email.trim()
                    );


            String json =
                    "{"
                            + "\"userId\":"
                            + userId + ","

                            + "\"name\":\""
                            + escape(name.trim())
                            + "\","

                            + "\"email\":\""
                            + escape(email.trim())
                            + "\","

                            + "\"message\":\""
                            + "User registered successfully"
                            + "\""
                            + "}";


            sendResponse(
                    exchange,
                    201,
                    json
            );


        } catch (SQLException e) {

            e.printStackTrace();

            // PostgreSQL UNIQUE violation
            if ("23505".equals(e.getSQLState())) {

                sendResponse(
                        exchange,
                        409,
                        message(
                                "A user with this email already exists"
                        )
                );

            } else {

                sendResponse(
                        exchange,
                        500,
                        message(
                                "Database error while registering user"
                        )
                );
            }


        } catch (Exception e) {

            e.printStackTrace();

            sendResponse(
                    exchange,
                    500,
                    message(
                            "Could not register user"
                    )
            );
        }

        return;
    }


    sendResponse(
            exchange,
            405,
            message("GET or POST required")
    );
}


    // =========================================================
    // EXP 5 - FAULT TOLERANCE STATE
    // GET /api/fault-tolerance/state
    // =========================================================

    private static void getFaultToleranceState(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("GET")) {

            sendResponse(
                    exchange,
                    405,
                    message("GET required")
            );

            return;
        }

        try {
            sendResponse(
                    exchange,
                    200,
                    FaultToleranceService.getState()
            );
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(
                    exchange,
                    500,
                    message("Could not read fault tolerance state")
            );
        }
    }


    // =========================================================
    // EXP 5 - REPLICATE BID TO BACKUP
    // POST /api/fault-tolerance/replicate?amount=60000
    // =========================================================

    private static void replicateFaultToleranceBid(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("POST")) {

            sendResponse(
                    exchange,
                    405,
                    message("POST required")
            );

            return;
        }

        try {
            Map<String, String> params =
                    getQueryParameters(exchange.getRequestURI());

            String amountValue = params.get("amount");

            if (amountValue == null) {
                sendResponse(
                        exchange,
                        400,
                        message("amount required")
                );
                return;
            }

            double amount = Double.parseDouble(amountValue);

            String result =
                    FaultToleranceService.replicateBid(amount);

            sendResponse(
                    exchange,
                    200,
                    message(result)
            );

        } catch (NumberFormatException e) {
            sendResponse(
                    exchange,
                    400,
                    message("amount must be numeric")
            );
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(
                    exchange,
                    500,
                    message("Could not replicate bid")
            );
        }
    }


    // =========================================================
    // EXP 5 - FAIL PRIMARY / PROMOTE BACKUP
    // POST /api/fault-tolerance/fail-primary
    // =========================================================

    private static void failPrimaryServer(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("POST")) {

            sendResponse(
                    exchange,
                    405,
                    message("POST required")
            );

            return;
        }

        try {
            String result = FaultToleranceService.failPrimary();
            sendResponse(exchange, 200, message(result));
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(
                    exchange,
                    500,
                    message("Could not perform failover")
            );
        }
    }


    // =========================================================
    // EXP 5 - RESET PRIMARY/BACKUP DEMO
    // POST /api/fault-tolerance/reset
    // =========================================================

    private static void resetFaultTolerance(
            HttpExchange exchange
    ) throws IOException {

        if (handleOptions(exchange)) {
            return;
        }

        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("POST")) {

            sendResponse(
                    exchange,
                    405,
                    message("POST required")
            );

            return;
        }

        try {
            String result = FaultToleranceService.reset();
            sendResponse(exchange, 200, message(result));
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(
                    exchange,
                    500,
                    message("Could not reset fault tolerance experiment")
            );
        }
    }

    // =========================================================
    // QUERY PARAMETERS
    // =========================================================

    private static Map<String, String>
    getQueryParameters(URI uri) {

        Map<String, String> params =
                new HashMap<>();


        String query =
                uri.getRawQuery();


        if (query == null
                || query.isEmpty()) {

            return params;
        }


        String[] pairs =
                query.split("&");


        for (String pair : pairs) {

            String[] parts =
                    pair.split("=", 2);


            String key =
                    URLDecoder.decode(
                            parts[0],
                            StandardCharsets.UTF_8
                    );


            String value = "";


            if (parts.length > 1) {

                value =
                        URLDecoder.decode(
                                parts[1],
                                StandardCharsets.UTF_8
                        );
            }


            params.put(
                    key,
                    value
            );
        }


        return params;
    }


    // =========================================================
    // CORS / OPTIONS
    // =========================================================

    private static boolean handleOptions(
            HttpExchange exchange
    ) throws IOException {

        exchange.getResponseHeaders()
                .set(
                        "Access-Control-Allow-Origin",
                        "*"
                );


        exchange.getResponseHeaders()
                .set(
                        "Access-Control-Allow-Methods",
                        "GET, POST, OPTIONS"
                );


        exchange.getResponseHeaders()
                .set(
                        "Access-Control-Allow-Headers",
                        "Content-Type"
                );


        if (exchange.getRequestMethod()
                .equalsIgnoreCase("OPTIONS")) {

            sendResponse(
                    exchange,
                    200,
                    ""
            );

            return true;
        }


        return false;
    }


    // =========================================================
    // RESPONSE
    // =========================================================

    private static void sendResponse(
            HttpExchange exchange,
            int status,
            String response
    ) throws IOException {

        exchange.getResponseHeaders()
                .set(
                        "Access-Control-Allow-Origin",
                        "*"
                );


        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );


        byte[] bytes =
                response.getBytes(
                        StandardCharsets.UTF_8
                );


        exchange.sendResponseHeaders(
                status,
                bytes.length
        );


        try (
                OutputStream os =
                        exchange.getResponseBody()
        ) {

            os.write(bytes);
        }
    }


    // =========================================================
    // JSON HELPERS
    // =========================================================

    private static String message(
            String text
    ) {

        return "{"
                + "\"message\":\""
                + escape(text)
                + "\""
                + "}";
    }


    private static String escape(
            String text
    ) {

        if (text == null) {
            return "";
        }


        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}