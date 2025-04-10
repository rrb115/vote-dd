package ddvote.shared;
import java.io.Serializable;
// Holds voter ID and password (Data Model - INSECURE passwords)
public class Credentials implements Serializable {
    private static final long serialVersionUID = 2L;
    private final String voterId; private final String password; // Plain text!
    public Credentials(String voterId, String password) { this.voterId = voterId; this.password = password; }
    public String getVoterId() { return voterId; } public String getPassword() { return password; }
    @Override public String toString() { return "Credentials{voterId='" + voterId + "'}"; } // Hide password
}