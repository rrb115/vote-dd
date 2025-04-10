package ddvote.server;
import ddvote.shared.*;
import java.rmi.*;
import java.util.List;
// Combined RMI Interface for Server Nodes (Contract/API)
public interface NodeService extends Remote {
    String SERVICE_NAME_PREFIX = "VotingNode_";
    // Client Methods
    boolean registerVoter(Credentials c) throws RemoteException;
    String loginVoter(Credentials c) throws RemoteException;
    void logoutVoter(String voterId) throws RemoteException;
    List<Candidate> getCandidates() throws RemoteException;
    VoteResultStatus submitVote(String voterId, String candidateId) throws RemoteException;
    List<VoteResult> getResults() throws RemoteException;
    ElectionState getElectionState() throws RemoteException;
    // Internal Methods
    void receiveHeartbeat(String senderId, VectorClock clock) throws RemoteException;
    void handleElectionMessage(ElectionMessage msg) throws RemoteException;
    void handleCoordinatorMessage(ElectionMessage msg) throws RemoteException;
    void applyReplicationUpdate(ReplicationUpdate update) throws RemoteException;
    boolean requestDistributedLock(String requesterId, VectorClock clock) throws RemoteException;
    void releaseDistributedLock(String requesterId, VectorClock clock) throws RemoteException;
    // Nested Enum for vote status
    enum VoteResultStatus { ACCEPTED, REJECTED_ALREADY_VOTED, REJECTED_INVALID_CANDIDATE, REJECTED_NOT_RUNNING, REJECTED_ERROR, REJECTED_NOT_LOGGED_IN, REJECTED_LOCK_BUSY }
}