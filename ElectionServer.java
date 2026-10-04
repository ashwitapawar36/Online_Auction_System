import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class ElectionServer
        extends UnicastRemoteObject
        implements ElectionInterface {

    private int serverId;

    private int port;

    private static final int TOTAL_SERVERS = 3;


    public ElectionServer(
            int serverId,
            int port
    ) throws RemoteException {

        this.serverId = serverId;

        this.port = port;

        updateStatus("ACTIVE");

        System.out.println(
                "Server " + serverId +
                        " started on port " +
                        port
        );
    }


    // =====================================================
    // SERVER HEALTH
    // =====================================================

    @Override
    public boolean isAlive() {
        return true;
    }


    @Override
    public int getServerId() {
        return serverId;
    }


    // =====================================================
    // START BULLY ELECTION
    // =====================================================

    @Override
    public void startElection() {

        System.out.println(
                "\nServer " + serverId +
                        " started an election."
        );


        boolean higherServerFound =
                false;


        for (
                int id = serverId + 1;
                id <= TOTAL_SERVERS;
                id++
        ) {

            int higherPort =
                    1100 + id;


            try {

                Registry registry =
                        LocateRegistry.getRegistry(
                                "localhost",
                                higherPort
                        );


                ElectionInterface higherServer =
                        (ElectionInterface)
                                registry.lookup(
                                        "ElectionService"
                                );


                if (higherServer.isAlive()) {

                    System.out.println(
                            "Server " + id +
                                    " is alive."
                    );


                    System.out.println(
                            "Sending ELECTION message."
                    );


                    higherServerFound =
                            true;


                    higherServer.electionMessage(
                            serverId
                    );
                }


            } catch (Exception e) {

                System.out.println(
                        "Server " + id +
                                " is DOWN."
                );

                markServerDown(id);
            }
        }


        if (!higherServerFound) {

            becomeCoordinator();
        }
    }


    // =====================================================
    // RECEIVE ELECTION
    // =====================================================

    @Override
    public void electionMessage(
            int senderId
    ) {

        System.out.println(
                "\nELECTION message received " +
                        "from Server " +
                        senderId
        );


        System.out.println(
                "Server " + serverId +
                        " sends OK."
        );


        startElection();
    }


    // =====================================================
    // BECOME COORDINATOR
    // =====================================================

    private void becomeCoordinator() {

        System.out.println(
                "\n********************************"
        );

        System.out.println(
                "SERVER " + serverId +
                        " IS THE NEW COORDINATOR"
        );

        System.out.println(
                "********************************"
        );


        updateCoordinatorInDatabase();


        // Inform other active servers

        for (
                int id = 1;
                id <= TOTAL_SERVERS;
                id++
        ) {

            if (id == serverId) {
                continue;
            }


            int serverPort =
                    1100 + id;


            try {

                Registry registry =
                        LocateRegistry.getRegistry(
                                "localhost",
                                serverPort
                        );


                ElectionInterface server =
                        (ElectionInterface)
                                registry.lookup(
                                        "ElectionService"
                                );


                server.coordinatorMessage(
                        serverId
                );


            } catch (Exception e) {

                markServerDown(id);
            }
        }
    }


    // =====================================================
    // RECEIVE COORDINATOR MESSAGE
    // =====================================================

    @Override
    public void coordinatorMessage(
            int coordinatorId
    ) {

        System.out.println(
                "\nCOORDINATOR message received."
        );

        System.out.println(
                "Server " + coordinatorId +
                        " is the coordinator."
        );
    }


    // =====================================================
    // DATABASE STATUS
    // =====================================================

    private void updateStatus(
            String status
    ) {

        String sql =
                "UPDATE election.server_nodes " +
                "SET status = ? " +
                "WHERE server_id = ?";


        try (
                Connection con =
                        DBConnection.getConnection();

                PreparedStatement ps =
                        con.prepareStatement(sql)
        ) {

            ps.setString(
                    1,
                    status
            );

            ps.setInt(
                    2,
                    serverId
            );

            ps.executeUpdate();


        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    private void markServerDown(
            int id
    ) {

        String sql =
                "UPDATE election.server_nodes " +
                "SET status = 'DOWN', " +
                "is_coordinator = FALSE " +
                "WHERE server_id = ?";


        try (
                Connection con =
                        DBConnection.getConnection();

                PreparedStatement ps =
                        con.prepareStatement(sql)
        ) {

            ps.setInt(
                    1,
                    id
            );

            ps.executeUpdate();


        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    private void updateCoordinatorInDatabase() {

        try (Connection con =
                     DBConnection.getConnection()) {


            try (PreparedStatement ps =
                         con.prepareStatement(
                                 "UPDATE election.server_nodes " +
                                 "SET is_coordinator = FALSE"
                         )) {

                ps.executeUpdate();
            }


            try (PreparedStatement ps =
                         con.prepareStatement(
                                 "UPDATE election.server_nodes " +
                                 "SET is_coordinator = TRUE, " +
                                 "status = 'ACTIVE' " +
                                 "WHERE server_id = ?"
                         )) {

                ps.setInt(
                        1,
                        serverId
                );

                ps.executeUpdate();
            }


        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    // =====================================================
    // MAIN
    // =====================================================

    public static void main(String[] args) {

        if (args.length != 2) {

            System.out.println(
                    "Usage: java ElectionServer " +
                    "<serverId> <port>"
            );

            return;
        }


        try {

            int serverId =
                    Integer.parseInt(
                            args[0]
                    );


            int port =
                    Integer.parseInt(
                            args[1]
                    );


            ElectionServer server =
                    new ElectionServer(
                            serverId,
                            port
                    );


            Registry registry =
                    LocateRegistry.createRegistry(
                            port
                    );


            registry.rebind(
                    "ElectionService",
                    server
            );


            System.out.println(
                    "Election Service ready."
            );


        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}