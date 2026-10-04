import java.sql.*;

public class ReplicationService {

    public enum ConsistencyMode {
        STRONG,
        EVENTUAL
    }

    private static volatile ConsistencyMode mode =
            ConsistencyMode.STRONG;

    private static final long REPLICATION_DELAY = 5000;


    // =====================================================
    // CONSISTENCY MODE
    // =====================================================

    public static synchronized void setMode(String value) {

        if (value != null &&
                value.equalsIgnoreCase("EVENTUAL")) {

            mode = ConsistencyMode.EVENTUAL;

        } else {

            mode = ConsistencyMode.STRONG;
        }

        System.out.println(
                "\nConsistency mode changed to: " + mode
        );
    }


    public static String getMode() {
        return mode.toString();
    }


    // =====================================================
    // CREATE REPLICATION LOG
    // =====================================================

    public static int createLog(
            Connection con,
            String tableName,
            String operation,
            int recordId,
            String oldValue,
            String newValue
    ) throws SQLException {

        String sql =
                "INSERT INTO replication_log " +
                "(table_name, operation, record_id, " +
                "old_value, new_value, status) " +
                "VALUES (?, ?, ?, ?, ?, 'PENDING') " +
                "RETURNING log_id";

        try (PreparedStatement ps =
                     con.prepareStatement(sql)) {

            ps.setString(1, tableName);
            ps.setString(2, operation);
            ps.setInt(3, recordId);
            ps.setString(4, oldValue);
            ps.setString(5, newValue);

            ResultSet rs = ps.executeQuery();
            rs.next();

            return rs.getInt("log_id");
        }
    }


    // =====================================================
    // REPLICATE USER
    // =====================================================

    public static void replicateUser(
            int logId,
            String operation,
            int userId,
            String name,
            String email
    ) {

        // Capture mode for THIS operation
        ConsistencyMode operationMode = mode;

        Runnable task = () -> {

            try {

                delayIfNeeded(operationMode);

                try (Connection con =
                             DBConnection.getConnection()) {

                    if (operation.equals("INSERT")) {

                        String sql =
                                "INSERT INTO replica.users " +
                                "(user_id, name, email) " +
                                "VALUES (?, ?, ?) " +
                                "ON CONFLICT (user_id) " +
                                "DO UPDATE SET " +
                                "name = EXCLUDED.name, " +
                                "email = EXCLUDED.email";

                        try (PreparedStatement ps =
                                     con.prepareStatement(sql)) {

                            ps.setInt(1, userId);
                            ps.setString(2, name);
                            ps.setString(3, email);

                            ps.executeUpdate();
                        }

                    } else if (operation.equals("UPDATE")) {

                        String sql =
                                "UPDATE replica.users " +
                                "SET name = ?, email = ? " +
                                "WHERE user_id = ?";

                        try (PreparedStatement ps =
                                     con.prepareStatement(sql)) {

                            ps.setString(1, name);
                            ps.setString(2, email);
                            ps.setInt(3, userId);

                            ps.executeUpdate();
                        }

                    } else if (operation.equals("DELETE")) {

                        String sql =
                                "DELETE FROM replica.users " +
                                "WHERE user_id = ?";

                        try (PreparedStatement ps =
                                     con.prepareStatement(sql)) {

                            ps.setInt(1, userId);
                            ps.executeUpdate();
                        }
                    }

                    markReplicated(con, logId);

                    System.out.println(
                            "User replication completed."
                    );
                }

            } catch (Exception e) {

                markFailed(logId);
                e.printStackTrace();
            }
        };

        executeReplication(task, operationMode);
    }


    // =====================================================
    // REPLICATE AUCTION
    // =====================================================

    public static void replicateAuction(
            int logId,
            int auctionId,
            double currentBid,
            String status
    ) {

        ConsistencyMode operationMode = mode;

        Runnable task = () -> {

            try {

                delayIfNeeded(operationMode);

                try (Connection con =
                             DBConnection.getConnection()) {

                    String sql =
                            "UPDATE replica.auctions " +
                            "SET current_bid = ?, status = ? " +
                            "WHERE auction_id = ?";

                    try (PreparedStatement ps =
                                 con.prepareStatement(sql)) {

                        ps.setDouble(1, currentBid);
                        ps.setString(2, status);
                        ps.setInt(3, auctionId);

                        ps.executeUpdate();
                    }

                    markReplicated(con, logId);

                    System.out.println(
                            "Auction replication completed."
                    );
                }

            } catch (Exception e) {

                markFailed(logId);
                e.printStackTrace();
            }
        };

        executeReplication(task, operationMode);
    }


    // =====================================================
    // REPLICATE BID
    // =====================================================

    public static void replicateBid(
            int logId,
            int bidId,
            int auctionId,
            int bidderId,
            double bidAmount,
            Timestamp physicalTime,
            long lamportTime,
            String result
    ) {

        ConsistencyMode operationMode = mode;

        Runnable task = () -> {

            try {

                delayIfNeeded(operationMode);

                try (Connection con =
                             DBConnection.getConnection()) {

                    String sql =
                            "INSERT INTO replica.bids " +
                            "(bid_id, auction_id, bidder_id, " +
                            "bid_amount, physical_time, " +
                            "lamport_time, result) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                            "ON CONFLICT (bid_id) DO NOTHING";

                    try (PreparedStatement ps =
                                 con.prepareStatement(sql)) {

                        ps.setInt(1, bidId);
                        ps.setInt(2, auctionId);
                        ps.setInt(3, bidderId);
                        ps.setDouble(4, bidAmount);
                        ps.setTimestamp(5, physicalTime);
                        ps.setLong(6, lamportTime);
                        ps.setString(7, result);

                        ps.executeUpdate();
                    }

                    markReplicated(con, logId);

                    System.out.println(
                            "Bid replication completed."
                    );
                }

            } catch (Exception e) {

                markFailed(logId);
                e.printStackTrace();
            }
        };

        executeReplication(task, operationMode);
    }


    // =====================================================
    // STRONG VS EVENTUAL
    // =====================================================

    private static void executeReplication(
            Runnable task,
            ConsistencyMode operationMode
    ) {

        if (operationMode == ConsistencyMode.STRONG) {

            System.out.println(
                    "STRONG: Replicating immediately..."
            );

            // Same thread.
            // Client waits for replication.
            task.run();

        } else {

            System.out.println(
                    "EVENTUAL: Primary committed. " +
                    "Replica will update after 5 seconds..."
            );

            // Separate background thread.
            // Client does NOT wait.
            Thread thread = new Thread(task);

            thread.setName(
                    "Replication-Worker-" +
                    System.currentTimeMillis()
            );

            thread.start();
        }
    }


    // =====================================================
    // ACTUAL EVENTUAL CONSISTENCY DELAY
    // =====================================================

    private static void delayIfNeeded(
            ConsistencyMode operationMode
    ) throws InterruptedException {

        if (operationMode ==
                ConsistencyMode.EVENTUAL) {

            System.out.println(
                    "Replica update waiting for 5 seconds..."
            );

            Thread.sleep(REPLICATION_DELAY);

            System.out.println(
                    "5 seconds completed. Updating replica now..."
            );
        }
    }


    // =====================================================
    // MARK LOG AS REPLICATED
    // =====================================================

    private static void markReplicated(
            Connection con,
            int logId
    ) throws SQLException {

        String sql =
                "UPDATE replication_log " +
                "SET status = 'REPLICATED', " +
                "replicated_at = CURRENT_TIMESTAMP " +
                "WHERE log_id = ?";

        try (PreparedStatement ps =
                     con.prepareStatement(sql)) {

            ps.setInt(1, logId);
            ps.executeUpdate();
        }
    }


    // =====================================================
    // MARK LOG AS FAILED
    // =====================================================

    private static void markFailed(int logId) {

        try (
                Connection con =
                        DBConnection.getConnection();

                PreparedStatement ps =
                        con.prepareStatement(
                                "UPDATE replication_log " +
                                "SET status = 'FAILED' " +
                                "WHERE log_id = ?"
                        )
        ) {

            ps.setInt(1, logId);
            ps.executeUpdate();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}