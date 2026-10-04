import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import java.sql.Timestamp;

import java.util.Scanner;

public class AuctionClient {

    public static void main(String[] args) {

        try {

            Scanner sc =
                    new Scanner(System.in);


            Registry registry =
                    LocateRegistry.getRegistry(
                            "localhost",
                            1099
                    );


            AuctionInterface auction =
                    (AuctionInterface)
                            registry.lookup(
                                    "AuctionService"
                            );


            System.out.println(
                    "======================================"
            );

            System.out.println(
                    " DISTRIBUTED ONLINE AUCTION"
            );

            System.out.println(
                    "======================================"
            );


            System.out.print(
                    "Enter Bidder ID: "
            );

            int bidderId =
                    sc.nextInt();


            // =================================================
            // SIMULATED CLOCK DRIFT
            // =================================================

            long simulatedClockOffset = 0;


            if (bidderId == 1) {

                simulatedClockOffset =
                        -5000;

            } else if (bidderId == 2) {

                simulatedClockOffset =
                        3000;
            }


            // =================================================
            // CRISTIAN CLOCK SYNCHRONIZATION
            // =================================================

            System.out.println(
                    "\nSynchronizing clock..."
            );


            long clientSendTime =
                    System.currentTimeMillis()
                            + simulatedClockOffset;


            long serverReportedTime =
                    auction.getServerTime();


            long clientReceiveTime =
                    System.currentTimeMillis()
                            + simulatedClockOffset;


            long rtt =
                    clientReceiveTime
                            - clientSendTime;


            long oneWayDelay =
                    rtt / 2;


            long estimatedServerTime =
                    serverReportedTime
                            + oneWayDelay;


            long clockOffset =
                    estimatedServerTime
                            - clientReceiveTime;


            System.out.println(
                    "\n===== CLOCK SYNCHRONIZATION ====="
            );

            System.out.println(
                    "Client Send Time : "
                            + new Timestamp(
                            clientSendTime
                    )
            );

            System.out.println(
                    "Server Time      : "
                            + new Timestamp(
                            serverReportedTime
                    )
            );

            System.out.println(
                    "RTT              : "
                            + rtt + " ms"
            );

            System.out.println(
                    "Clock Offset     : "
                            + clockOffset + " ms"
            );

            System.out.println(
                    "Corrected Time   : "
                            + new Timestamp(
                            clientReceiveTime
                                    + clockOffset
                    )
            );


            auction.saveClockSync(
                    bidderId,
                    clientSendTime,
                    serverReportedTime,
                    clientReceiveTime,
                    rtt,
                    estimatedServerTime,
                    clockOffset
            );


            // =================================================
            // AUCTION TIME
            // =================================================

            long auctionEndTime =
                    auction.getAuctionEndTime();

            System.out.println(
                        "DEBUG Auction End Time: "
                        + new Timestamp(auctionEndTime)
            );

            System.out.println(
                        "DEBUG Current System Time: "
                        + new Timestamp(System.currentTimeMillis())
            );


            long correctedClientTime =
                    System.currentTimeMillis()
                            + clockOffset;


            long remaining =
                    auctionEndTime
                            - correctedClientTime;


            if (remaining <= 0) {

                System.out.println(
                        "\nAuction is CLOSED."
                );

                sc.close();

                return;
            }


            System.out.println(
                    "\nAuction End Time: "
                            + new Timestamp(
                            auctionEndTime
                    )
            );


            // =================================================
            // EXP 5 CONSISTENCY
            // =================================================

            System.out.println(
                    "\nSelect Consistency Mode:"
            );

            System.out.println(
                    "1. Strong Consistency"
            );

            System.out.println(
                    "2. Eventual Consistency"
            );

            System.out.print(
                    "Choice: "
            );


            int choice =
                    sc.nextInt();


            if (choice == 2) {

                auction.setConsistencyMode(
                        "EVENTUAL"
                );

            } else {

                auction.setConsistencyMode(
                        "STRONG"
                );
            }


            System.out.println(
                    "\nConsistency Mode: "
                            + auction.getConsistencyMode()
            );


            // =================================================
            // SHOW PRIMARY + REPLICA BEFORE BID
            // =================================================

            double primaryBefore =
                    auction.getHighestBid();


            double replicaBefore =
                    auction.getReplicaHighestBid();


            System.out.println(
                    "\n===== BEFORE BID ====="
            );

            System.out.println(
                    "Primary : Rs. "
                            + primaryBefore
            );

            System.out.println(
                    "Replica : Rs. "
                            + replicaBefore
            );


            // =================================================
            // PLACE BID
            // =================================================

            System.out.print(
                    "\nEnter Bid Amount: Rs. "
            );


            double bidAmount =
                    sc.nextDouble();


            String result =
                    auction.placeBid(
                            bidderId,
                            bidAmount
                    );


            System.out.println(
                    "\n======================================"
            );

            System.out.println(result);

            System.out.println(
                    "======================================"
            );


            // =================================================
            // IMMEDIATE READ
            // =================================================

            System.out.println(
                    "\n===== IMMEDIATE READ ====="
            );


            System.out.println(
                    "Primary : Rs. "
                            + auction.getHighestBid()
            );


            System.out.println(
                    "Replica : Rs. "
                            + auction.getReplicaHighestBid()
            );


            if (auction.getConsistencyMode()
                    .equals("EVENTUAL")) {

                System.out.println(
                        "\nWaiting for replica..."
                );


                Thread.sleep(6000);


                System.out.println(
                        "\n===== AFTER REPLICATION ====="
                );


                System.out.println(
                        "Primary : Rs. "
                                + auction.getHighestBid()
                );


                System.out.println(
                        "Replica : Rs. "
                                + auction.getReplicaHighestBid()
                );
            }


            sc.close();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}