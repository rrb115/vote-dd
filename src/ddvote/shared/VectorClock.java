package ddvote.shared;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
// Vector Clock for tracking causality (DC Concept: Logical Time)
public class VectorClock implements Serializable {
    private static final long serialVersionUID = 101L;
    private final ConcurrentHashMap<String, Integer> clock = new ConcurrentHashMap<>();
    public int getTime(String nodeId) { return clock.getOrDefault(nodeId, 0); }
    public synchronized void tick(String localNodeId) { clock.merge(localNodeId, 1, Integer::sum); }
    public synchronized void merge(VectorClock remoteClock) {
        if (remoteClock == null) return;
        remoteClock.clock.forEach((nodeId, remoteTime) -> clock.merge(nodeId, remoteTime, Math::max));
    }
    public synchronized void receiveAction(String localNodeId, VectorClock remoteClock) { merge(remoteClock); tick(localNodeId); }
    public VectorClock copy() { VectorClock c = new VectorClock(); c.clock.putAll(this.clock); return c; }
    @Override public synchronized String toString() {
        return clock.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(",", "{", "}"));
    }
}