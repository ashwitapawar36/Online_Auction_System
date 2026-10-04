import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AuctionInterface extends Remote {

    // Place bid
    String placeBid(
            int bidderId,
            double bidAmount
    ) throws RemoteException;


    // Get highest bid from PRIMARY
    double getHighestBid()
            throws RemoteException;


    // Exp 5:
    // Get highest bid from REPLICA
    double getReplicaHighestBid()
            throws RemoteException;


    // Physical clock
    long getServerTime()
            throws RemoteException;


    // Auction deadline
    long getAuctionEndTime()
            throws RemoteException;


    // Cristian clock synchronization
    void saveClockSync(
            int clientId,
            long clientSendTime,
            long serverReportedTime,
            long clientReceiveTime,
            long rtt,
            long estimatedServerTime,
            long clockOffset
    ) throws RemoteException;


    // Exp 5 consistency mode
    void setConsistencyMode(String mode)
            throws RemoteException;


    String getConsistencyMode()
            throws RemoteException;
}