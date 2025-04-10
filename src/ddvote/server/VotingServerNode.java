package ddvote.server;

import ddvote.naming.NamingService;
import ddvote.shared.*;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry; // **** IMPORT ADDED ****
import java.rmi.registry.Registry;       // **** IMPORT ADDED ****
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

// Core Server Node Logic (Service Implementation)
public class VotingServerNode extends UnicastRemoteObject implements NodeService {
    private static final Logger LOGGER = Logger.getLogger(VotingServerNode.class.getName());
    private final String nodeId;
    // Store naming host/port separately for direct lookup
    private final String namingHost;
    private final int namingPort = RemoteObjectUtils.RMI_REGISTRY_PORT; // Use constant
    private NamingService namingStub; // Still store the stub once found
    private final ServerNodeState state;
    private final ConcurrentHashMap<String, NodeService> peers = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private static final long HB_INTERVAL = 4000;
    private static final long PEER_TIMEOUT = 12000;
    private static final long ELECTION_TIMEOUT = 6000;
    private volatile boolean running = true;

    protected VotingServerNode(String id, String namingHost) throws RemoteException {
        super();
        this.nodeId = id;
        this.namingHost = namingHost; // Store host for direct lookup
        // namingUrl field removed as we construct it dynamically or use host/port
        this.state = new ServerNodeState(nodeId);
        this.executor = Executors.newCachedThreadPool();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    // --- Startup & Discovery ---
    public void start() {
        LOGGER.info("Starting Node: " + nodeId);
        // **** Diagnostic Test Removed from here - connectNaming will try direct lookup ****
        try {
            connectNaming(); // Try the modified connection method
            registerNaming();
            startDiscoveryBeats();
            startFailureDetect();
            scheduler.schedule(this::checkElection, 8, TimeUnit.SECONDS);
            LOGGER.info("Node " + nodeId + " started successfully.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Node start failed", e);
            shutdown();
        }
    }

    // **** MODIFIED connectNaming Method ****
    private void connectNaming() throws RemoteException, NotBoundException {
        LOGGER.info("Attempting to connect to Naming Service on " + namingHost + ":" + namingPort + "...");
        try {
            // 1. Get a direct reference to the registry
            Registry registry = LocateRegistry.getRegistry(namingHost, namingPort);
            LOGGER.info("Successfully obtained reference to RMI registry.");

            // 2. Attempt the lookup using the registry reference
            LOGGER.info("Looking up '" + NamingService.LOOKUP_NAME + "' in registry...");
            // The lookupObject helper still contains the diagnostic list() call
            this.namingStub = RemoteObjectUtils.lookupObject(NamingService.LOOKUP_NAME);

            // If lookupObject didn't throw NotBoundException, we succeeded
            LOGGER.info("Successfully looked up and connected to Naming Service.");

        } catch (RemoteException e) {
            LOGGER.log(Level.SEVERE, "RMI RemoteException during Naming Service connection", e);
            throw e; // Re-throw to indicate connection failure
        } catch (NotBoundException e) {
            LOGGER.log(Level.SEVERE, "RMI NotBoundException: '" + NamingService.LOOKUP_NAME + "' not found in registry.", e);
            throw e; // Re-throw to indicate lookup failure
        } catch (Exception e) {
            // Catch any other unexpected errors during connection
            LOGGER.log(Level.SEVERE, "Unexpected error during Naming Service connection", e);
            throw new RemoteException("Unexpected error connecting to naming service", e);
        }
    }


    // --- registerNaming and other methods remain largely the same ---

    private void registerNaming() {
        String url = NodeService.SERVICE_NAME_PREFIX + nodeId;
        try {
            // Bind self using the helper (which uses getRegistry internally)
            RemoteObjectUtils.bindObject(url, this);
            // Register with the Naming Service stub we obtained
            if (namingStub == null) {
                throw new RemoteException("Naming service stub is null, cannot register.");
            }
            namingStub.register(url, url); // Register RMI name with Naming Service
            LOGGER.info("Registered service: " + url);
        } catch (RemoteException e) {
            LOGGER.log(Level.SEVERE, "Failed to register with Naming Service", e);
            throw new RuntimeException(e); // Propagate as runtime to halt startup
        }
    }

    // ... startDiscoveryBeats, discover, sendBeats, startFailureDetect, checkElection ...
    // ... Client Methods (registerVoter, loginVoter, etc.) ...
    // ... Internal Methods (receiveHeartbeat, handleElectionMessage, etc.) ...
    // ... Election Logic ...
    // ... Replication & Forwarding ...
    // ... Shutdown ...
    // ... Main ...

    // --- Make sure the rest of the file content from the previous version is included below ---
    private void startDiscoveryBeats() { scheduler.scheduleAtFixedRate(() -> { if(running) { try { discover(); sendBeats(); } catch (Exception e) { LOGGER.log(Level.WARNING, "Error in discovery/heartbeat", e);}} }, 0, HB_INTERVAL, TimeUnit.MILLISECONDS); }
    private void discover() {
        if (namingStub == null) { LOGGER.warning("Cannot discover peers, no naming stub."); return; } // Check if naming stub exists
        LOGGER.fine("Discovering peers...");
        try {
            Map<String, String> allServices = namingStub.listServices();
            Set<String> currentPeerIds = new HashSet<>(peers.keySet());

            for (Map.Entry<String, String> entry : allServices.entrySet()) {
                String serviceName = entry.getKey();
                String rmiName = entry.getValue(); // The name used for binding/lookup

                if (serviceName.startsWith(NodeService.SERVICE_NAME_PREFIX)) {
                    String peerId = serviceName.substring(NodeService.SERVICE_NAME_PREFIX.length());
                    if (!peerId.equals(this.nodeId) && !peers.containsKey(peerId)) {
                        // Found a new peer
                        try {
                            LOGGER.fine("Attempting lookup for new peer: " + rmiName);
                            NodeService peerStub = RemoteObjectUtils.lookupObject(rmiName); // Use helper for lookup
                            peers.put(peerId, peerStub);
                            state.updatePeerBeat(peerId); // Initialize heartbeat time
                            LOGGER.info("Discovered and connected to peer: " + peerId);
                        } catch (RemoteException | NotBoundException e) {
                            LOGGER.log(Level.WARNING, "Failed to lookup/connect to new peer " + peerId + " at " + rmiName, e);
                        }
                    }
                    // Remove from set to track which peers are still active
                    currentPeerIds.remove(peerId);
                }
            }

            // Remove peers that are no longer registered in the naming service
            currentPeerIds.forEach(oldPeerId -> {
                if (!oldPeerId.equals(this.nodeId)) {
                    LOGGER.warning("Peer " + oldPeerId + " no longer found in Naming Service. Removing.");
                    peers.remove(oldPeerId);
                    state.removePeer(oldPeerId);
                }
            });

        } catch (RemoteException e) {
            LOGGER.log(Level.SEVERE, "Failed to list services from Naming Service during discovery", e);
            // Maybe try reconnecting to naming service? Or just wait for next cycle.
            namingStub = null; // Invalidate stub on error?
        }
    }
    private void sendBeats() { if (!running) return; state.getClock().tick(nodeId); VectorClock clock = state.getClockCopy(); peers.forEach((id, stub) -> executor.submit(() -> { try { stub.receiveHeartbeat(nodeId, clock); } catch (RemoteException e) { LOGGER.warning("Heartbeat to " + id + " failed: " + e.getMessage()); /* Failure detector handles removal */ }})); }
    private void startFailureDetect() { scheduler.scheduleAtFixedRate(() -> { if(running) { long now = System.currentTimeMillis(); state.getPeerBeats().forEach((id, time) -> {
        if ((now - time) > PEER_TIMEOUT) { LOGGER.warning("Peer timeout: " + id); peers.remove(id); state.removePeer(id); if (id.equals(state.getPrimaryId())) {
            LOGGER.warning("Primary node " + id + " failed. Initiating election."); state.setPrimaryId(null); initiateElection(); } } }); } }, PEER_TIMEOUT / 2, PEER_TIMEOUT / 2, TimeUnit.MILLISECONDS); }
    private void checkElection() { if (running && state.getPrimaryId() == null && !state.isElecRunning()) { initiateElection(); } }

    // --- Client Methods ---
    @Override public boolean registerVoter(Credentials c) throws RemoteException { if (!state.isPrimary()) return forward(p -> p.registerVoter(c));
        LOGGER.info("Primary register: " + c.getVoterId()); boolean ok; synchronized(state) { state.getClock().tick(nodeId); ok = state.addVoter(c.getVoterId(), c.getPassword()); }
        if (ok) replicate(new ReplicationUpdate(ReplicationUpdate.UpdateType.REGISTER_VOTER, c, state.getClockCopy())); return ok; }
    @Override public String loginVoter(Credentials c) { LOGGER.fine("Login: " + c.getVoterId()); String pw = state.getPw(c.getVoterId()); return (pw != null && pw.equals(c.getPassword())) ? c.getVoterId() : null; }
    @Override public void logoutVoter(String id) { LOGGER.fine("Logout: " + id); } @Override public List<Candidate> getCandidates() { return state.getCands(); }
    @Override public List<VoteResult> getResults() { return state.getRes(); } @Override public ElectionState getElectionState() { return state.getElecState(); }
    @Override public VoteResultStatus submitVote(String vId, String cId) throws RemoteException { if (!state.isPrimary()) return forward(p -> p.submitVote(vId, cId));
        LOGGER.info("Primary vote: " + vId + "->" + cId); if (!requestDistributedLock(nodeId, state.getClockCopy())) return VoteResultStatus.REJECTED_LOCK_BUSY;
        VoteResultStatus status = VoteResultStatus.REJECTED_ERROR; try { synchronized(state) { if (state.getElecState() != ElectionState.RUNNING) status = VoteResultStatus.REJECTED_NOT_RUNNING;
        else if (!state.candidates.containsKey(cId)) status = VoteResultStatus.REJECTED_INVALID_CANDIDATE; else if (state.hasVoted(vId)) status = VoteResultStatus.REJECTED_ALREADY_VOTED;
        else { state.getClock().tick(nodeId); if (state.addVote(vId, cId)) { status = VoteResultStatus.ACCEPTED; replicate(new ReplicationUpdate(ReplicationUpdate.UpdateType.RECORD_VOTE,
                new ReplicationUpdate.VoteData(vId, cId), state.getClockCopy())); } } } } finally { releaseDistributedLock(nodeId, state.getClockCopy()); } return status; }

    // --- Internal Methods ---
    @Override public void receiveHeartbeat(String senderId, VectorClock clock) { state.updatePeerBeat(senderId); state.getClock().receiveAction(nodeId, clock); }
    @Override public void handleElectionMessage(ElectionMessage msg) throws RemoteException { state.getClock().receiveAction(nodeId, msg.getTimestamp()); LOGGER.info("Rcvd ElecMsg: " + msg);
        if (msg.getType() == ElectionMessageType.ELECTION_REQUEST) { sendAnswer(msg.getSenderId()); if (nodeId.compareTo(msg.getSenderId()) > 0) initiateElection(); }
        else if (msg.getType() == ElectionMessageType.ANSWER) { LOGGER.fine("Rcvd Answer from " + msg.getSenderId()); state.setElecRunning(true, false); /* Stop waiting */ } }
    @Override public void handleCoordinatorMessage(ElectionMessage msg) throws RemoteException { state.getClock().receiveAction(nodeId, msg.getTimestamp()); LOGGER.info("Rcvd CoordMsg: " + msg);
        if (msg.getType() == ElectionMessageType.COORDINATOR) { String newPrimary = msg.getSenderId(); LOGGER.warning("New Primary: " + newPrimary); state.setPrimaryId(newPrimary);
            state.setElecRunning(false, false); if (state.isPrimary()) LOGGER.warning("!!! I AM NEW PRIMARY !!!"); } }
    @Override public void applyReplicationUpdate(ReplicationUpdate update) { if (state.isPrimary()) return; LOGGER.info("Applying update: " + update.getType());
        state.getClock().receiveAction(nodeId, update.getTimestamp()); synchronized(state) { try { if (update.getType() == ReplicationUpdate.UpdateType.REGISTER_VOTER) {
            Credentials c = (Credentials)update.getData(); state.addVoter(c.getVoterId(), c.getPassword()); } else if (update.getType() == ReplicationUpdate.UpdateType.RECORD_VOTE) {
            ReplicationUpdate.VoteData d = (ReplicationUpdate.VoteData)update.getData(); state.addVote(d.voterId, d.candidateId); } } catch (Exception e) { LOGGER.severe("Apply update failed"); } } }
    @Override public boolean requestDistributedLock(String reqId, VectorClock clock) throws RemoteException { if (!state.isPrimary()) throw new RemoteException("Not primary");
        state.getClock().receiveAction(nodeId, clock); boolean acquired = state.acquireLock(reqId); LOGGER.info("Lock request from "+reqId+": "+(acquired?"GRANTED":"BUSY")); return acquired; }
    @Override public void releaseDistributedLock(String reqId, VectorClock clock) throws RemoteException { if (!state.isPrimary()) throw new RemoteException("Not primary");
        state.getClock().receiveAction(nodeId, clock); if (state.releaseLock(reqId)) LOGGER.info("Lock released by "+reqId); else LOGGER.warning("Failed release by "+reqId); }

    // --- Election Logic (Bully) ---
    private void initiateElection() { if (!state.setElecRunning(false, true)) return; LOGGER.info("Initiating election..."); state.getClock().tick(nodeId);
        List<String> higher = peers.keySet().stream().filter(id -> id.compareTo(nodeId) > 0).collect(Collectors.toList());
        if (higher.isEmpty()) { declarePrimary(); } else { ElectionMessage msg = new ElectionMessage(ElectionMessageType.ELECTION_REQUEST, nodeId, state.getClockCopy());
            higher.forEach(id -> { NodeService stub = peers.get(id); if (stub != null) executor.submit(() -> { try { stub.handleElectionMessage(msg); } catch (RemoteException e) { LOGGER.warning("Elec msg failed to "+id); }}); }); // Added logging
            scheduler.schedule(() -> { if (state.isElecRunning()) { LOGGER.info("Election timeout, declaring self primary."); declarePrimary(); } }, ELECTION_TIMEOUT, TimeUnit.MILLISECONDS); } }
    private void sendAnswer(String requesterId) { NodeService stub = peers.get(requesterId); if (stub == null) return; state.getClock().tick(nodeId);
        ElectionMessage msg = new ElectionMessage(ElectionMessageType.ANSWER, nodeId, state.getClockCopy()); executor.submit(() -> { try { stub.handleElectionMessage(msg); } catch (RemoteException e) { LOGGER.warning("Answer msg failed to "+requesterId); }}); } // Added logging
    private void declarePrimary() { LOGGER.warning("!!! Declaring PRIMARY: " + nodeId + " !!!"); state.setPrimaryId(nodeId); state.setElecRunning(false, false);
        state.getClock().tick(nodeId); ElectionMessage msg = new ElectionMessage(ElectionMessageType.COORDINATOR, nodeId, state.getClockCopy());
        peers.forEach((id, stub) -> executor.submit(() -> { try { stub.handleCoordinatorMessage(msg); } catch (RemoteException e) { LOGGER.warning("Coord msg failed to "+id); }})); } // Added logging

    // --- Replication & Forwarding ---
    private void replicate(ReplicationUpdate update) { LOGGER.fine("Replicating: " + update.getType()); peers.forEach((id, stub) -> executor.submit(() -> { try { stub.applyReplicationUpdate(update); } catch (RemoteException e) { LOGGER.warning("Replication failed to "+id); }})); } // Added logging
    private <T> T forward(RemoteOperation<T> op) throws RemoteException { String pid = state.getPrimaryId(); if (pid == null) throw new RemoteException("Primary unknown");
        NodeService primary = peers.get(pid); if (primary == null) throw new RemoteException("Primary unreachable"); LOGGER.fine("Forwarding to primary: " + pid); return op.execute(primary); }
    @FunctionalInterface interface RemoteOperation<T> { T execute(NodeService primary) throws RemoteException; }

    // --- Shutdown ---
    public void shutdown() { if (!running) return; running = false; LOGGER.warning("Shutting down " + nodeId); scheduler.shutdown(); executor.shutdown();
        try { if (namingStub != null) namingStub.unregister(NodeService.SERVICE_NAME_PREFIX + nodeId); } catch (Exception e) {} RemoteObjectUtils.unbindObject(NodeService.SERVICE_NAME_PREFIX + nodeId);
        RemoteObjectUtils.unexportObject(this); LOGGER.warning("Shutdown complete " + nodeId); }
    // --- Main ---
    public static void main(String[] args) { if (args.length < 2) { System.err.println("Usage: VotingServerNode <nodeId> <namingHost>"); System.exit(1); }
        String id = args[0]; String host = args[1]; VotingServerNode node = null; try { node = new VotingServerNode(id, host);
            final VotingServerNode finalNode = node; Runtime.getRuntime().addShutdownHook(new Thread(finalNode::shutdown)); node.start(); Thread.currentThread().join(); }
        catch (Exception e) { LOGGER.log(Level.SEVERE, "Node "+id+" failed", e); if(node!=null) node.shutdown(); System.exit(1); } }
} // End of class VotingServerNode