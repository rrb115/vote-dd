package ddvote.shared;
import java.io.Serializable;
// Defines the possible states of the election (State Enum)
public enum ElectionState implements Serializable {
    NOT_STARTED("Not Started"), RUNNING("Running"), FINISHED("Finished");
    private final String displayName;
    ElectionState(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
    @Override public String toString() { return displayName; }
}