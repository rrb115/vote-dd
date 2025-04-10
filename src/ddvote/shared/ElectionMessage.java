package ddvote.shared;
import java.io.Serializable;
// Carries election messages between nodes (DC Concept: Election Protocol)
public class ElectionMessage implements Serializable {
    private static final long serialVersionUID = 102L;
    final ElectionMessageType type; final String senderId; final VectorClock timestamp;
    public ElectionMessage(ElectionMessageType type, String senderId, VectorClock timestamp) { this.type = type; this.senderId = senderId; this.timestamp = timestamp; }
    public ElectionMessageType getType() { return type; } public String getSenderId() { return senderId; } public VectorClock getTimestamp() { return timestamp; }
    @Override public String toString() { return "ElectionMsg{" + type + " from " + senderId + " @ " + timestamp + '}'; }
}