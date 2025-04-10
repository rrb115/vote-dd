package ddvote.shared;
import java.io.Serializable;
// Represents a state change sent from primary to backups (DC Concept: Replication)
public class ReplicationUpdate implements Serializable {
    private static final long serialVersionUID = 103L;
    public enum UpdateType { REGISTER_VOTER, RECORD_VOTE }
    final UpdateType type; final Object data; final VectorClock timestamp;
    public ReplicationUpdate(UpdateType type, Object data, VectorClock timestamp) { this.type = type; this.data = data; this.timestamp = timestamp; }
    public UpdateType getType() { return type; } public Object getData() { return data; } public VectorClock getTimestamp() { return timestamp; }
    @Override public String toString() { return "ReplicationUpdate{" + type + ", data=" + data + ", ts=" + timestamp + '}'; }
    // Inner class for vote data payload
    public static class VoteData implements Serializable {
        private static final long serialVersionUID = 1031L;
        public final String voterId; public final String candidateId;
        public VoteData(String voterId, String candidateId) { this.voterId = voterId; this.candidateId = candidateId; }
        @Override public String toString() { return voterId + "->" + candidateId; }
    }
}