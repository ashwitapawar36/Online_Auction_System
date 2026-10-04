import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import java.util.Scanner;

public class ElectionClient {

    public static void main(String[] args) {

        Scanner sc =
                new Scanner(System.in);


        System.out.println(
                "================================"
        );

        System.out.println(
                " BULLY ELECTION CLIENT"
        );

        System.out.println(
                "================================"
        );


        System.out.print(
                "Enter server ID to start election (1-3): "
        );


        int id =
                sc.nextInt();


        int port =
                1100 + id;


        try {

            Registry registry =
                    LocateRegistry.getRegistry(
                            "localhost",
                            port
                    );


            ElectionInterface server =
                    (ElectionInterface)
                            registry.lookup(
                                    "ElectionService"
                            );


            server.startElection();


        } catch (Exception e) {

            System.out.println(
                    "Server " + id +
                            " is unavailable."
            );
        }


        sc.close();
    }
}