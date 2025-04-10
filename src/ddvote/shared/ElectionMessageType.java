package ddvote.shared;
import java.io.Serializable;
// Types of messages for the Bully election algorithm (DC Concept: Election Protocol)
public enum ElectionMessageType implements Serializable { ELECTION_REQUEST, ANSWER, COORDINATOR }