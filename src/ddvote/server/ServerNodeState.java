package ddvote.server;
import ddvote.shared.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;
// Holds state for a VotingServerNode (State Management)
public class ServerNodeState {
    final String nodeId;
    // Replicated State
    final ConcurrentHashMap<String, String> voters = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Candidate> candidates = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
    final Set<String> voted = ConcurrentHashMap.newKeySet();
    final AtomicReference<ElectionState> electionState = new AtomicReference<>(ElectionState.NOT_STARTED);
    // Local State
    final AtomicReference<String> primaryId = new AtomicReference<>(null);
    final AtomicReference<Boolean> isPrimary = new AtomicReference<>(false);
    final AtomicReference<Boolean> electionRunning = new AtomicReference<>(false);
    final VectorClock clock = new VectorClock();
    final ConcurrentHashMap<String, Long> peerHeartbeats = new ConcurrentHashMap<>();
    final AtomicReference<String> lockHolder = new AtomicReference<>(null); // Simplified lock holder

    public ServerNodeState(String id) { this.nodeId = id; initCandidates(); }
    private void initCandidates() { addCand(new Candidate("C1","A","D1")); addCand(new Candidate("C2","B","D2")); }
    private void addCand(Candidate c) { candidates.putIfAbsent(c.getId(), c); counts.putIfAbsent(c.getId(), 0); }
    // State Modifiers (called by primary or applyUpdate)
    synchronized boolean addVoter(String id, String pw) { return voters.putIfAbsent(id, pw) == null; }
    synchronized boolean addVote(String vId, String cId) { if (voted.add(vId)) { counts.compute(cId, (k, v) -> v == null ? 1 : v + 1); return true; } return false; }
    synchronized void setElecState(ElectionState s) { electionState.set(s); }
    // Read Methods
    String getPw(String id) { return voters.get(id); } boolean hasVoted(String id) { return voted.contains(id); }
    ElectionState getElecState() { return electionState.get(); } List<Candidate> getCands() { return List.copyOf(candidates.values()); }
    List<VoteResult> getRes() { Map<String, Integer> snap = new ConcurrentHashMap<>(counts); return candidates.values().stream()
            .map(c -> new VoteResult(c.getId(), c.getName(), snap.getOrDefault(c.getId(), 0)))
            .sorted((r1, r2) -> Integer.compare(r2.getVoteCount(), r1.getVoteCount())).collect(Collectors.toList()); }
    // Local State Accessors/Mutators
    String getId() { return nodeId; } VectorClock getClock() { return clock; } VectorClock getClockCopy() { return clock.copy(); }
    String getPrimaryId() { return primaryId.get(); } void setPrimaryId(String id) { primaryId.set(id); isPrimary.set(nodeId.equals(id)); }
    boolean isPrimary() { return isPrimary.get(); } boolean isElecRunning() { return electionRunning.get(); }
    boolean setElecRunning(boolean exp, boolean upd) { return electionRunning.compareAndSet(exp, upd); }
    void updatePeerBeat(String id) { peerHeartbeats.put(id, System.currentTimeMillis()); } Map<String, Long> getPeerBeats() { return Map.copyOf(peerHeartbeats); }
    void removePeer(String id) { peerHeartbeats.remove(id); }
    // Simplified Lock
    synchronized boolean acquireLock(String reqId) { return lockHolder.compareAndSet(null, reqId); }
    synchronized boolean releaseLock(String holderId) { return lockHolder.compareAndSet(holderId, null); }
}