package ddvote.naming;
import java.rmi.*;
import java.util.Map;
// RMI interface for the Naming Service (Contract/API)
public interface NamingService extends Remote {
    String LOOKUP_NAME = "VotingNamingService";
    void register(String serviceName, String rmiUrl) throws RemoteException;
    void unregister(String serviceName) throws RemoteException;
    String lookup(String serviceName) throws RemoteException; // Returns RMI name/URL or null
    Map<String, String> listServices() throws RemoteException;
}