import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

public class AuctionServer
        extends UnicastRemoteObject
        implements AuctionInterface {

    private long lamportClock = 0;

    private static final int AUCTION_ID = 1;


    public AuctionServer()
            throws RemoteException {

        super();
    }


    // =====================================================
    // PHYSICAL CLOCK
    // =====================================================

    @Override
    public long getServerTime()
            throws RemoteException {

        return System.currentTimeMillis();
    }


    // =====================================================
    // AUCTION END TIME
    // =====================================================

@Override
public long getAuctionEndTime() throws RemoteException {

    String sql =
            "SELECT end_time FROM public.auctions " +
            "WHERE auction_id = ?";

    try (
        Connection con = DBConnection.getConnection();
        PreparedStatement ps = con.prepareStatement(sql)
    ) {

        ps.setInt(1, AUCTION_ID);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {

            Timestamp endTime =
                    rs.getTimestamp("end_time");

            System.out.println(
                    "Auction end time from DB: " + endTime
            );

            return endTime.getTime();
        }

        // Very useful for debugging
        System.out.println(
                "ERROR: Auction ID " +
                AUCTION_ID +
                " not found in database."
        );

    } catch (Exception e) {

        System.out.println(
                "ERROR while reading auction end time:"
        );

        e.printStackTrace();
    }

    return 0;
}


    // =====================================================
    // PRIMARY HIGHEST BID
    // =====================================================

    @Override
    public double getHighestBid()
            throws RemoteException {

        return getBidFromTable(
                "public.auctions"
        );
    }


    // =====================================================
    // REPLICA HIGHEST BID
    // =====================================================

    @Override
    public double getReplicaHighestBid()
            throws RemoteException {

        return getBidFromTable(
                "replica.auctions"
        );
    }


    private double getBidFromTable(
            String table
    ) {

        String sql =
                "SELECT current_bid " +
                "FROM " + table +
                " WHERE auction_id = ?";

        try (
                Connection con =
                        DBConnection.getConnection();

                PreparedStatement ps =
                        con.prepareStatement(sql)
        ) {

            ps.setInt(1, AUCTION_ID);

            ResultSet rs =
                    ps.executeQuery();

            if (rs.next()) {

                return rs.getDouble(
                        "current_bid"
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

        return 0;
    }


    // =====================================================
    // CONSISTENCY MODE
    // =====================================================

    @Override
    public void setConsistencyMode(
            String mode
    ) throws RemoteException {

        ReplicationService.setMode(mode);
    }


    @Override
    public String getConsistencyMode()
            throws RemoteException {

        return ReplicationService.getMode();
    }


    // =====================================================
    // PLACE BID
    // EXP 2 + EXP 3 + EXP 5
    // =====================================================

    @Override
    public synchronized String placeBid(
            int bidderId,
            double bidAmount
    ) throws RemoteException {

        // Logical clock
        lamportClock++;

        long currentLamport =
                lamportClock;


        // Physical clock
        long physicalTime =
                System.currentTimeMillis();


        String threadName =
                Thread.currentThread().getName();


        System.out.println(
                "\n======================================"
        );

        System.out.println(
                "BID REQUEST RECEIVED"
        );

        System.out.println(
                "Thread       : " + threadName
        );

        System.out.println(
                "Bidder ID    : " + bidderId
        );

        System.out.println(
                "Bid Amount   : Rs. " + bidAmount
        );

        System.out.println(
                "Physical Time: " +
                        new Timestamp(physicalTime)
        );

        System.out.println(
                "Lamport Time : " +
                        currentLamport
        );

        System.out.println(
                "Consistency  : " +
                        ReplicationService.getMode()
        );


        try (Connection con =
                     DBConnection.getConnection()) {

            con.setAutoCommit(false);

            try {

                // -----------------------------------------
                // LOCK AUCTION ROW
                // -----------------------------------------

                String selectSql =
                        "SELECT current_bid, " +
                        "end_time, status " +
                        "FROM public.auctions " +
                        "WHERE auction_id = ? " +
                        "FOR UPDATE";


                double highestBid;
                Timestamp auctionEnd;
                String status;


                try (PreparedStatement ps =
                             con.prepareStatement(selectSql)) {

                    ps.setInt(
                            1,
                            AUCTION_ID
                    );

                    ResultSet rs =
                            ps.executeQuery();


                    if (!rs.next()) {

                        con.rollback();

                        return "Auction not found.";
                    }


                    highestBid =
                            rs.getDouble(
                                    "current_bid"
                            );

                    auctionEnd =
                            rs.getTimestamp(
                                    "end_time"
                            );

                    status =
                            rs.getString(
                                    "status"
                            );
                }


                // -----------------------------------------
                // AUCTION CLOSED
                // -----------------------------------------

                if (physicalTime >=
                        auctionEnd.getTime()
                        ||
                        status.equals("CLOSED")) {

                    int bidId =
                            saveBid(
                                    con,
                                    bidderId,
                                    bidAmount,
                                    physicalTime,
                                    currentLamport,
                                    "REJECTED"
                            );


                    String closeSql =
                            "UPDATE public.auctions " +
                            "SET status = 'CLOSED' " +
                            "WHERE auction_id = ?";


                    try (PreparedStatement ps =
                                 con.prepareStatement(
                                         closeSql
                                 )) {

                        ps.setInt(
                                1,
                                AUCTION_ID
                        );

                        ps.executeUpdate();
                    }


                    int auctionLog =
                            ReplicationService.createLog(
                                    con,
                                    "auctions",
                                    "UPDATE",
                                    AUCTION_ID,
                                    "status=" + status,
                                    "status=CLOSED"
                            );


                    int bidLog =
                            ReplicationService.createLog(
                                    con,
                                    "bids",
                                    "INSERT",
                                    bidId,
                                    null,
                                    "bid=" + bidAmount +
                                            ",result=REJECTED"
                            );


                    con.commit();


                    ReplicationService.replicateAuction(
                            auctionLog,
                            AUCTION_ID,
                            highestBid,
                            "CLOSED"
                    );


                    ReplicationService.replicateBid(
                            bidLog,
                            bidId,
                            AUCTION_ID,
                            bidderId,
                            bidAmount,
                            new Timestamp(physicalTime),
                            currentLamport,
                            "REJECTED"
                    );


                    return
                            "BID REJECTED! Auction has closed."
                            + "\nPhysical Time: "
                            + new Timestamp(physicalTime)
                            + "\nLamport Time: "
                            + currentLamport;
                }


                // -----------------------------------------
                // BID ACCEPTED
                // -----------------------------------------

                if (bidAmount > highestBid) {

                    String updateSql =
                            "UPDATE public.auctions " +
                            "SET current_bid = ? " +
                            "WHERE auction_id = ?";


                    try (PreparedStatement ps =
                                 con.prepareStatement(
                                         updateSql
                                 )) {

                        ps.setDouble(
                                1,
                                bidAmount
                        );

                        ps.setInt(
                                2,
                                AUCTION_ID
                        );

                        ps.executeUpdate();
                    }


                    int bidId =
                            saveBid(
                                    con,
                                    bidderId,
                                    bidAmount,
                                    physicalTime,
                                    currentLamport,
                                    "ACCEPTED"
                            );


                    int auctionLog =
                            ReplicationService.createLog(
                                    con,
                                    "auctions",
                                    "UPDATE",
                                    AUCTION_ID,
                                    "current_bid=" +
                                            highestBid,
                                    "current_bid=" +
                                            bidAmount
                            );


                    int bidLog =
                            ReplicationService.createLog(
                                    con,
                                    "bids",
                                    "INSERT",
                                    bidId,
                                    null,
                                    "bid=" + bidAmount +
                                            ",result=ACCEPTED"
                            );


                    con.commit();


                    // Exp 5 replication
                    ReplicationService.replicateAuction(
                            auctionLog,
                            AUCTION_ID,
                            bidAmount,
                            "ACTIVE"
                    );


                    ReplicationService.replicateBid(
                            bidLog,
                            bidId,
                            AUCTION_ID,
                            bidderId,
                            bidAmount,
                            new Timestamp(physicalTime),
                            currentLamport,
                            "ACCEPTED"
                    );


                    System.out.println(
                            "Result: BID ACCEPTED"
                    );

                    System.out.println(
                            "Primary Bid: Rs. " +
                                    bidAmount
                    );


                    return
                            "BID ACCEPTED!"
                            + "\nNew Highest Bid: Rs. "
                            + bidAmount
                            + "\nPhysical Time: "
                            + new Timestamp(physicalTime)
                            + "\nLamport Time: "
                            + currentLamport
                            + "\nConsistency: "
                            + ReplicationService.getMode();
                }


                // -----------------------------------------
                // BID TOO LOW
                // -----------------------------------------

                else {

                    int bidId =
                            saveBid(
                                    con,
                                    bidderId,
                                    bidAmount,
                                    physicalTime,
                                    currentLamport,
                                    "REJECTED"
                            );


                    int bidLog =
                            ReplicationService.createLog(
                                    con,
                                    "bids",
                                    "INSERT",
                                    bidId,
                                    null,
                                    "bid=" + bidAmount +
                                            ",result=REJECTED"
                            );


                    con.commit();


                    ReplicationService.replicateBid(
                            bidLog,
                            bidId,
                            AUCTION_ID,
                            bidderId,
                            bidAmount,
                            new Timestamp(physicalTime),
                            currentLamport,
                            "REJECTED"
                    );


                    System.out.println(
                            "Result: BID REJECTED"
                    );


                    return
                            "BID REJECTED!"
                            + "\nCurrent Highest Bid: Rs. "
                            + highestBid
                            + "\nPhysical Time: "
                            + new Timestamp(physicalTime)
                            + "\nLamport Time: "
                            + currentLamport;
                }

            } catch (Exception e) {

                con.rollback();

                throw e;
            }

        } catch (Exception e) {

            e.printStackTrace();

            return
                    "Database error: "
                            + e.getMessage();
        }
    }


    // =====================================================
    // SAVE BID
    // =====================================================

    private int saveBid(
            Connection con,
            int bidderId,
            double bidAmount,
            long physicalTime,
            long lamportTime,
            String result
    ) throws Exception {

        String sql =
        "INSERT INTO public.bids " +
        "(auction_id, bidder_id, " +
        "bid_amount, physical_time, " +
        "lamport_time, result) " +
        "VALUES (?, ?, ?, ?, ?, CAST(? AS bid_result)) " +
        "RETURNING bid_id";

        try (PreparedStatement ps =
                     con.prepareStatement(sql)) {

            ps.setInt(
                    1,
                    AUCTION_ID
            );

            ps.setInt(
                    2,
                    bidderId
            );

            ps.setDouble(
                    3,
                    bidAmount
            );

            ps.setTimestamp(
                    4,
                    new Timestamp(
                            physicalTime
                    )
            );

            ps.setLong(
                    5,
                    lamportTime
            );

            ps.setString(
                    6,
                    result
            );


            ResultSet rs =
                    ps.executeQuery();

            rs.next();

            return rs.getInt(
                    "bid_id"
            );
        }
    }


    // =====================================================
    // CRISTIAN CLOCK SYNCHRONIZATION
    // =====================================================

    @Override
    public void saveClockSync(
            int clientId,
            long clientSendTime,
            long serverReportedTime,
            long clientReceiveTime,
            long rtt,
            long estimatedServerTime,
            long clockOffset
    ) throws RemoteException {

        String sql =
                "INSERT INTO public.clock_sync " +
                "(client_id, client_send_time, " +
                "server_reported_time, " +
                "client_receive_time, rtt_ms, " +
                "estimated_server_time, " +
                "clock_offset_ms) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";


        try (
                Connection con =
                        DBConnection.getConnection();

                PreparedStatement ps =
                        con.prepareStatement(sql)
        ) {

            ps.setInt(
                    1,
                    clientId
            );

            ps.setTimestamp(
                    2,
                    new Timestamp(
                            clientSendTime
                    )
            );

            ps.setTimestamp(
                    3,
                    new Timestamp(
                            serverReportedTime
                    )
            );

            ps.setTimestamp(
                    4,
                    new Timestamp(
                            clientReceiveTime
                    )
            );

            ps.setLong(
                    5,
                    rtt
            );

            ps.setTimestamp(
                    6,
                    new Timestamp(
                            estimatedServerTime
                    )
            );

            ps.setLong(
                    7,
                    clockOffset
            );


            ps.executeUpdate();


            System.out.println(
                    "Clock synchronization stored " +
                    "for Client " + clientId
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    // =====================================================
    // START SERVER
    // =====================================================

public static void main(String[] args) {

    try {

        System.out.println("Starting Auction Server...");

        AuctionServer server = new AuctionServer();

        System.out.println("Creating RMI Registry on port 1099...");

        Registry registry =
                LocateRegistry.createRegistry(1099);

        System.out.println("RMI Registry created successfully.");

        registry.rebind(
                "AuctionService",
                server
        );

        System.out.println("AuctionService bound successfully.");

        System.out.println(
                "======================================"
        );

        System.out.println(
                " DISTRIBUTED AUCTION SERVER STARTED"
        );

        System.out.println(
                "======================================"
        );

        System.out.println("RMI                : ENABLED");
        System.out.println("Multithreading     : ENABLED");
        System.out.println("PostgreSQL         : ENABLED");
        System.out.println("Physical Clock     : ENABLED");
        System.out.println("Cristian Sync      : ENABLED");
        System.out.println("Lamport Clock      : ENABLED");
        System.out.println("Replication        : ENABLED");

        System.out.println(
                "Consistency Mode   : "
                + ReplicationService.getMode()
        );

        System.out.println("\nWaiting for bidders...");

        // Keep main thread alive
        Thread.currentThread().join();

    } 
    catch (Exception e) {

        System.out.println("\nSERVER ERROR:");
        e.printStackTrace();
    }
}
}