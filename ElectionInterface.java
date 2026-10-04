import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ElectionInterface
        extends Remote {

    boolean isAlive()
            throws RemoteException;


    int getServerId()
            throws RemoteException;


    void startElection()
            throws RemoteException;


    void electionMessage(
            int senderId
    ) throws RemoteException;


    void coordinatorMessage(
            int coordinatorId
    ) throws RemoteException;
}