import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class FaultToleranceService {

    private static final int PRIMARY_SERVER_ID = 1;
    private static final int BACKUP_SERVER_ID = 2;

    private static final double INITIAL_BID = 58500.00;


    // =====================================================
    // GET CURRENT PRIMARY-BACKUP STATE
    // =====================================================

    public static String getState() {

        String sql =
                "SELECT server_id, server_name, server_role, " +
                "status, auction_id, product_name, current_bid " +
                "FROM replica_servers " +
                "ORDER BY server_id";

        StringBuilder json =
                new StringBuilder("[");

        try (
                Connection con =
                        DBConnection.getConnection();

                PreparedStatement ps =
                        con.prepareStatement(sql);

                ResultSet rs =
                        ps.executeQuery()
        ) {

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

                        .append("\"role\":\"")
                        .append(
                                escape(
                                        rs.getString(
                                                "server_role"
                                        )
                                )
                        )
                        .append("\",")

                        .append("\"status\":\"")
                        .append(
                                escape(
                                        rs.getString(
                                                "status"
                                        )
                                )
                        )
                        .append("\",")

                        .append("\"auctionId\":")
                        .append(
                                rs.getInt("auction_id")
                        )
                        .append(",")

                        .append("\"productName\":\"")
                        .append(
                                escape(
                                        rs.getString(
                                                "product_name"
                                        )
                                )
                        )
                        .append("\",")

                        .append("\"currentBid\":")
                        .append(
                                rs.getDouble(
                                        "current_bid"
                                )
                        )

                        .append("}");
            }

        } catch (Exception e) {

            e.printStackTrace();

            return "[]";
        }

        json.append("]");

        return json.toString();
    }


    // =====================================================
    // UPDATE PRIMARY AND REPLICATE TO BACKUP
    // =====================================================

    public static String replicateBid(
            double newBid
    ) {

        Connection con = null;

        try {

            con =
                    DBConnection.getConnection();

            con.setAutoCommit(false);


            // -------------------------------------------------
            // Read current Primary bid
            // -------------------------------------------------

            double currentBid;

            String selectPrimary =
                    "SELECT current_bid " +
                    "FROM replica_servers " +
                    "WHERE server_id = ? " +
                    "AND server_role = 'PRIMARY' " +
                    "AND status = 'ACTIVE' " +
                    "FOR UPDATE";

            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    selectPrimary
                            )
            ) {

                ps.setInt(
                        1,
                        PRIMARY_SERVER_ID
                );

                ResultSet rs =
                        ps.executeQuery();

                if (!rs.next()) {

                    con.rollback();

                    return "Primary server S1 is not active.";
                }

                currentBid =
                        rs.getDouble(
                                "current_bid"
                        );
            }


            // -------------------------------------------------
            // Validate bid
            // -------------------------------------------------

            if (newBid <= currentBid) {

                con.rollback();

                return "Bid must be greater than current bid Rs. "
                        + currentBid;
            }


            // -------------------------------------------------
            // Update Primary S1
            // -------------------------------------------------

            String updatePrimary =
                    "UPDATE replica_servers " +
                    "SET current_bid = ? " +
                    "WHERE server_id = ? " +
                    "AND server_role = 'PRIMARY' " +
                    "AND status = 'ACTIVE'";

            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    updatePrimary
                            )
            ) {

                ps.setDouble(
                        1,
                        newBid
                );

                ps.setInt(
                        2,
                        PRIMARY_SERVER_ID
                );

                int rows =
                        ps.executeUpdate();

                if (rows == 0) {

                    con.rollback();

                    return "Could not update Primary S1.";
                }
            }


            // -------------------------------------------------
            // Replicate state to Backup S2
            // -------------------------------------------------

            String updateBackup =
                    "UPDATE replica_servers " +
                    "SET current_bid = ? " +
                    "WHERE server_id = ? " +
                    "AND server_role = 'BACKUP' " +
                    "AND status = 'ACTIVE'";

            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    updateBackup
                            )
            ) {

                ps.setDouble(
                        1,
                        newBid
                );

                ps.setInt(
                        2,
                        BACKUP_SERVER_ID
                );

                int rows =
                        ps.executeUpdate();

                if (rows == 0) {

                    con.rollback();

                    return "Backup S2 is not available.";
                }
            }


            con.commit();

            System.out.println();
            System.out.println(
                    "========================================"
            );
            System.out.println(
                    "PRIMARY-BACKUP REPLICATION"
            );
            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "New Bid Received: Rs. " + newBid
            );

            System.out.println(
                    "Primary S1 updated successfully."
            );

            System.out.println(
                    "Replicating state to Backup S2..."
            );

            System.out.println(
                    "Backup S2 updated successfully."
            );

            System.out.println(
                    "REPLICATION SUCCESSFUL"
            );

            return "Bid Rs. "
                    + newBid
                    + " replicated successfully from S1 to S2.";


        } catch (Exception e) {

            if (con != null) {

                try {
                    con.rollback();
                } catch (Exception ignored) {
                }
            }

            e.printStackTrace();

            return "Replication failed.";

        } finally {

            if (con != null) {

                try {

                    con.setAutoCommit(true);
                    con.close();

                } catch (Exception ignored) {
                }
            }
        }
    }


    // =====================================================
    // SIMULATE PRIMARY FAILURE AND PROMOTE BACKUP
    // =====================================================

    public static String failPrimary() {

        Connection con = null;

        try {

            con =
                    DBConnection.getConnection();

            con.setAutoCommit(false);


            // -------------------------------------------------
            // Check whether S1 is still the active Primary
            // -------------------------------------------------

            String checkSql =
                    "SELECT status, server_role " +
                    "FROM replica_servers " +
                    "WHERE server_id = ? " +
                    "FOR UPDATE";

            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    checkSql
                            )
            ) {

                ps.setInt(
                        1,
                        PRIMARY_SERVER_ID
                );

                ResultSet rs =
                        ps.executeQuery();

                if (!rs.next()) {

                    con.rollback();

                    return "Primary server S1 was not found.";
                }

                String status =
                        rs.getString("status");

                String role =
                        rs.getString(
                                "server_role"
                        );

                if (status.equalsIgnoreCase("FAILED")) {

                    con.rollback();

                    return "S1 has already failed.";
                }

                if (!role.equalsIgnoreCase("PRIMARY")) {

                    con.rollback();

                    return "S1 is not currently the Primary.";
                }
            }


            // -------------------------------------------------
            // Mark S1 as FAILED
            // -------------------------------------------------

            String failSql =
                    "UPDATE replica_servers " +
                    "SET status = 'FAILED', " +
                    "server_role = 'FAILED' " +
                    "WHERE server_id = ?";

            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    failSql
                            )
            ) {

                ps.setInt(
                        1,
                        PRIMARY_SERVER_ID
                );

                ps.executeUpdate();
            }


            // -------------------------------------------------
            // Promote S2 from BACKUP to PRIMARY
            // -------------------------------------------------

            String promoteSql =
                    "UPDATE replica_servers " +
                    "SET server_role = 'PRIMARY', " +
                    "status = 'ACTIVE' " +
                    "WHERE server_id = ? " +
                    "AND server_role = 'BACKUP' " +
                    "AND status = 'ACTIVE'";

            int promoted;

            try (
                    PreparedStatement ps =
                            con.prepareStatement(
                                    promoteSql
                            )
            ) {

                ps.setInt(
                        1,
                        BACKUP_SERVER_ID
                );

                promoted =
                        ps.executeUpdate();
            }


            if (promoted == 0) {

                con.rollback();

                return "No active Backup S2 is available.";
            }


            con.commit();


            System.out.println();
            System.out.println(
                    "========================================"
            );
            System.out.println(
                    "SIMULATING PRIMARY FAILURE"
            );
            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "Primary Server S1 has FAILED!"
            );

            System.out.println(
                    "Primary failure detected."
            );

            System.out.println(
                    "Promoting Backup S2..."
            );

            System.out.println(
                    "Backup S2 promoted successfully."
            );

            System.out.println();

            System.out.println(
                    "S2 IS NOW THE PRIMARY SERVER"
            );

            System.out.println(
                    "Auction service continues successfully."
            );


            return "S1 failed. S2 promoted to Primary successfully.";


        } catch (Exception e) {

            if (con != null) {

                try {
                    con.rollback();
                } catch (Exception ignored) {
                }
            }

            e.printStackTrace();

            return "Failover failed.";

        } finally {

            if (con != null) {

                try {

                    con.setAutoCommit(true);
                    con.close();

                } catch (Exception ignored) {
                }
            }
        }
    }


    // =====================================================
    // RESET EXPERIMENT
    // =====================================================

    public static String reset() {

        String sql =
                "UPDATE replica_servers " +
                "SET server_role = CASE " +
                "WHEN server_id = 1 THEN 'PRIMARY' " +
                "WHEN server_id = 2 THEN 'BACKUP' " +
                "END, " +
                "status = 'ACTIVE', " +
                "current_bid = ? " +
                "WHERE server_id IN (1, 2)";

        try (
                Connection con =
                        DBConnection.getConnection();

                PreparedStatement ps =
                        con.prepareStatement(sql)
        ) {

            ps.setDouble(
                    1,
                    INITIAL_BID
            );

            ps.executeUpdate();


            System.out.println();
            System.out.println(
                    "Fault Tolerance experiment reset."
            );

            System.out.println(
                    "S1 = PRIMARY | ACTIVE | Rs. "
                            + INITIAL_BID
            );

            System.out.println(
                    "S2 = BACKUP  | ACTIVE | Rs. "
                            + INITIAL_BID
            );


            return "Experiment reset successfully.";


        } catch (Exception e) {

            e.printStackTrace();

            return "Could not reset experiment.";
        }
    }


    // =====================================================
    // SIMPLE JSON ESCAPE
    // =====================================================

    private static String escape(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}